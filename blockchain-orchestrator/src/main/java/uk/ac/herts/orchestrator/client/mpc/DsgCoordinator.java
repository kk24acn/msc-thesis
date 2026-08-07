package uk.ac.herts.orchestrator.client.mpc;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.mpc.config.MpcProperties;
import uk.ac.herts.orchestrator.client.mpc.grpc.TraceIdClientInterceptor;
import uk.ac.herts.orchestrator.exception.mpc.DsgRoundException;
import uk.ac.herts.orchestrator.exception.mpc.SignatureAggregationException;
import uk.ac.herts.orchestrator.exception.mpc.SignatureGenerationException;
import uk.ac.herts.orchestrator.exception.mpc.SignatureVerificationException;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.DsgPhaseRequest;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.DsgPhaseResponse;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.InitPayload;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.PeerPayloads;
import uk.ac.herts.orchestrator.grpc.signer.DsgServiceGrpc.DsgServiceFutureStub;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;

import org.web3j.crypto.Sign.SignatureData;

@Slf4j
@Component
public class DsgCoordinator {
    private final Map<Integer, DsgServiceFutureStub> stubs;
    private final MpcProperties mpcProperties;
    private final Semaphore grpcConcurrencyLimit;
    private final SignatureAggregator signatureAggregator;
    private final QuorumManager quorumManager;
    private final AtomicInteger globalRoundRobinCounter = new AtomicInteger(0);

    public DsgCoordinator(
            Map<Integer, DsgServiceFutureStub> stubs,
            MpcProperties mpcProperties,
            SignatureAggregator signatureAggregator,
            QuorumManager quorumManager) {
        int concurrencyLimit = mpcProperties.getGrpcConcurrencyLimit();
        this.stubs = stubs;
        this.mpcProperties = mpcProperties;
        this.signatureAggregator = signatureAggregator;
        this.quorumManager = quorumManager;
        this.grpcConcurrencyLimit = new Semaphore(concurrencyLimit, true);
        this.quorumManager.initialize(stubs.keySet());
        log.info("{} initiated with GRPC_CONCURRENCY_LIMIT={}", this.getClass().getName(), concurrencyLimit);
    }

    public record DsgResult(SignatureData signature, int retries, OffsetDateTime firstFaultAt) {
    }

    public <T> T executeUnderConcurrencyLimit(Callable<T> task) {
        try {
            grpcConcurrencyLimit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for DSG semaphore", e);
        }

        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected checked exception during DSG execution", e);
        } finally {
            grpcConcurrencyLimit.release();
        }
    }

    public DsgResult executeDsg(MpcKey mpcKey, byte[] messageHash) {
        log.info("Starting DSG execution for keyId={} with {} Signer Stubs", mpcKey.getKeyId(), stubs.size());

        int maxAttempts = mpcProperties.getDsg().getMaxRetries() + 1;
        int quarantineBudget = stubs.size() - mpcKey.getThreshold();
        Exception lastException = null;
        OffsetDateTime firstFaultAt = null;
        int actualAttempt = 0;
        int localRoundRobin = Math.abs(globalRoundRobinCounter.getAndIncrement());

        for (int i = 0; i < maxAttempts; i++) {
            actualAttempt++;
            List<Integer> quorum = quorumManager.selectQuorum(mpcKey.getThreshold(), localRoundRobin);
            localRoundRobin++;
            String dsgSessionId = UUID.randomUUID().toString();

            try {
                log.info("DSG attempt {} (effective {}/{}, session={}, quorum={})",
                        actualAttempt, i + 1, maxAttempts, dsgSessionId, quorum);

                List<ByteString> initialPayloads = initializeQuorum(
                        mpcKey.getKeyId(), dsgSessionId, messageHash, quorum, i);
                SignatureData sigData = processRounds(
                        dsgSessionId, quorum, messageHash, mpcKey.getEthereumAddress(), i, initialPayloads);

                quorumManager.onQuorumSuccess(quorum);
                log.info("DSG succeeded with session {}", dsgSessionId);
                return new DsgResult(sigData, i, firstFaultAt);
            } catch (SignatureAggregationException e) {
                lastException = e;
                if (firstFaultAt == null) {
                    firstFaultAt = OffsetDateTime.now();
                }
                quorumManager.onQuorumFailure(quorum);
                log.warn("Aggregation failure in quorum {}: {}. Retrying.", quorum, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                if (firstFaultAt == null) {
                    firstFaultAt = OffsetDateTime.now();
                }
                quorumManager.onQuorumFailure(quorum);
                boolean banProcessed = quorumManager.processBanRequest(e, mpcKey.getThreshold());
                if (banProcessed) {
                    if (quarantineBudget > 0) {
                        quarantineBudget--;
                        i--;
                    }
                    log.warn("Quarantined node during quorum {}.", quorum);
                } else {
                    log.warn("Execution failure in quorum {}. Exception: {}", quorum, e.getMessage(), e);
                }
            }
        }

        if (lastException instanceof SignatureAggregationException) {
            throw new SignatureVerificationException(
                    "Quorum permutations failed. Last failure was an aggregation error",
                    maxAttempts - 1, lastException, quorumManager.getQuarantinedPartyIds(), firstFaultAt);
        } else {
            throw new SignatureGenerationException("All quorum permutations failed",
                    maxAttempts - 1, lastException, quorumManager.getQuarantinedPartyIds(), firstFaultAt);
        }
    }

    private DsgServiceFutureStub getStubWithDeadline(int partyId, int retry) {
        DsgServiceFutureStub stub = stubs.get(partyId)
                .withDeadlineAfter(mpcProperties.getDsg().getRequestTimeout());
        String traceId = MDC.get("traceId");
        stub = stub.withInterceptors(new TraceIdClientInterceptor(traceId, retry));
        return stub;
    }

    private List<ByteString> initializeQuorum(String keyId, String dsgSessionId, byte[] messageHash,
            List<Integer> quorum, int retry) {
        log.info("--- DSG Phase 1; Quorum {} ---", quorum);
        List<CompletableFuture<ByteString>> futures = new ArrayList<>();

        for (Integer partyId : quorum) {
            log.debug("Sending ExecuteDsgPhase (Init) to Signer#{}: keyId={}, sessionId={}", partyId, keyId,
                    dsgSessionId);

            CompletableFuture<ByteString> future = toCompletableFuture(getStubWithDeadline(partyId, retry)
                    .executeDsgPhase(DsgPhaseRequest.newBuilder()
                            .setDsgSessionId(dsgSessionId)
                            .setPartyId(partyId)
                            .setInit(InitPayload.newBuilder()
                                    .setKeyId(keyId)
                                    .setMessageHash(ByteString.copyFrom(messageHash))
                                    .setDerivationPath(mpcProperties.getDsg().getEthDerivationPath())
                                    .build())
                            .build()))
                    .thenApply(response -> {
                        log.debug("Signer#{} succeeded ExecuteDsgPhase (Init), got {} bytes", partyId,
                                response.getIntermediateOutput().size());
                        return response.getIntermediateOutput();
                    })
                    .exceptionally(e -> {
                        throw DsgRoundException.buildException(partyId, 0, "Init", e);
                    });

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<ByteString> initialPayloads = futures.stream().map(CompletableFuture::join).toList();
            log.info("DSG Phase 1 completed ({} payloads)", initialPayloads.size());
            return initialPayloads;
        } catch (Exception e) {
            throw DsgRoundException.unwrap(e);
        }
    }

    private SignatureData processRounds(String dsgSessionId, List<Integer> quorum, byte[] messageHash,
            String expectedAddress, int retry, List<ByteString> currentPayloads) {
        log.info("Continuing DSG phases for session: {}", dsgSessionId);

        int maxRounds = mpcProperties.getDsg().getMaxRounds();
        for (int round = 0; round < maxRounds; round++) {
            int currentPhase = round + 2;
            log.info("--- DSG Phase {}; Quorum {} ---", currentPhase, quorum);

            try {
                List<CompletableFuture<DsgPhaseResponse>> roundFutures = createRoundFutures(
                        dsgSessionId, quorum, currentPayloads, retry, round);

                List<DsgPhaseResponse> responses = roundFutures.stream()
                        .map(CompletableFuture::join)
                        .toList();

                currentPayloads = responses.stream()
                        .map(DsgPhaseResponse::getIntermediateOutput)
                        .toList();

                if (responses.get(0).hasSignatureShare()) {
                    SignatureData sigData = signatureAggregator.aggregate(responses, messageHash, expectedAddress);
                    log.info("DSG signature obtained after {} phases", currentPhase);
                    return sigData;
                }

                log.info("DSG Phase {} completed", currentPhase);
            } catch (SignatureAggregationException e) {
                throw e;
            } catch (Exception e) {
                throw DsgRoundException.unwrap(e);
            }
        }

        throw new SignatureGenerationException(
                String.format("DSG protocol did not complete within %d rounds", maxRounds), maxRounds, null, Set.of());
    }

    private List<CompletableFuture<DsgPhaseResponse>> createRoundFutures(String dsgSessionId, List<Integer> quorum,
            List<ByteString> payloads, int retry, int round) {
        log.debug("Creating ExecuteDsgPhase (Advance) futures for {} signers with {} payloads", quorum.size(),
                payloads.size());
        return quorum.stream()
                .map(partyId -> {
                    log.debug("Sending ExecuteDsgPhase (Advance) to Signer#{} (sessionId={})", partyId, dsgSessionId);
                    return toCompletableFuture(getStubWithDeadline(partyId, retry)
                            .executeDsgPhase(DsgPhaseRequest.newBuilder()
                                    .setDsgSessionId(dsgSessionId)
                                    .setPartyId(partyId)
                                    .setPeerPayloads(PeerPayloads.newBuilder()
                                            .addAllPayloads(payloads)
                                            .build())
                                    .build()))
                            .thenApply(response -> {
                                log.debug("Signer#{} returned response: {} bytes", partyId,
                                        response.getIntermediateOutput().size());
                                return response;
                            })
                            .exceptionally(e -> {
                                throw DsgRoundException.buildException(partyId, round, "Advance", e);
                            });
                })
                .toList();
    }

    private <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        Futures.addCallback(listenableFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                completableFuture.complete(result);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                completableFuture.completeExceptionally(t);
            }
        }, Runnable::run);
        return completableFuture;
    }
}

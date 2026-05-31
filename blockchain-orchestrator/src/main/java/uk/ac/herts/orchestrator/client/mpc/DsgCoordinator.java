package uk.ac.herts.orchestrator.client.mpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import uk.ac.herts.orchestrator.api.filter.TraceIdFilter;
import uk.ac.herts.orchestrator.client.mpc.config.MpcProperties;
import uk.ac.herts.orchestrator.client.mpc.grpc.TraceIdClientInterceptor;
import uk.ac.herts.orchestrator.exception.SignatureGenerationException;
import uk.ac.herts.orchestrator.exception.SignatureAggregationException;
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
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final Map<Integer, List<List<Integer>>> quorumCombinationsCache = new ConcurrentHashMap<>();

    public DsgCoordinator(
            Map<Integer, DsgServiceFutureStub> stubs,
            MpcProperties mpcProperties,
            SignatureAggregator signatureAggregator) {
        int grpcConcurrencyLimit = mpcProperties.getGrpcConcurrencyLimit();
        this.stubs = stubs;
        this.mpcProperties = mpcProperties;
        this.signatureAggregator = signatureAggregator;
        this.grpcConcurrencyLimit = new Semaphore(grpcConcurrencyLimit, true);
        log.info("{} initiated with GRPC_CONCURRENCY_LIMIT={}", this.getClass().getName(), grpcConcurrencyLimit);
    }

    public record DsgResult(SignatureData signature, int retries) {
    }

    public <T> T executeUnderConcurrencyLimit(Callable<T> task) {
        try {
            grpcConcurrencyLimit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for DSG semaphore", e);
        }

        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            grpcConcurrencyLimit.release();
        }
    }

    private DsgServiceFutureStub getStubWithDeadline(int partyId, int retry) {
        DsgServiceFutureStub stub = stubs.get(partyId)
                .withDeadlineAfter(mpcProperties.getDsg().getRequestTimeout());
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        stub = stub.withInterceptors(new TraceIdClientInterceptor(traceId, retry));
        return stub;
    }

    public DsgResult executeDsg(MpcKey mpcKey, byte[] messageHash) {
        log.info("Starting DSG execution for keyId={} with {} Signer Stubs", mpcKey.getKeyId(), stubs.size());
        List<Integer> availableNodes = new ArrayList<>(stubs.keySet());
        List<List<Integer>> candidateQuorums = quorumCombinationsCache.computeIfAbsent(
                mpcKey.getThreshold(), t -> generateQuorumCombinations(availableNodes, t));

        int totalPermutations = candidateQuorums.size();
        int maxAttempts = mpcProperties.getDsg().getMaxRetries() + 1;
        int offset = Math.abs(roundRobinCounter.getAndIncrement() % totalPermutations);
        String expectedAddress = mpcKey.getEthereumAddress();

        boolean onlyAggregationFailures = true;
        for (int i = 0; i < maxAttempts; i++) {
            List<Integer> quorum = candidateQuorums.get((i + offset) % totalPermutations);
            String dsgSessionId = UUID.randomUUID().toString();

            try {
                log.info("DSG permutation attempt {}/{} (session={})", i + 1, maxAttempts, dsgSessionId);

                List<ByteString> initialPayloads = initializeQuorum(mpcKey.getKeyId(), dsgSessionId, messageHash,
                        quorum, i);
                SignatureData sigData = processRounds(dsgSessionId, quorum, messageHash, expectedAddress, i,
                        initialPayloads);

                log.info("DSG succeeded with session {}", dsgSessionId);
                return new DsgResult(sigData, i);
            } catch (SignatureAggregationException e) {
                log.warn("Signature aggregation failure occurred in quorum {}: {}. Retrying with next permutation",
                        quorum, e.getMessage());
            } catch (Exception e) {
                onlyAggregationFailures = false;
                log.warn("Execution failure in quorum {}. Retrying...", quorum);
            }
        }
        throw new SignatureGenerationException("All quorum permutations failed", maxAttempts - 1, null,
                onlyAggregationFailures);
    }

    private List<List<Integer>> generateQuorumCombinations(List<Integer> nodes, int threshold) {
        List<List<Integer>> combinations = new ArrayList<>();
        generateQuorumCombinationsHelper(nodes, threshold, 0, new ArrayList<>(), combinations);
        return combinations;
    }

    private void generateQuorumCombinationsHelper(List<Integer> nodes, int threshold, int start, List<Integer> current,
            List<List<Integer>> combinations) {
        if (current.size() == threshold) {
            combinations.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < nodes.size(); i++) {
            current.add(nodes.get(i));
            generateQuorumCombinationsHelper(nodes, threshold, i + 1, current, combinations);
            current.remove(current.size() - 1);
        }
    }

    private List<ByteString> initializeQuorum(String keyId, String dsgSessionId, byte[] messageHash,
            List<Integer> quorum,
            int retry) {
        log.info("Initializing specific quorum: {}", quorum);
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
                        throw new RuntimeException(String.format("Signer#%d failed ExecuteDsgPhase (Init)", partyId),
                                e);
                    });

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<ByteString> initialPayloads = futures.stream().map(CompletableFuture::join).toList();
            log.info("Quorum {} initialized successfully with {} Phase 1 payloads", quorum, initialPayloads.size());
            return initialPayloads;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Failed to initialize quorum %s", quorum), e);
        }
    }

    private SignatureData processRounds(String dsgSessionId, List<Integer> quorum, byte[] messageHash,
            String expectedAddress, int retry, List<ByteString> currentPayloads) {
        log.info("Starting DSG rounds for session: {}", dsgSessionId);

        int round = 0;

        while (true) {
            log.info("--- DSG Round {}; Quorum {} ---", round++, quorum);

            try {
                List<CompletableFuture<DsgPhaseResponse>> roundFutures = createRoundFutures(
                        dsgSessionId, quorum, currentPayloads, retry);

                List<DsgPhaseResponse> responses = roundFutures.stream()
                        .map(CompletableFuture::join)
                        .toList();

                currentPayloads = responses.stream()
                        .map(DsgPhaseResponse::getIntermediateOutput)
                        .toList();

                if (responses.get(0).hasSignatureShare()) {
                    SignatureData sigData = signatureAggregator.aggregate(responses, messageHash, expectedAddress);
                    log.info("DSG signature obtained after {} rounds", round);
                    return sigData;
                }

                log.info("DSG round {} completed", round);
            } catch (SignatureAggregationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(String.format("DSG round %d failed", round), e);
            }
        }
    }

    private List<CompletableFuture<DsgPhaseResponse>> createRoundFutures(String dsgSessionId, List<Integer> quorum,
            List<ByteString> payloads, int retry) {
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
                                throw new RuntimeException(
                                        String.format("Signer#%d failed ExecuteDsgPhase (Advance)", partyId), e);
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

package uk.ac.herts.orchestrator.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.config.MpcProperties;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.AdvanceDsgRequest;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.AdvanceDsgResponse;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.InitDsgRequest;
import uk.ac.herts.orchestrator.grpc.signer.DsgServiceGrpc;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;

@Slf4j
@Component
public class DsgCoordinator {
    private final Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> stubs;
    private final MpcProperties mpcProperties;

    public DsgCoordinator(Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> stubs, MpcProperties mpcProperties) {
        this.stubs = stubs;
        this.mpcProperties = mpcProperties;
    }

    private DsgServiceGrpc.DsgServiceFutureStub getStubWithDeadline(int partyId) {
        var stub = stubs.get(partyId).withDeadlineAfter(mpcProperties.getDsg().getRequestTimeout());
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            stub = stub.withInterceptors(new TraceIdClientInterceptor(traceId));
        }
        return stub;
    }

    public byte[] executeDsg(MpcKey mpcKey, byte[] messageHash) {
        log.info("Starting DSG execution for keyId={} with {} Signer Stubs", mpcKey.getKeyId(), stubs.size());

        int attempts = 0;
        int maxRetries = mpcProperties.getDsg().getMaxRetries();
        Exception lastException = null;

        while (attempts++ < maxRetries) {
            String dsgSessionId = UUID.randomUUID().toString();
            try {
                log.info("DSG Attempt {} (Session: {})", attempts, dsgSessionId);
                List<Integer> activeQuorum = initializeQuorum(mpcKey.getKeyId(),
                        dsgSessionId,
                        messageHash,
                        mpcKey.getThreshold());
                byte[] result = processRounds(dsgSessionId, activeQuorum);
                log.info("DSG succeeded on attempt {} with session {}", attempts, dsgSessionId);
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("DSG attempt {} failed for session {}. Error: {} - {}",
                        attempts, dsgSessionId, e.getClass().getSimpleName(), e.getMessage(), e);

                if (attempts < maxRetries) {
                    log.info("Retrying... ({}/{})", attempts, maxRetries);
                }
            }
        }

        throw new RuntimeException(String.format("DSG failed after %d attempts", attempts), lastException);
    }

    private List<Integer> initializeQuorum(String keyId, String dsgSessionId, byte[] messageHash, int threshold) {
        if (stubs.size() < threshold) {
            throw new IllegalArgumentException(
                    String.format("Not enough signers available. Threshold required %d; signers available %d",
                            threshold, stubs.size()));
        }

        log.info("Initializing quorum with threshold: {}/{}", threshold, stubs.size());
        Map<Integer, CompletableFuture<Void>> futureMap = new HashMap<>();

        for (Integer partyId : stubs.keySet()) {
            log.debug("Sending InitDsgRequest to Signer#{}: keyId={}, sessionId={}",
                    partyId, keyId, dsgSessionId);

            CompletableFuture<Void> future = toCompletableFuture(getStubWithDeadline(partyId)
                    .initDsg(InitDsgRequest.newBuilder()
                            .setKeyId(keyId)
                            .setDsgSessionId(dsgSessionId)
                            .setPartyId(partyId)
                            .setMessageHash(ByteString.copyFrom(messageHash))
                            .setDerivationPath(mpcProperties.getDsg().getEthDerivationPath())
                            .build()))
                    .thenAccept(_ -> {
                        log.debug("Signer#{} succeeded InitDsgRequest", partyId);
                    })
                    .exceptionally(e -> {
                        throw new RuntimeException(String.format("Signer#%d failed InitDsgRequest", partyId), e);
                    });
            futureMap.put(partyId, future);
        }

        try {
            CompletableFuture.allOf(futureMap.values().toArray(CompletableFuture[]::new)).join();
            log.info("All signers initialized successfully");
            return new ArrayList<>(futureMap.keySet()).subList(0, threshold);
        } catch (Exception e) {
            log.warn("Not all signers succeeded. Collecting successful ones...");
            List<Integer> successful = futureMap.entrySet().stream()
                    .filter(entry -> {
                        boolean isDone = entry.getValue().isDone();
                        boolean isSuccess = isDone && !entry.getValue().isCompletedExceptionally();
                        log.debug("Signer#{}: done={}, success={}", entry.getKey(), isDone, isSuccess);
                        return isSuccess;
                    })
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();

            log.info("Successful signers: {}/{} - {}", successful.size(), stubs.size(), successful);
            if (successful.size() < threshold) {
                throw new RuntimeException(
                        String.format("Not enough signers available for DSG quorum. Need: %d, Successful: %d",
                                threshold, successful.size()),
                        e);
            }

            List<Integer> selected = new ArrayList<>(successful.subList(0, threshold));
            log.info("Quorum initialized with {} signers: {}", selected.size(), selected);
            return selected;
        }
    }

    private byte[] processRounds(String dsgSessionId, List<Integer> quorum) {
        log.info("Starting DSG rounds for session: {}", dsgSessionId);

        List<ByteString> currentPayloads = new ArrayList<>();
        int round = 0;

        while (true) {
            log.info("--- DSG Round {}; Quorum {} ---", round++, quorum);

            try {
                List<CompletableFuture<AdvanceDsgResponse>> roundFutures = createRoundFutures(
                        dsgSessionId, quorum, currentPayloads);

                List<AdvanceDsgResponse> responses = roundFutures.stream()
                        .map(CompletableFuture::join)
                        .toList();

                currentPayloads = responses.stream()
                        .map(AdvanceDsgResponse::getOutput)
                        .toList();

                if (responses.get(0).getIsDone()) {
                    byte[] signature = responses.get(0).getOutput().toByteArray();
                    log.info("DSG signature obtained after {} rounds. signature={} bytes", round, signature.length);
                    return signature;
                }

                log.info("DSG round {} completed", round);
            } catch (Exception e) {
                throw new RuntimeException(String.format("DSG round %d failed", round), e);
            }
        }
    }

    private List<CompletableFuture<AdvanceDsgResponse>> createRoundFutures(String dsgSessionId, List<Integer> quorum,
            List<ByteString> payloads) {
        log.debug("Creating AdvanceDsgRequest futures for {} signers with {} payloads", quorum.size(), payloads.size());
        return quorum.stream()
                .map(partyId -> {
                    log.debug("Sending AdvanceDsgRequest to Signer#{} (sessionId={})", partyId, dsgSessionId);
                    return toCompletableFuture(getStubWithDeadline(partyId)
                            .advanceDsg(AdvanceDsgRequest
                                    .newBuilder()
                                    .setDsgSessionId(dsgSessionId)
                                    .setPartyId(partyId)
                                    .addAllPayloads(payloads)
                                    .build()))
                            .thenApply(response -> {
                                log.debug("Signer#{} returned response: {} bytes", partyId,
                                        response.getOutput().size());
                                return response;
                            })
                            .exceptionally(e -> {
                                throw new RuntimeException(
                                        String.format("Signer#%d failed AdvanceDsgRequest", partyId), e);
                            });
                })
                .toList();
    }

    private static final Metadata.Key<String> TRACE_ID_KEY = Metadata.Key.of("x-trace-id",
            Metadata.ASCII_STRING_MARSHALLER);

    private static class TraceIdClientInterceptor implements ClientInterceptor {
        private final String traceId;

        TraceIdClientInterceptor(String traceId) {
            this.traceId = traceId;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    headers.put(TRACE_ID_KEY, traceId);
                    super.start(responseListener, headers);
                }
            };
        }
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
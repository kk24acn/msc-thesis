package uk.ac.herts.orchestrator.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.AdvanceDsgRequest;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.AdvanceDsgResponse;
import uk.ac.herts.orchestrator.grpc.signer.Dsg.InitDsgRequest;
import uk.ac.herts.orchestrator.grpc.signer.DsgServiceGrpc;
import uk.ac.herts.orchestrator.repository.entity.MpcKey;

@Slf4j
@Component
public class DsgCoordinator {

    private final Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> stubs;
    private final String DEFAULT_ETH_DERIVATION_PATH = "m/0";

    public DsgCoordinator(Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> stubs) {
        this.stubs = stubs;
    }

    private DsgServiceGrpc.DsgServiceFutureStub getStubWithDeadline(int partyId) {
        return stubs.get(partyId).withDeadlineAfter(10, TimeUnit.SECONDS);
    }

    public byte[] executeDsg(MpcKey mpcKey, byte[] messageHash) {
        int attempts = 0;
        Exception lastException = null;

        log.info("Starting DSG execution for keyId: {}", mpcKey.getKeyId());
        log.info("Available Signer Stubs: {}", stubs.size());

        while (attempts < 3) {
            String dsgSessionId = UUID.randomUUID().toString();
            try {
                log.info("DSG Attempt {} (Session: {})", attempts + 1, dsgSessionId);
                List<Integer> activeQuorum = initializeQuorum(mpcKey.getKeyId(),
                        dsgSessionId,
                        messageHash,
                        mpcKey.getThreshold());
                log.info("Quorum initialized with {} signers: {}", activeQuorum.size(), activeQuorum);

                byte[] result = processRounds(dsgSessionId, activeQuorum);
                log.info("DSG succeeded on attempt {} with session {}", attempts + 1, dsgSessionId);
                return result;
            } catch (Exception e) {
                attempts++;
                lastException = e;
                log.warn("DSG attempt {} failed for session {}. Error: {} - {}",
                        attempts, dsgSessionId, e.getClass().getSimpleName(), e.getMessage(), e);

                if (attempts < 3) {
                    log.info("Retrying... ({}/3)", attempts);
                }
            }
        }

        log.error("DSG FAILED after all {} attempts", attempts);
        if (lastException != null)
            log.error("Last exception: {}", lastException.getMessage());
        throw new RuntimeException("DSG failed after max retries", lastException);
    }

    private List<Integer> initializeQuorum(String keyId, String dsgSessionId, byte[] messageHash, int threshold) {
        if (stubs.size() < threshold)
            throw new IllegalArgumentException(
                    String.format("Not enough signers available. Threshold required {}; signers available {}",
                            threshold, stubs.size()));

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
                            .setDerivationPath(DEFAULT_ETH_DERIVATION_PATH)
                            .build()))
                    .thenAccept(_ -> {
                        log.debug("Signer#{} succeeded InitDsgRequest", partyId);
                    })
                    .exceptionally(e -> {
                        log.warn("Signer#{} failed InitDsgRequest: {} - {}",
                                partyId, e.getClass().getSimpleName(), e.getMessage());
                        throw new RuntimeException(e);
                    });
            futureMap.put(partyId, future);
        }

        try {
            log.info("Waiting for all signers to initialize...");
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
                log.error("Not enough successful signers. Need {}, got {}", threshold, successful.size());
                throw new RuntimeException("Not enough signers available for DSG quorum. Need: " + threshold +
                        ", Successful: " + successful.size(), e);
            }

            List<Integer> selected = new ArrayList<>(successful.subList(0, threshold));
            log.warn("Proceeding with {} signers instead of all {}: {}", selected.size(), stubs.size(), selected);
            return selected;
        }
    }

    private byte[] processRounds(String dsgSessionId, List<Integer> quorum) {
        List<ByteString> currentPayloads = new ArrayList<>();
        boolean isDone = false;
        byte[] finalSignature = null;
        int round = 0;

        log.info("Starting DSG rounds for session: {}", dsgSessionId);
        while (!isDone) {
            round++;
            log.info("--- DSG Round {} ---", round);
            log.debug("Current quorum signers: {}", quorum);

            try {
                List<CompletableFuture<AdvanceDsgResponse>> roundFutures = createRoundFutures(dsgSessionId, quorum,
                        currentPayloads);

                log.debug("Sending AdvanceDsgRequest to {} signers", roundFutures.size());
                CompletableFuture.allOf(roundFutures.toArray(CompletableFuture[]::new)).join();

                currentPayloads = extractPayloads(roundFutures);
                AdvanceDsgResponse anyResponse = roundFutures.getFirst().join();
                isDone = anyResponse.getIsDone();

                log.info("Round {} completed. isDone={}, outputs={} bytes",
                        round, isDone, currentPayloads.stream().mapToInt(ByteString::size).sum());

                if (isDone) {
                    finalSignature = anyResponse.getOutput().toByteArray();
                    log.info("DSG signature obtained: {} bytes", finalSignature.length);
                }
            } catch (Exception e) {
                log.error("Round {} failed: {} - {}", round, e.getClass().getSimpleName(), e.getMessage(), e);
                throw new RuntimeException("DSG round " + round + " failed", e);
            }
        }

        log.info("DSG rounds completed after {} rounds", round);
        return finalSignature;
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
                                log.warn("Signer#{} failed AdvanceDsgRequest: {}", partyId, e.getMessage());
                                throw new RuntimeException(e);
                            });
                })
                .toList();
    }

    private List<ByteString> extractPayloads(List<CompletableFuture<AdvanceDsgResponse>> roundFutures) {
        return roundFutures.stream()
                .map(future -> future.join().getOutput())
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
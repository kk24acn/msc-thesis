package uk.ac.herts.orchestrator.exception.mpc;

import io.grpc.StatusRuntimeException;
import lombok.Getter;

@Getter
public class DsgRoundException extends RuntimeException {
    private final int failedSignerPartyId;
    private final int failedAtRound;
    private final String grpcStatusCode;
    private final String grpcDescription;

    public DsgRoundException(String message, Throwable cause, int failedSignerPartyId,
            int failedAtRound, String grpcStatusCode, String grpcDescription) {
        super(message, cause);
        this.failedSignerPartyId = failedSignerPartyId;
        this.failedAtRound = failedAtRound;
        this.grpcStatusCode = grpcStatusCode;
        this.grpcDescription = grpcDescription;
    }

    public static DsgRoundException buildException(int partyId, int round, String phase, Throwable cause) {
        StatusRuntimeException grpcException = extractGrpcException(cause);
        String statusCode = grpcException != null ? grpcException.getStatus().getCode().name() : "UNKNOWN";
        String description = grpcException != null ? grpcException.getStatus().getDescription() : cause.getMessage();

        String message = String.format("Signer#%d failed ExecuteDsgPhase (%s): %s",
                partyId, phase, description);

        return new DsgRoundException(message, cause, partyId, round, statusCode, description);
    }

    public static StatusRuntimeException extractGrpcException(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof StatusRuntimeException e) {
                return e;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }

    public static int extractReportingPartyId(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof DsgRoundException e && e.getFailedSignerPartyId() != -1) {
                return e.getFailedSignerPartyId();
            }
            current = current.getCause();
            depth++;
        }
        return -1;
    }

    public static String extractGrpcDescription(Throwable throwable) {
        StatusRuntimeException grpcException = extractGrpcException(throwable);
        if (grpcException != null && grpcException.getStatus().getDescription() != null) {
            return grpcException.getStatus().getDescription();
        }
        return throwable.getMessage();
    }
}

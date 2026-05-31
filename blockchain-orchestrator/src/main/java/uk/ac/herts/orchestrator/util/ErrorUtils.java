package uk.ac.herts.orchestrator.util;

public final class ErrorUtils {

    private ErrorUtils() {
    }

    public static String buildErrorMessage(String phase, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(phase).append(".");

        String mainMsg = e.getMessage();
        if (mainMsg != null && !mainMsg.isEmpty()) {
            sb.append(" ").append(mainMsg);
        }

        Throwable cause = e.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && !causeMsg.isEmpty()) {
                sb.append(" (Root cause: ").append(cause.getClass().getSimpleName())
                        .append(" - ").append(causeMsg).append(")");
            }
        }

        return sb.toString();
    }
}

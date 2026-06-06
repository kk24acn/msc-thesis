package uk.ac.herts.orchestrator.util;

public final class ErrorUtils {

    private ErrorUtils() {
    }

    public static String buildErrorMessage(String phase, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(phase).append(".");

        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 5) {
            String msg = current.getMessage();
            if (msg != null && !msg.isEmpty()) {
                if (depth == 0) {
                    sb.append(" ").append(msg);
                } else {
                    sb.append(" (Caused by: ").append(current.getClass().getSimpleName())
                            .append(" - ").append(msg).append(")");
                }
            }
            current = current.getCause();
            depth++;
        }

        return sb.toString();
    }
}

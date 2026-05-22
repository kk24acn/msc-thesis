package uk.ac.herts.orchestrator.client.mpc.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class TraceIdClientInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> TRACE_ID_KEY = Metadata.Key.of("x-trace-id",
            Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> RETRY_COUNT_KEY = Metadata.Key.of("x-retry-count",
            Metadata.ASCII_STRING_MARSHALLER);

    private final String traceId;
    private final int retryCount;

    public TraceIdClientInterceptor(String traceId, int retryCount) {
        this.traceId = traceId;
        this.retryCount = retryCount;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (traceId != null) {
                    headers.put(TRACE_ID_KEY, traceId);
                }
                headers.put(RETRY_COUNT_KEY, String.valueOf(retryCount));
                super.start(responseListener, headers);
            }
        };
    }
}

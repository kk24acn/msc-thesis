package uk.ac.herts.orchestrator.client.mpc.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.Channel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.grpc.signer.DsgServiceGrpc;
import uk.ac.herts.orchestrator.grpc.signer.DsgServiceGrpc.DsgServiceFutureStub;

@Slf4j
@Configuration
public class MpcGrpcStubConfig {

    @Bean
    public Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> dsgServiceStubs(MpcProperties mpcProperties) {
        Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> stubs = mpcProperties.getSigners().stream()
                .collect(Collectors.toMap(
                        MpcProperties.SignerNode::getId,
                        node -> {
                            log.info("Creating gRPC stub for Signer#{} at address: {}",
                                    node.getId(), node.getAddress());
                            try {
                                Channel channel = NettyChannelBuilder
                                        .forTarget(node.getAddress())
                                        .usePlaintext()
                                        .build();
                                DsgServiceFutureStub stub = DsgServiceGrpc.newFutureStub(channel);
                                log.info("DSG Service stub created for Signer#{} ({})",
                                        node.getId(), node.getAddress());
                                return stub;
                            } catch (Exception e) {
                                throw new RuntimeException(
                                        String.format("Failed to create gRPC stub for Signer#%s at %s",
                                                node.getId(),
                                                node.getAddress()),
                                        e);
                            }
                        }));
        log.info("gRPC Stubs initialized successfully. Total stubs: {}", stubs.size());
        return stubs;
    }
}

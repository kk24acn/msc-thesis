package uk.ac.herts.orchestrator.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.grpc.signer.DsgServiceGrpc;

@Slf4j
@Configuration
public class GrpcStubConfig {

    @Bean
    public Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> dsgServiceStubs(MpcSignerProperties properties) {
        Map<Integer, DsgServiceGrpc.DsgServiceFutureStub> stubs = properties.getSigners().stream()
                .collect(Collectors.toMap(
                        MpcSignerProperties.SignerNode::getId,
                        node -> {
                            log.info("Creating gRPC stub for Signer#{} at address: {}", node.getId(),
                                    node.getAddress());
                            try {
                                var channel = NettyChannelBuilder
                                        .forTarget(node.getAddress())
                                        .usePlaintext()
                                        .build();
                                log.info("Channel created for Signer#{} ({})", node.getId(), node.getAddress());

                                var stub = DsgServiceGrpc.newFutureStub(channel);
                                log.info("DSG Service stub created for Signer#{}", node.getId());
                                return stub;
                            } catch (Exception e) {
                                log.error("Failed to create stub for Signer#{} at {}: {}",
                                        node.getId(), node.getAddress(), e.getMessage(), e);
                                throw new RuntimeException("Failed to create gRPC stub for " + node.getAddress(), e);
                            }
                        }));

        log.info("gRPC Stubs initialized successfully. Total stubs: {}", stubs.size());
        return stubs;
    }
}
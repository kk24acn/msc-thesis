package uk.ac.herts.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import signer.SignerGrpc;

@Configuration
public class GrpcConfig {

    @Bean
    SignerGrpc.SignerBlockingStub stub(GrpcChannelFactory channels) {
        return SignerGrpc.newBlockingStub(channels.createChannel("signer"));
    }
}

package uk.ac.herts.orchestrator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@EnableConfigurationProperties(HardhatProperties.class)
public class Web3Config {

    @Bean
    public Web3j web3j(HardhatProperties hardhatProperties) {
        return Web3j.build(new HttpService(hardhatProperties.getRpcUrl()));
    }

    @Bean
    public Credentials signerCredentials(HardhatProperties hardhatProperties) {
        if (!StringUtils.hasText(hardhatProperties.getPrivateKey())) {
            throw new IllegalStateException("Missing hardhat.private-key configuration");
        }
        return Credentials.create(hardhatProperties.getPrivateKey());
    }
}

package uk.ac.herts.orchestrator.client.blockchain.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "spring.hardhat")
public class HardhatProperties {
    private String rpcUrl;
    private int maxRetries;
    private Long gasLimit;
    private Duration retryBackoff;
    private Duration gasPriceCacheTtl;
    private Duration blockNumberCacheTtl;
}

package uk.ac.herts.orchestrator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigInteger;
import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "spring.hardhat")
public class HardhatProperties {
    private String rpcUrl = "http://127.0.0.1:8545";
    private String privateKey;
    private Duration transactionTimeout = Duration.ofSeconds(20);
    private int maxRetries = 3;
    private Duration retryBackoff = Duration.ofMillis(300);
    private Duration receiptPollInterval = Duration.ofSeconds(1);
    private BigInteger gasLimit = BigInteger.valueOf(21_000);
    private Long chainId;
    private int recoveryConcurrency = 4;
}

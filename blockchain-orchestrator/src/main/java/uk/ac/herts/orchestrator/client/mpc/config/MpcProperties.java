package uk.ac.herts.orchestrator.client.mpc.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.mpc")
public class MpcProperties {
    private int grpcConcurrencyLimit;
    private String signerAddresses;
    private DsgProperties dsg = new DsgProperties();
    private QuarantineProperties quarantine = new QuarantineProperties();

    public List<SignerNode> getSigners() {
        if (signerAddresses != null && !signerAddresses.trim().isEmpty()) {
            log.info("Parsing signer addresses: {}", signerAddresses);
            List<SignerNode> parsedSigners = new ArrayList<>();
            String[] addresses = signerAddresses.split(",");

            for (int i = 0; i < addresses.length; i++) {
                SignerNode node = new SignerNode();
                node.setId(i);
                node.setAddress(addresses[i].trim());
                parsedSigners.add(node);
                log.info("Signer #{}: {} (partyId={})", i, node.getAddress(), i);
            }

            log.info("Successfully configured {} signer nodes", parsedSigners.size());
            return parsedSigners;
        }

        log.warn("No signer configuration found!");
        return new ArrayList<>();
    }

    @Getter
    @Setter
    public static class SignerNode {
        private int id;
        private String address;
    }

    @Getter
    @Setter
    public static class DsgProperties {
        private int maxRetries;
        private int maxRounds;
        private Duration requestTimeout;
        private String ethDerivationPath;
    }

    @Getter
    @Setter
    public static class QuarantineProperties {
        private QuarantineStrategyFactory.QuarantineMode mode;
        private long evictionIntervalMs;
        private Duration ttl = Duration.ofSeconds(30);
        private int probeInterval = 10;
        private int failureThreshold = 5;
    }
}

package uk.ac.herts.orchestrator.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "spring.mpc")
@Getter
@Setter
public class MpcSignerProperties {
    private String signerAddresses;

    public List<SignerNode> getSigners() {
        if (signerAddresses != null && !signerAddresses.trim().isEmpty()) {
            log.info("Parsing SIGNER_RPC_URLS environment variable: {}", signerAddresses);
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

        log.warn("No signer configuration found! SIGNER_RPC_URLS env var is empty or not set");
        return new ArrayList<>();
    }

    @Getter
    @Setter
    public static class SignerNode {
        private int id;
        private String address;
    }
}
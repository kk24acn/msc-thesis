package uk.ac.herts.orchestrator.client.mpc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import uk.ac.herts.orchestrator.client.mpc.quarantine.BlindQuarantineStrategy;
import uk.ac.herts.orchestrator.client.mpc.quarantine.CircuitBreakerQuarantineStrategy;
import uk.ac.herts.orchestrator.client.mpc.quarantine.DisabledQuarantineStrategy;
import uk.ac.herts.orchestrator.client.mpc.quarantine.QuarantineStrategy;

@Slf4j
@Configuration
public class QuarantineStrategyFactory {

    @Bean
    public QuarantineStrategy quarantineStrategy(MpcProperties mpcProperties) {
        MpcProperties.QuarantineProperties config = mpcProperties.getQuarantine();
        QuarantineStrategy strategy = switch (config.getMode()) {
            case QuarantineMode.DISABLED -> new DisabledQuarantineStrategy();
            case QuarantineMode.SOFT -> new BlindQuarantineStrategy(config, false);
            case QuarantineMode.STRICT -> new BlindQuarantineStrategy(config, true);
            case QuarantineMode.CIRCUIT_BREAKER -> new CircuitBreakerQuarantineStrategy(config, false);
        };
        log.info("Quarantine strategy initialized: {} (mode: {})",
                strategy.getClass().getSimpleName(), config.getMode());
        return strategy;
    }

    public enum QuarantineMode {
        DISABLED, // Quarantine OFF
        SOFT, // Refuses to quarantine if it would break the threshold
        STRICT, // Quarantines immediately with ability to break the threshold
        CIRCUIT_BREAKER // Quarantines after n failures in row, periodically probes quarantined nodes
    }
}

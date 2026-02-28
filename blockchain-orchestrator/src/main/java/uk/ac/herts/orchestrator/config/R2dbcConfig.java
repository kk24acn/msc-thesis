package uk.ac.herts.orchestrator.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;

// TODO Check for possible removal
@Configuration
@Profile("reactive")
public class R2dbcConfig {

    @Bean
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get("r2dbc:postgresql://localhost:5432/mydb");
    }

    @Bean
    public R2dbcTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
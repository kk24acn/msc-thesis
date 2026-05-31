package uk.ac.herts.orchestrator.client.blockchain.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

@Configuration
@EnableConfigurationProperties(HardhatProperties.class)
public class Web3Config {

    @Bean
    public Web3j web3j(HardhatProperties hardhatProperties) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(100);
        dispatcher.setMaxRequestsPerHost(100);
        ConnectionPool connectionPool = new ConnectionPool(50, 5, TimeUnit.MINUTES);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .build();
        return Web3j.build(new HttpService(hardhatProperties.getRpcUrl(), httpClient));
    }
}

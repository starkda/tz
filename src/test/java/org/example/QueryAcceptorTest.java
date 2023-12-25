package org.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.example.runners.CurrencyUpdater;
import org.example.runners.QueryAcceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;

public class QueryAcceptorTest {
    ElasticsearchClient esClient;
    ElasticsearchContainer container;

    @BeforeEach
    public void initClient() {
        container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.16.2");
        container.start();
        RestClient restClient = RestClient
                .builder(HttpHost.create(container.getHttpHostAddress()))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        esClient = new co.elastic.clients.elasticsearch.ElasticsearchClient(transport);
    }

    @AfterEach
    public void releaseResources() {
        container.stop();
    }


    @Test
    @DisplayName("it should return average value of btc for last hour")
    public void getAveragePriceTest() throws URISyntaxException, IOException, InterruptedException {
        List<CurrencyEntry> currencyEntries =
                List.of(new CurrencyEntry("btc", 1, 1, System.currentTimeMillis()),
                        new CurrencyEntry("btc", 3, 1, System.currentTimeMillis()
                                - 100),
                        new CurrencyEntry("btc", 10, 1, System.currentTimeMillis()
                                - 1000 * 60 * 60 - 20),
                        new CurrencyEntry("eth", 12, 1, System.currentTimeMillis()));

        CurrencyUpdater currencyUpdater =
                Mockito.spy(new CurrencyUpdater(esClient, "kudato", List.of("kek", "mem")));
        Mockito.doReturn(currencyEntries).when(currencyUpdater).makeAPICall(any(), any());


        QueryAcceptor queryAcceptor = new QueryAcceptor(esClient);
        currencyUpdater.run();
        Thread.sleep(1000);// видимо эластику нужно время чтобы изменения появилис
        double val = queryAcceptor.getAveragePrice("btc");
        Assertions.assertEquals(2, val);
    }

    @Test
    @DisplayName("it should return max percentage change among all currencies for the last 24hrs")
    public void getMaxChangeTest() throws URISyntaxException, IOException, InterruptedException {
        List<CurrencyEntry> currencyEntries =
                List.of(new CurrencyEntry("btc", 1, 1, System.currentTimeMillis()),
                        new CurrencyEntry("btc", 3, 100, System.currentTimeMillis()
                                - 100),
                        new CurrencyEntry("avax", 10, 3, System.currentTimeMillis()
                                - 1000 * 60 * 60),
                        new CurrencyEntry("eth", 10, 3, System.currentTimeMillis()
                                - 60));

        CurrencyUpdater currencyUpdater =
                Mockito.spy(new CurrencyUpdater(esClient, "kudato", List.of("kek", "mem")));
        Mockito.doReturn(currencyEntries).when(currencyUpdater).makeAPICall(any(), any());

        QueryAcceptor queryAcceptor = new QueryAcceptor(esClient);

        currencyUpdater.run();
        Thread.sleep(1000);
        List<String> indexes = queryAcceptor.getMaxChange();
        Assertions.assertArrayEquals(List.of("avax", "eth").toArray(), indexes.toArray());
    }
}

package org.example.runners;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.CurrencyEntry;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CurrencyUpdater implements Runnable {
    public CurrencyUpdater(ElasticsearchClient es, String uri, List<String> apiKeys) {
        this.elasticsearchClient = es;
        this.uri = uri;
        this.apiKeys = apiKeys;
    }

    public String uri = "";

    public List<String> apiKeys = new ArrayList<>();

    static volatile AtomicInteger curKey = new AtomicInteger(0);

    ElasticsearchClient elasticsearchClient;

    @Override
    public void run() {
        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        List<CurrencyEntry> entries = null;
        try {
            entries = makeAPICall(uri, parameters);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
        entries.stream().forEach(currencyEntry ->
        {
            try {
                elasticsearchClient.index(i -> i
                        .index(currencyEntry.getName())
                        .id(String.valueOf(currencyEntry.getTime()))
                        .document(currencyEntry));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

    public List<CurrencyEntry> makeAPICall(String uri, List<NameValuePair> parameters)
            throws URISyntaxException, IOException {
        String response_content = "";

        String key = apiKeys.get(curKey.get());
        URIBuilder query = new URIBuilder(uri);
        query.addParameters(parameters);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet request = new HttpGet(query.build());

        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.addHeader("X-CMC_PRO_API_KEY", key);

        try (CloseableHttpResponse response = client.execute(request)) {
            if (response.getStatusLine().getStatusCode() == 429) {
                curKey.set(Integer.parseInt(key) == curKey.get() % apiKeys.size() ? curKey.get() + 1 : curKey.get());
                System.out.println(String.format("thread %s exceed its requests quota. apiKey was %s, changing to %s",
                        Thread.currentThread().getName(), key, apiKeys.get(curKey.get())));
                return List.of();
            }
            System.out.println(String.format("Succesfully got data in thread %s", Thread.currentThread().getName()));
            HttpEntity entity = response.getEntity();
            response_content = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response_content);
        ArrayNode currencyArray = (ArrayNode) rootNode.at("/data");
        List<CurrencyEntry> currencyEntries = new ArrayList<>();
        Iterator<JsonNode> iterator = currencyArray.elements();
        while (iterator.hasNext()) {
            JsonNode employeeNode = iterator.next();
            String name = employeeNode.at("/symbol").asText().toLowerCase();
            double value = employeeNode.at("/quote/USD/price").asDouble();
            double percentage = employeeNode.at("/quote/USD/percent_change_24h").asDouble();
            currencyEntries.add(new CurrencyEntry(name, value, percentage, System.currentTimeMillis()));
        }

        return currencyEntries;
    }
}

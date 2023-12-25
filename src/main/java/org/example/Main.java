package org.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.example.runners.CurrencyUpdater;
import org.example.runners.QueryAcceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


public class Main {

    public static void main(String[] args) throws InterruptedException {

        QueryAcceptor.sc = new Scanner(System.in);

        String uri = args[0];
        List<String> apiKeys = new ArrayList<>();
        for (int i = 1; i < args.length - 1; i++){
           apiKeys.add(args[i]);
        }

        RestClient restClient = RestClient
                .builder(HttpHost.create(args[args.length - 1]))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        ElasticsearchClient esClient = new ElasticsearchClient(transport);
        Thread requester = (new Thread(new QueryAcceptor(esClient)));

        ThreadPoolExecutor poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        requester.start();
        while (true) {
            poolExecutor.submit(new CurrencyUpdater(esClient, uri, apiKeys));
            Thread.sleep(1000);
        }
    }
}
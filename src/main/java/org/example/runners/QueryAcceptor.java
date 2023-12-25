package org.example.runners;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.AvgAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.MaxAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import lombok.AllArgsConstructor;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.example.CurrencyEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

public class QueryAcceptor implements Runnable {


    @AllArgsConstructor
    class IndexTime {
        String index;
        long value;
    }

    @AllArgsConstructor
    class IndexValue {
        String index;
        double value;
    }

    ElasticsearchClient esClient;

    public QueryAcceptor(ElasticsearchClient es) {
        this.esClient = es;
    }

    public static Scanner sc;
    @Override
    public void run() {
        System.out.println("thread id: " + Thread.currentThread().getName());
        System.out.println("""
                You can perform 3 actions:
                 2 currency - average price of specific currency for the last hrs
                 3 - max percentage change for the last 24hrs""");

        while (true) {
            switch (sc.nextInt()) {
                case 2 -> {
                    try {
                        System.out.println("request started");
                        System.out.printf("average price is: %s", getAveragePrice(sc.next()));
                        System.out.println("request passed");

                    } catch (IOException e) {
                       e.printStackTrace();
                    }
                }

                case 3 -> {
                    try {
                        System.out.println("request started");
                        Stream<String> indexes = getMaxChange().stream();
                        System.out.println("biggest percentage change for last 24 hrs have:");
                        indexes.forEach(System.out::println);
                        System.out.println("request passed");
                    } catch (IOException | NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }


    IndexTime getIndexValue(String index) throws IOException {
        Query query = RangeQuery.of(m -> m
                .field("time")
        )._toQuery();

        SearchResponse<Void> response = esClient.search(b -> b
                        .index(index)
                        .size(0)
                        .query(query)
                        .aggregations("time_val_" + index, a -> a
                                .max(c -> c.field("time"))
                        ),
                Void.class
        );
        MaxAggregate max = response.aggregations().get("time_val_" + index).max();
        double ret = max.value();
        IndexTime indexTime = new IndexTime(index, (long) ret);
        return indexTime;
    }

    public List<String> getMaxChange() throws IOException, NullPointerException {
        List<String> indices = getIndices();
        List<String> stringValues = indices.stream().filter(x -> !x.equals(".geoip_databases")).toList();
        List<IndexTime> indexTimes = new ArrayList<>();
        for (String str: stringValues){
            indexTimes.add(getIndexValue(str));
        }

        List<IndexValue> indexValues = new ArrayList<>();
            for (IndexTime indexTime : indexTimes) {
                CurrencyEntry currencyEntry = esClient.get(a -> a.index(indexTime.index).
                        id(String.valueOf(indexTime.value)), CurrencyEntry.class).source();
                IndexValue indexValue = new IndexValue(indexTime.index, currencyEntry.getPercentage());
                indexValues.add(indexValue);
            }
        double maxValue = indexValues.stream().max(Comparator.comparingDouble(a -> a.value))
                .orElseThrow(() -> new IOException("no currencies were present")).value;
        return indexValues.stream().filter(indexValue -> indexValue.value == maxValue).map(indexValue -> indexValue.index).toList();

    }

    public double getAveragePrice(String currency) throws IOException {
        getIndices().stream().filter(x -> x.equals(currency)).findFirst()
                .orElseThrow(() -> new IOException(String.format("currency %s not found", currency)));

        Query query = RangeQuery.of(m -> m
                .field("time")
                .gt(JsonData.of(System.currentTimeMillis() - 1000 * 60 * 60))
        )._toQuery();

        SearchResponse<Void> response = esClient.search(b -> b
                        .index(currency)
                        .size(0)
                        .query(query)
                        .aggregations("average_val_" + currency, a -> a
                                .avg(c -> c.field("currentValue"))
                        ),
                Void.class
        );
        AvgAggregate avg = response.aggregations().get("average_val_" + currency).avg();
        return avg.value();
    }

    List<String> getIndices() throws IOException {
        List<String> indices = new ArrayList<>();
        IndicesResponse indicesResponse = esClient.cat().indices();
        indicesResponse.valueBody().stream().forEach(record -> indices.add(record.index()));

        return indices;
    }

}

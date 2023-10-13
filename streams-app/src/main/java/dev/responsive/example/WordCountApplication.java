/*
 * Copyright 2023 Responsive Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.responsive.example;

import dev.responsive.kafka.api.ResponsiveKafkaStreams;
import dev.responsive.kafka.api.config.ResponsiveConfig;
import dev.responsive.kafka.api.stores.ResponsiveKeyValueParams;
import dev.responsive.kafka.api.stores.ResponsiveStores;
import java.util.Arrays;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;


public class WordCountApplication {
  public static void main(final String[] args) throws Exception {
    Properties props = new Properties();

    // Kafka Streams Configs
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-application");
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Responsive Configs
    props.put(ResponsiveConfig.STORAGE_HOSTNAME_CONFIG, "localhost");
    props.put(ResponsiveConfig.STORAGE_PORT_CONFIG, "9042");
    props.put(ResponsiveConfig.STORAGE_DATACENTER_CONFIG, "datacenter1");
    props.put(ResponsiveConfig.TENANT_ID_CONFIG, "quickstart");

    StreamsBuilder builder = new StreamsBuilder();
    KStream<String, String> textLines = builder.stream("plaintext-input");
    KTable<String, Long> wordCounts = textLines
        .flatMapValues(textLine -> Arrays.asList(textLine.toLowerCase().split("\\W+")))
        .groupBy((key, word) -> word)
        .count(ResponsiveStores.materialized(ResponsiveKeyValueParams.keyValue("counts-store")));
    wordCounts
        .toStream()
        .peek((word, count) -> System.out.println(word + ": " + count))
        .to("wordcount-output", Produced.with(Serdes.String(), Serdes.Long()));

    KafkaStreams streams = new ResponsiveKafkaStreams(builder.build(), props);
    streams.start();
  }
}

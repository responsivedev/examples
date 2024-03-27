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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;

@SuppressWarnings("ALL")
public class Main {

  private static final String[] PROPERTIES_PATHS = new String[] {
      "/configs/app.properties",
      "/secrets/responsive-creds.properties",
      "/secrets/kafka-creds.properties",
      "/secrets/ccloud-sr-creds.properties",
  };

  public static final String INPUT_TOPIC = "responsive-example-input";
  public static final String OUTPUT_TOPIC = "responsive-example-output";

  public static void main(final String[] args) throws Exception {
    final Properties props = ConfigUtils.loadConfigs(PROPERTIES_PATHS);
    setUp(props);

    if (args.length > 0 && "--generator".equalsIgnoreCase(args[0])) {
      generate(props);
    } else {
      consume(props);
    }
  }

  static void generate(Properties props) throws Exception {
    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      executorService.shutdownNow();
      System.exit(0);
    }));

    executorService.submit(new Generator(new KafkaProducer<>(props)));
  }

  static void consume(Properties props) throws Exception {
    final Map<String, Object> config = new HashMap<>();
    props.forEach((k, v) -> config.put((String) k, v));

    final Topology topology = topology();
    final KafkaStreams streams = new KafkaStreams(topology, props);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      streams.close();
      System.exit(0);
    }));

    streams.start();
  }

  static void setUp(final Properties props) {
    final Admin admin = Admin.create(props);
    try {
      admin.createTopics(List.of(
          new NewTopic(INPUT_TOPIC, Optional.of(8), Optional.empty()),
          new NewTopic(OUTPUT_TOPIC, Optional.of(1), Optional.empty())
      )).all().get();
    } catch (final Exception ignored) {
    }
  }

  static Topology topology() {
    final StreamsBuilder builder = new StreamsBuilder();
    // Serializers/deserializers (serde) for String and Long types
    final Serde<String> stringSerde = Serdes.String();
    final Serde<Long> longSerde = Serdes.Long();

    // Construct a `KStream` from the input topic, where message values
    // represent lines of text (for the sake of this example, we ignore whatever may be stored
    // in the message keys).
    final KStream<String, String> textLines = builder.stream(
        INPUT_TOPIC,
        Consumed.with(stringSerde, stringSerde)
    );

    final KTable<String, Long> wordCounts = textLines
        // Split each text line, by whitespace, into words.
        .flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\W+")))
        // Group the text words as message keys
        .groupBy((key, value) -> value)
        // Count the occurrences of each word (message key).
        .count(Materialized.as(Stores.persistentKeyValueStore("word-counts")));

    // Store the running counts as a changelog stream to the output topic.
    wordCounts
        .toStream()
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));

    return builder.build();
  }
}

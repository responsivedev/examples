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

import static dev.responsive.example.ConfigUtils.loadConfig;

import dev.responsive.kafka.api.ResponsiveKafkaStreams;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.Stores;

@SuppressWarnings("ALL")
public class Main {

  public static final String INPUT_TOPIC = "input";
  public static final String OUTPUT_TOPIC = "output";

  public static void main(final String[] args) throws Exception {
    final Properties props = loadConfig("/mnt/app.properties");
    setUp(props);

    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    if (args.length > 0 && "--generator".equalsIgnoreCase(args[0])) {
      executorService.submit(new Generator(new KafkaProducer<>(props)));
      while (!Thread.interrupted()) {
        Thread.sleep(10_000);
      }
      executorService.shutdown();
    } else {
      final Map<String, Object> config = new HashMap<>();
      props.forEach((k, v) -> config.put((String) k, v));

      final Topology topology = topology();
      final ResponsiveKafkaStreams streams = new ResponsiveKafkaStreams(topology, props);

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        executorService.shutdown();
        streams.close();
      }));

      streams.start();
    }
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
    final KStream<String, String> input = builder.stream(INPUT_TOPIC);
    input.groupByKey()
        .count(Materialized.as(Stores.persistentKeyValueStore("COUNT")))
        .toStream()
        // don't spam the output topic
        .filter((k, v) -> k.startsWith("aa"))
        .to(OUTPUT_TOPIC);
    return builder.build();
  }
}

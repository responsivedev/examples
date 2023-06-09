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

import dev.responsive.kafka.api.ResponsiveDriver;
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
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;

public class Main {

  public static void main(final String[] args) throws Exception {
    // TODO(agavra): update the ResponsiveDriver to accept Properties files
    final Properties props = loadConfig();
    props.put("sasl.jaas.config", System.getenv("SASL_JAAS_CONFIG"));
    props.put("responsive.client.secret", System.getenv("RESPONSIVE_CLIENT_SECRET"));

    final Admin admin = Admin.create(props);
    try {
      admin.createTopics(List.of(
          new NewTopic("people", Optional.of(2), Optional.empty()),
          new NewTopic("bids", Optional.of(2), Optional.empty())
      ));
    } catch (final UnknownTopicOrPartitionException ignored) {
    }

    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(new Generator(new KafkaProducer<>(props)));

    final Map<String, Object> config = new HashMap<>();
    props.forEach((k, v) -> config.put((String) k, v));

    final ResponsiveDriver driver = ResponsiveDriver.connect(config);
    final Topology topology = topology(driver);
    final KafkaStreams streams = new KafkaStreams(topology, new StreamsConfig(config));

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        executorService.shutdown();
        streams.close();
        driver.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }));

    streams.start();
  }

  static Topology topology(final ResponsiveDriver driver) {
    final StreamsBuilder builder = new StreamsBuilder();

    // schema for bids is key: <bid_id> value: <bid_id, amount, person_id>
    final KStream<String, String> bids = builder.stream("bids");
    // schema for people is key: <person_id> value: <person_id, name, state>
    final KTable<String, String> people = builder.table("people", driver.materialized("people"));

    bids
        // person_id is 3rd column
        .selectKey((k, v) -> v.split(",")[2])
        // schema is now <bid_id, amount, person_id, name, state>
        .join(people, (bid, person) -> bid + person)
        // state is the 5th column
        .filter((k, v) -> v.split(",")[4].equals("CA"))
        .to("output");

    return builder.build();
  }

  static Properties loadConfig() throws IOException {
    final Properties cfg = new Properties();
    try (InputStream inputStream = Main.class.getResourceAsStream("/app.properties")) {
      cfg.load(inputStream);
    }
    return cfg;
  }
}
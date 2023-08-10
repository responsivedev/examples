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
import dev.responsive.kafka.api.ResponsiveStores;
import dev.responsive.kafka.store.ResponsiveStoreBuilder;
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
import org.apache.kafka.common.serialization.Serdes.StringSerde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

public class Main {

  public static final String INPUT_TOPIC = "input";
  public static final String DELETES_TOPIC = "input-deletes";
  public static final String OUTPUT_TOPIC = "output";

  private static final String STATE_STORE = "state-store";

  public static void main(final String[] args) throws Exception {
    final Properties props = loadConfig();
    props.put("sasl.jaas.config", System.getenv("SASL_JAAS_CONFIG"));
    props.put("responsive.client.secret", System.getenv("RESPONSIVE_CLIENT_SECRET"));
    props.put("responsive.client.id", System.getenv("RESPONSIVE_CLIENT_ID"));

    final Admin admin = Admin.create(props);
    try {
      admin.createTopics(List.of(
          new NewTopic(INPUT_TOPIC, Optional.of(32), Optional.empty()),
          new NewTopic(DELETES_TOPIC, Optional.of(32), Optional.empty()),
          new NewTopic(OUTPUT_TOPIC, Optional.of(2), Optional.empty())
      ));
    } catch (final UnknownTopicOrPartitionException ignored) {
    }

    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(new Generator(new KafkaProducer<>(props)));

    final Map<String, Object> config = new HashMap<>();
    props.forEach((k, v) -> config.put((String) k, v));

    final Topology topology = topology();
    final KafkaStreams streams = ResponsiveKafkaStreams.create(topology, config);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      executorService.shutdown();
      streams.close();
    }));

    streams.start();
  }

  static Topology topology() {
    final StreamsBuilder builder = new StreamsBuilder();
    final KStream<String, String> input = builder.stream(List.of(INPUT_TOPIC, DELETES_TOPIC));

    final Predicate<String, String> expirationPredicate = (key, value) -> value == null;
    final Branched<String, String> expirationBranch = Branched.withConsumer(
        expirationStream -> expirationStream.process(
            ExpiredEventProcessor::new,
            Named.as("expiration-processor"),
            STATE_STORE));

    final Branched<String, String> dedupe = Branched.withConsumer(
        eventStream -> eventStream.processValues(
            DedupeProcessor::new,
            Named.as("dedupe-processor"),
            STATE_STORE)
            .filter((k, v) -> k.startsWith("aa")) // reduce kafka writes
            .to(OUTPUT_TOPIC));

    builder.addStateStore(
        new ResponsiveStoreBuilder<>(
            Stores.timestampedKeyValueStoreBuilder(
                ResponsiveStores.timestampedFactStore(STATE_STORE),
                new StringSerde(),
                new StringSerde()),
            true)
    );
    input.split()
        .branch(expirationPredicate, expirationBranch)
        .defaultBranch(dedupe);

    return builder.build();
  }

  static Properties loadConfig() throws IOException {
    final Properties cfg = new Properties();
    try (InputStream inputStream = Main.class.getResourceAsStream("/app.properties")) {
      cfg.load(inputStream);
    }
    return cfg;
  }

  private static class ExpiredEventProcessor implements Processor<String, String, String, String> {

    TimestampedKeyValueStore<String, String> store;

    @Override
    public void init(final ProcessorContext<String, String> context) {
      store = context.getStateStore(STATE_STORE);
    }

    @Override
    public void process(final Record<String, String> record) {
      // don't delete records that have only been in the store
      // for less than 1 minute
      final ValueAndTimestamp<String> valAndTs = store.get(record.key());
      if (valAndTs == null || valAndTs.timestamp() + 30_000 < record.timestamp()) {
        return;
      }
      store.delete(record.key());
    }
  }

  private static class DedupeProcessor
      implements FixedKeyProcessor<String, String, String> {

    TimestampedKeyValueStore<String, String> store;
    FixedKeyProcessorContext<String, String> context;

    @Override
    public void init(final FixedKeyProcessorContext<String, String> context) {
      store = context.getStateStore(STATE_STORE);
      this.context = context;
    }

    @Override
    public void process(final FixedKeyRecord<String, String> record) {
      final ValueAndTimestamp<String> seen = store.putIfAbsent(
          record.key(),
          ValueAndTimestamp.make("SEEN", context.currentStreamTimeMs())
      );

      if (seen == null) {
        context.forward(record);
      }
    }
  }
}

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

import dev.responsive.kafka.api.StreamsStoreDriver;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes.StringSerde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainTest {

  @Test
  public void test() {
    // Given:
    Properties props = new Properties();
    props.putAll(Map.of(
        StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, StringSerde.class,
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, StringSerde.class
    ));
    TopologyTestDriver testDriver = new TopologyTestDriver(Main.topology(new StreamsStoreDriver() {
      @Override
      public KeyValueBytesStoreSupplier kv(final String name) {
        return null;
      }

      @Override
      public KeyValueBytesStoreSupplier timestampedKv(final String name) {
        return Stores.persistentTimestampedKeyValueStore(name);
      }

      @Override
      public <K, V> StoreBuilder<TimestampedKeyValueStore<K, V>> timestampedKeyValueStoreBuilder(
          final String name, final Serde<K> keySerde, final Serde<V> valueSerde) {
        return Stores.timestampedKeyValueStoreBuilder(timestampedKv(name), keySerde, valueSerde);
      }

      @Override
      public KeyValueBytesStoreSupplier globalKv(final String name) {
        return null;
      }

      @Override
      public WindowBytesStoreSupplier windowed(final String name, final long retentionMs,
          final long windowSize,
          final boolean retainDuplicates) {
        return null;
      }

      @Override
      public <K, V> Materialized<K, V, KeyValueStore<Bytes, byte[]>> materialized(
          final String name) {
        return null;
      }

      @Override
      public <K, V> Materialized<K, V, KeyValueStore<Bytes, byte[]>> globalMaterialized(
          final String name) {
        return null;
      }

      @Override
      public <K, V> Materialized<K, V, WindowStore<Bytes, byte[]>> windowMaterialized(
          final String name,
          final long retentionMs, final long windowSize, final boolean retainDuplicates) {
        return null;
      }
    }), props);

    final TestInputTopic<String, String> inputTopic = testDriver.createInputTopic(Main.INPUT_TOPIC,
        new StringSerializer(), new StringSerializer());
    final TestInputTopic<String, String> deleteTopic = testDriver.createInputTopic(
        Main.DELETES_TOPIC, new StringSerializer(), new StringSerializer());
    final TestOutputTopic<String, String> outputTopic = testDriver.createOutputTopic(
        Main.OUTPUT_TOPIC, new StringDeserializer(), new StringDeserializer());

    inputTopic.pipeInput("a", "foo");
    inputTopic.pipeInput("b", "bar");
    inputTopic.pipeInput("c", "baz");
    inputTopic.pipeInput("a", "quk");
    deleteTopic.pipeInput("b", (String) null);
    inputTopic.pipeInput("b", "zab");

    final List<TestRecord<String, String>> testRecords = outputTopic.readRecordsToList();
    Assertions.assertEquals(testRecords.size(), 4);
    Assertions.assertEquals(testRecords.get(0).key(), "a");
    Assertions.assertEquals(testRecords.get(0).value(), "foo");
    Assertions.assertEquals(testRecords.get(1).key(), "b");
    Assertions.assertEquals(testRecords.get(1).value(), "bar");
    Assertions.assertEquals(testRecords.get(2).key(), "c");
    Assertions.assertEquals(testRecords.get(3).key(), "b");
    Assertions.assertEquals(testRecords.get(3).value(), "zab");
  }

}

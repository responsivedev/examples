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

import com.google.common.util.concurrent.RateLimiter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@SuppressWarnings("UnstableApiUsage")
public class Generator implements Runnable {

  private final Random random = new Random();
  private final KafkaProducer<String, String> producer;
  private final RateLimiter limiter;

  public Generator(final KafkaProducer<String, String> producer) {
    this.producer = producer;
    final double generatorEventsPerSecond = Double.parseDouble(
        Objects.requireNonNullElse(System.getenv("GENERATOR_EPS"), "500")
    );
    limiter = RateLimiter.create(generatorEventsPerSecond);
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      // one out of 10 events will be an expiry event
      final ProducerRecord<String, String> event;
      if (random.nextInt(10) == 0) {
        event = new ProducerRecord<>(Main.DELETES_TOPIC, key(), null);
      } else {
        event = new ProducerRecord<>(Main.INPUT_TOPIC, key(), value());
      }
      producer.send(event);
      limiter.acquire();
    }
  }

  private String key() {
    return String.valueOf(random.nextInt(1_000_000));
  }

  private String value() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

}

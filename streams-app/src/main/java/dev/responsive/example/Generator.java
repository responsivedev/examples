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
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnstableApiUsage")
public class Generator implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

  private static final String PROPERTIES_FILENAME = "/configs/app.properties";
  private static final double EVENT_RATE_DEFAULT = 100;

  private final KafkaProducer<String, String> producer;
  private final Dictionary dictionary;

  private RateLimiter limiter;
  private double currentEventRate = EVENT_RATE_DEFAULT;
  private double totalEventsProduced = 0;

  public Generator(final KafkaProducer<String, String> producer) {
    this.producer = producer;
    this.limiter = RateLimiter.create(currentEventRate);
    this.dictionary = new Dictionary();
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      final ProducerRecord<String, String> event;
      event = new ProducerRecord<>(Main.INPUT_TOPIC, null, dictionary.randomLine());
      producer.send(event);
      limiter.acquire();

      if (totalEventsProduced++ % currentEventRate != 0) {
        continue;
      }

      final double newEventRate = getEventRateOverride();
      if (newEventRate != currentEventRate && newEventRate > 0) {
        limiter = RateLimiter.create(newEventRate);
        currentEventRate = newEventRate;
        LOG.info("Using new custom rate: " + currentEventRate + " events/s");
      }
    }
  }

  private double getEventRateOverride() {
    try {
      final Properties cfg = ConfigUtils.loadConfigs(PROPERTIES_FILENAME);
      String eventRateString = cfg.getProperty("generator.rate", String.valueOf(currentEventRate));
      return Double.parseDouble(eventRateString);
    } catch (Exception e) {
      LOG.error("Failed to parse event rate: " + e.getMessage());
    }
    return currentEventRate;
  }
}

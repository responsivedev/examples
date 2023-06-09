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

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class Generator implements Runnable {

  private static final String[] PEOPLE = new String[]{
      "Alice",
      "Bob",
      "Carol",
      "Dave"
  };

  private static final String[] STATES = new String[] {
      "AZ",
      "CA",
      "CO",
      "NY",
      "MN",
      "WI"
  };

  private final Random random = new Random();
  private final KafkaProducer<String, String> producer;
  private final AtomicLong bidId = new AtomicLong(0);

  public Generator(final KafkaProducer<String, String> producer) {
    this.producer = producer;
  }

  @Override
  public void run() {
    // first populate all people
    for (int i = 0; i < 100; i++) {
      final ProducerRecord<String, String> person = person(i);
      producer.send(person);
    }

    // now start generating events where every 10th event is a person
    // update (e.g. moving state/changing name)
    long events = 0;
    while (!Thread.currentThread().isInterrupted()) {
      if (events % 10 == 0) {
        final ProducerRecord<String, String> person = person();
        producer.send(person);
      } else {
        final ProducerRecord<String, String> bid = bid();
        producer.send(bid);

      }

      events++;
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private ProducerRecord<String, String> person() {
    return person(random.nextInt(100));
  }

  private ProducerRecord<String, String> person(final int i) {
    // assume 100 distinct people
    final String personId = String.valueOf(i);
    return new ProducerRecord<>(
        "people",
        personId,
        String.join(",",
            personId,
            PEOPLE[random.nextInt(PEOPLE.length)],
            STATES[random.nextInt(STATES.length)]
        )
    );
  }

  private ProducerRecord<String, String> bid() {
    final String bidId = String.valueOf(this.bidId.getAndIncrement());
    return new ProducerRecord<>(
        "bids",
        bidId,
        String.join(",",
            bidId,
            String.valueOf(random.nextInt(100)), // amount
            String.valueOf(random.nextInt(100)) // person
        )
    );
  }
}

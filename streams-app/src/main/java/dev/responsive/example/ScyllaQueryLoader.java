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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public class ScyllaQueryLoader {

  public static void main(String[] args) {
    CqlSession session = CqlSession.builder()
        .addContactPoint(new InetSocketAddress(
            "node-0.aws-us-west-2.bc938b370dd7000bf3de.clusters.scylla.cloud",
            9042
        ))
        .withLocalDatacenter("AWS_US_WEST_2")
        .withAuthCredentials("scylla", "cMWHDmTJx7U3t5p")
        .withKeyspace("testing")
        .build();

    final PreparedStatement select = session.prepare(
        "SELECT * FROM small WHERE partitionKey = ? AND dataKey = ?"
    );

    final Random random = new Random();
    long start = System.nanoTime();
    int count = 0;
    int errors = 0;
    while (!Thread.interrupted()) {
      count++;

      final byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      bytes[31] = 1; // guarantees always a miss

      final ByteBuffer key = ByteBuffer.wrap(bytes);
      final BoundStatement bound = select.bind(count % 32768, key);
      try {
        for (Row row : session.execute(bound)) {
          // discard
        }
      } catch (final DriverTimeoutException e) {
        errors++;
      }

      if (count % 1000 == 0) {
        final long elapsed = Math.max(1, (System.nanoTime() - start) / 1000000);
        System.out.println(
            "Executed 1000 queries in " + elapsed
                + "ms with " + errors + " errors. (" + count / (elapsed / 1000) + " qps)");
        start = System.nanoTime();
        count = 0;
        errors = 0;
      }
    }
  }
}

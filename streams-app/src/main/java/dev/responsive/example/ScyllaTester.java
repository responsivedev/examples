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
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public class ScyllaTester {

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

    final PreparedStatement smallInsert = session.prepare(
        "INSERT INTO small (partitionKey, dataKey, value) VALUES (?, ?, ?)"
    );

    final Random random = new Random();
    for (int i = 0; i < 10; i++) {
      final byte[] bytes = new byte[32];
      random.nextBytes(bytes);

      final ByteBuffer key = ByteBuffer.wrap(bytes);
      final BoundStatement bound = smallInsert.bind(i % 32768, key, key);
      session.executeAsync(bound);
    }
  }

}

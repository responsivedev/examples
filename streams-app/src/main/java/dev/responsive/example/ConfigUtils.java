/*
 * Copyright 2024 Responsive Computing, Inc.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigUtils {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

  public static Properties loadConfigs(final String... paths) throws IOException {
    final Properties cfg = new Properties();
    for (var path : paths) {
      loadConfigIfPresent(cfg, path);
    }

    postProcessProperties(cfg);
    return cfg;
  }

  private static void loadConfigIfPresent(final Properties cfg, final String path)
      throws IOException {
    if (!Files.exists(Paths.get(path))) {
      return;
    }

    LOG.info("Loading config from " + path);
    try (InputStream inputStream = new FileInputStream(path)) {
      cfg.load(inputStream);
    }
  }

  private static void postProcessProperties(Properties cfg) {
    if (cfg.containsKey("kafka.api.key")) {
      LOG.info("Kafka broker credentials detected.");
      cfg.put("security.protocol", "SASL_SSL");
      cfg.put("sasl.mechanism", "PLAIN");
      cfg.put(
          "sasl.jaas.config",
          ("org.apache.kafka.common.security.plain.PlainLoginModule "
              + "required username='%s' "
              + "password='%s';").formatted(
              cfg.get("kafka.api.key"),
              cfg.get("kafka.api.secret")
          )
      );
    }

    if (cfg.containsKey("sr.api.key")) {
      LOG.info("ConfluentCloud Schema Repository detected.");
      cfg.put("basic.auth.credentials.source", "USER_INFO");
      cfg.put(
          "basic.auth.user.info",
          "%s:%s".formatted(cfg.get("sr.api.key"), cfg.get("sr.api.secret"))
      );
    }
  }
}

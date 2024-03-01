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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;

public class ConfigUtils {

  private ConfigUtils() { }

  public static final String KAFKA_API_KEY_ENV = "KAFKA_API_KEY";
  public static final String KAFKA_API_SECRET_ENV = "KAFKA_API_SECRET";

  public static final String SR_API_KEY_ENV = "SR_API_KEY";
  public static final String SR_API_SECRET_ENV = "SR_API_SECRET";

  public static Properties loadConfig(
      final String path
  ) throws IOException {
    return loadConfig(path, System.getenv());
  }

  public static Properties loadConfig(
      final String path,
      final Map<String, String> env
  ) throws IOException {
    final Properties config = loadInitialConfig(path);
    loadGenericEnvConfigs(config, env);
    loadCCloudBrokerConfigs(config, env);
    loadCCloudSRConfigs(config, env);
    return config;
  }

  private static Properties loadInitialConfig(final String configFile) throws IOException {
    if (!Files.exists(Paths.get(configFile))) {
      throw new IOException(configFile + " not found.");
    }

    final Properties cfg = new Properties();
    try (InputStream inputStream = new FileInputStream(configFile)) {
      cfg.load(inputStream);
    }

    return cfg;
  }

  public static void loadGenericEnvConfigs(
      final Properties config,
      final Map<String, String> env
  ) {
    loadGenericEnvConfigs(config::put, env);
  }

  private static void loadGenericEnvConfigs(
      final BiConsumer<String, String> setter,
      final Map<String, String> env
  ) {
    for (final Map.Entry<String, String> var : env.entrySet()) {
      if (var.getKey().startsWith("__EXT_")) {
        final String key = var.getKey()
            .substring("__EXT_".length())
            .toLowerCase(Locale.ROOT).replace('_', '.');
        setter.accept(key, var.getValue());
      }
    }
  }

  private static void loadCCloudBrokerConfigs(
      final Properties config,
      final Map<String, String> env
  ) {
    if (hasEnv(KAFKA_API_KEY_ENV, env)) {
      config.put("security.protocol", "SASL_SSL");
      config.put("sasl.mechanism", "PLAIN");
      config.put(
          "sasl.jaas.config",
          ("org.apache.kafka.common.security.plain.PlainLoginModule "
              + "required username='%s' "
              + "password='%s';").formatted(
              env.get(KAFKA_API_KEY_ENV),
              env.get(KAFKA_API_SECRET_ENV))
      );
    }
  }

  private static void loadCCloudSRConfigs(
      final Properties config,
      final Map<String, String> env
  ) {
    if (hasEnv(SR_API_KEY_ENV, env)) {
      config.put("basic.auth.credentials.source", "USER_INFO");
      config.put(
          "basic.auth.user.info",
          "%s:%s".formatted(env.get(SR_API_KEY_ENV), env.get(SR_API_SECRET_ENV))
      );
    }
  }

  private static boolean hasEnv(final String key, final Map<String, String> env) {
    final var val = env.get(key);
    return val != null && !val.isBlank();
  }

}

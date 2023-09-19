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

plugins {
    id("java")
    id("com.google.cloud.tools.jib") version "3.3.2"
}

group = "dev.responsive"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

jib {
    to {
        image = "public.ecr.aws/j8q9y0n6/responsivedev/example-app:rohan-policy-test-11"
    }
    container {
        entrypoint = listOf("/run.sh")
    }
    extraDirectories {
        permissions.set(
            mapOf("/run.sh" to "755")
        )
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.apache.kafka", "kafka-streams", "3.5.0")
    implementation("dev.responsive", "kafka-client", "0.9.0-SNAPSHOT")
    implementation("org.slf4j:slf4j-log4j12:2.0.5")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("io.opentelemetry.javaagent:opentelemetry-javaagent:1.25.0")
    implementation("com.google.guava:guava:31.1-jre")

    implementation("com.scylladb:java-driver-core:4.15.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.apache.kafka:kafka-streams-test-utils:3.4.0")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

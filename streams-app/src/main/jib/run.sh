#!/bin/sh

#
# Copyright 2023 Responsive Computing, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ ! -z "$CONTROLLER_ENDPOINT" ]; then
  EXPORTER_OPTS="-javaagent:/app/libs/opentelemetry-javaagent-1.25.0.jar
  -Dotel.metrics.exporter=otlp
  -Dotel.service.name=example
  -Dotel.jmx.config=/extra/otel-jmx.config
  -Dotel.exporter.otlp.metrics.headers=api-key=${API_KEY},secret=${API_SECRET}
  -Dotel.exporter.otlp.endpoint=${CONTROLLER_ENDPOINT}
  -Dotel.exporter.otlp.metrics.endpoint=${CONTROLLER_ENDPOINT}
  -Dotel.resource.attributes=responsiveApplicationId=responsive/example
  -Dotel.metric.export.interval=10000
  "
fi

java ${EXPORTER_OPTS} -cp @/app/jib-classpath-file dev.responsive.example.Main
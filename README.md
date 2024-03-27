# Responsive Quickstart Example

This repository contains an example Kafka Streams application that runs in a 
local KinD (Kubernetes in Docker) cluster as well as instructions for migrating
to Responsive.

This quickstart will:
- migrate an application from `KafkaStreams` to `ResponsiveKafkaStreams`
- deploy a Responsive autoscaling policy
- outline the key operational metrics on the Responsive observability dashboard

## Building

This quickstart example relies on the presence of a local docker image for
`responsive-example`. You can generate this image by running:

```bash
$ ./gradlew :streams-app:jibDockerBuild
```

## Deploying a KinD Cluster

The next step is to deploy a local k8s cluster with:
1. An example Kafka Streams application which computes word count
2. A Data Generator
3. A Kafka Broker
4. (Optional) A local MongoDB server

Deploy these by running:
```bash
$ bash ./kind/bootstrap.sh
```

Once this completes, you should be able to see the following pods deployed:
```
 kubectl get pods -n responsive
NAME                            READY   STATUS    RESTARTS      AGE
example-6c5dd46bd8-2f4r2        1/1     Running   1 (57s ago)   65s
generator-59975d568c-q9dkm      1/1     Running   1 (57s ago)   65s
kafka-broker-75b984c48d-cq928   2/2     Running   0             65s
mongo-855c74c766-cq2gj          1/1     Running   0             65s
```

## Setup Responsive Cloud

Login to https://cloud.responsive.dev and navigate to the Tutorial page (it
is the graduation hat on the left sidebar). 

![tutorial.png](tutorial.png)

Follow the instructions to create an environment named `example` and an 
application with the id `my-responsive-example`.

![create-env.png](create-env.png)

Create a new application:

![create-app.png](create-app.png)

You should also provision a storage cluster - this will take anywhere from
five to ten minutes.

## Migrate to Responsive

### Code Changes

You can either follow the steps in the tutorial UI or apply the patch below to
make the change.

```diff
diff --git a/streams-app/src/main/java/dev/responsive/example/Main.java b/streams-app/src/main/java/dev/responsive/example/Main.java
index 8f8f627..6668c41 100644
--- a/streams-app/src/main/java/dev/responsive/example/Main.java
+++ b/streams-app/src/main/java/dev/responsive/example/Main.java
@@ -16,6 +16,8 @@

 package dev.responsive.example;

+import dev.responsive.kafka.api.ResponsiveKafkaStreams;
+import dev.responsive.kafka.api.stores.ResponsiveStores;
 import java.util.Arrays;
 import java.util.HashMap;
 import java.util.List;
@@ -78,7 +80,7 @@ public class Main {
     props.forEach((k, v) -> config.put((String) k, v));

     final Topology topology = topology();
-    final KafkaStreams streams = new KafkaStreams(topology, props);
+    final KafkaStreams streams = new ResponsiveKafkaStreams(topology, props);

     Runtime.getRuntime().addShutdownHook(new Thread(() -> {
       streams.close();
@@ -119,7 +121,7 @@ public class Main {
         // Group the text words as message keys
         .groupBy((key, value) -> value)
         // Count the occurrences of each word (message key).
-        .count(Materialized.as(Stores.persistentKeyValueStore("word-counts")));
+        .count(Materialized.as(ResponsiveStores.keyValueStore("word-counts")));

     // Store the running counts as a changelog stream to the output topic.
     wordCounts
     
```

### API Keys

You will need API keys for metrics as well as data storage. You can create
both in the Responsive Cloud UI. For the metrics API keys, navigate to the
environment "Security" tab in the top navigation bar (or press the button
to create an API Key in the Tutorial after setting the environment in the
second step):

![create-metrics-key.png](create-metrics-key.png)

To create the storage API keys, first wait for the provisioning to complete
and then create your API keys and add your ip to the access list. This happens
within the scope of an application (so first select your newly crated application):

![configure-storage.png](configure-storage.png)

### Configurations

There are two parts to the configurations:

1. the application configurations
2. the secret configurations

You may choose to simply put all of you configurations in plaintext (since it is
a local cluster) by copy-pasting the configurations displayed in the UI and adding
the values for you secrets:

![configurations.png](configurations.png)

To take advantage of kubernetes secrets, you can put your secrets in
a file named `responsive-creds.properties` in the `./secrets`
folder of this repository. This file should contain the following
configuration properties:

```properties
# metrics secrets (environment API key)
responsive.metrics.api.key=
responsive.metrics.secret=

# storage secrets
responsive.client.id=
responsive.client.secret=
```

To apply these secrets, run:

```bash
$ bash ./scripts/update-app-secrets.sh
```

## Rebuild & Redeploy

Now that you have migrated your application to `ResponsiveKafkaStreams` and set
up all the configurations required, the next step is to rebuild and redeploy:

```bash
$ ./gradlew :streams-app:jibDockerBuild
$ bash ./kind/bootstrap.sh
```

When the application restarts you should start to see metrics on the dashboard.

## Troubleshooting

**Getting `Invalid API Key or Secret` in the Logs**.

Make sure that you have properly configured `secrets/responsive-metrics-creds.properties`. An
example file looks like this:
```properties
# Responsive API Credentials | responsive-kind

responsive.metrics.api.key=ABCDEFGHIJKLMNOP
responsive.metrics.secret=ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFG1234567890=
```
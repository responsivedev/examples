# Responsive Examples

This repository contains an example Kafka Streams application that is powered
by the Responsive Platform for Kafka Streams. 

This repo allows you to get up and running using a local Kubernetes In Docker
(kind) cluster.

## Creating your cluster

This Example application assumes you have access to Responsive Cloud. If you do not
yet have an account and cannot create one, reach out to `info@responsive.dev` to get
started.

1. Log in to [Responsive Cloud](https://cloud.responsive.dev). 
2. Take note of your Organization slug. You may change it to a meaningful value at this point, but
   you should not change it after you have deployed any application.
3. After logging in, create a new environment (you can name this whatever you want) and take note of
   the environment slug.
4. In this new environment, create an application with the name `My Responsive Example` and id
   `my-responsive-example`.
5. Navigate back to the new environment and open the "Security" tab. Create a new API key and take
   note of the key/secret values.

### KinD

These steps will instruct you on how to set up a local kubernetes cluster using KinD. 
It will contain the following deployments:
- The example `Responsive` application
- A data generator for the example application that can be independently scaled
- A Kafka Broker
- A MongoDB server

1. Set up the secrets. The bootstrapping will use secrets that are expected to be located inside
   the `secrets` directory within this repository's working directory. You must have at least a
   file named `responsive-metrics-creds.properties` with values for `responsive.metrics.api.key`
   and `responsive.metrics.secret`. (Note that these are not the default names for the api key and
   secret that are downloaded from the UI -- we're working on making that clearer!)

```
responsive-metrics-creds.properties (Responsive Metrics Key)
```

2. Build the `responsive-example` docker image so that you can load it locally:

```
$ ./gradlew :streams-app:jibDockerBuild
```

3. Navigate to the "Setup" tab in the Cloud UI for your newly created application and
   take note of the configurations. Populate the `responsive.tenant.id` and 
   `responsive.controller.endpoint` values in `kind/app.properties`

4. You are now ready to create and bootstrap the KinD cluster:
```
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

If you navigate back to the cloud UI, you should now see metrics flowing through into the dashboard.
Note that storage metrics will not be available for locally-provisioned mongoDB clusters.

#### Configurations for Confluent Cloud

If you want to use credentials with Confluent Cloud, you can change your `kind/app.properties`
file to point to the right bootstrap server, and add the following secret file containing
`kafka.api.key` and `kafka.api.secret`: `./secrets/kafka-creds.properties`

If you are using the Confluent Cloud Schema Registry, you can define `sr.api.key` and
`sr.api.secret` in `./secrets/ccloud-sr-creds.properties`.

You can re-create your cluster with a new app at any time, which should only take a couple of
minutes. This will update all of the secrets and docker images with any local changes:

```
$ kind delete cluster -n kind-responsive
$ bash ./kind/bootstrap.sh
```

### Pulumi

If you are using Pulumi to create your cluster, you can run `pulumi up` from within the 
`pulumi` directory.

## Running the Operator

Follow the steps in the "Setup" pane within the environment you created to deploy the operator and
set up an autoscaling policy.

## Troubleshooting

**Getting `Invalid API Key or Secret` in the Logs**. 

Make sure that you have properly configured `secrets/responsive-metrics-creds.properties`. An
example file looks like this:
```properties
# Responsive API Credentials | responsive-kind

responsive.metrics.api.key=ABCDEFGHIJKLMNOP
responsive.metrics.secret=ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFG1234567890=
```
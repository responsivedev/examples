# Responsive Examples

This repository contains an example Kafka Streams application that is powered
by the Responsive Platform for Kafka Streams. There are two modules: the
`streams-app` module, which contains the application logic (described below),
and the `pulumi` module, which contains the infrastructure-as-code definition
required for deploying the streams application into an AWS EKS cluster with
the Responsive operator.

## Building

There are a few phases for building this project - when making changes to the
`streams-app` module, use `./gradlew build` to ensure that it can build locally
and then use `./gradlew :streams-app:jib` to build the docker image for the
streams application (note that this requires AWS ECR credentials to push to
the public ECR repository).

To deploy the streams app into an EKS cluster, `cd` into the `pulumi` dir. First,
you need to set the secrets for the Kafka Cluster credentials and ScyllaDB cluster 
credentials. 

**NOTE:** at the moment, the kafka cluster and scyllaDB cluster/user are hard-coded.
We should update this to allow public usage of this repository.

```bash
pulumi config set --secret kafkaSaslJaasConfig "org.apache.kafka.common.security.plain.PlainLoginModule required username='<USERNAME>' password='<PASSWORD>';" 
pulumi config set --secret responsiveClientSecret <responsive_secret>
```

Then run `pulumi up` (ensure you have `pulumi` installed).

## Running the Operator

Once you've built and deployed the streams app on an EKS cluster, you need
to apply the Responsive Operator Helm chart. First, you'll need to create a
k8s secret containing the `responsive.platform.api.key` and `responsive.platform.api.secret`:
```bash
$ export API_KEY=<insert your api key ID here>
$ export SECRET=<insert your api key secret here>
$ cat <<EOF >> secret.properties
responsive.platform.api.key=${API_KEY}
responsive.platform.api.secret=${API_KEY}
EOF
$ kubectl create secret generic --namespace responsive ctl-secret --from-file=secret.properties
```

Then apply the Helm chart to your k8s cluster:

```bash
helm install responsive-operator oci://public.ecr.aws/j8q9y0n6/responsiveinc/charts/responsive-operator \
    --version <latest_version> \
    --set controllerEndpoint=dns:///demo.ctl.us-west-2.aws.cloud.responsive.dev \
    --namespace responsive
```

You can confirm that everything is deployed by looking at the "Outputs" from
the `pulumi up` command and set the kubectl config:
```bash
aws eks update-kubeconfig ... 
```

Then look to see which pods are running using `kubectl get pods -A`
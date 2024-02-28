import json

import pulumi
import pulumi_aws as aws
import pulumi_awsx as awsx
import pulumi_eks as eks
import pulumi_kubernetes
import pulumi_kubernetes as k8s


config = pulumi.Config()

eks_vpc = awsx.ec2.Vpc(
  "responsive-example-eks-vpc",
  enable_dns_hostnames=True,
  number_of_availability_zones=2,
  cidr_block="10.0.0.0/16",
  nat_gateways=awsx.ec2.NatGatewayConfigurationArgs(
    strategy=awsx.ec2.NatGatewayStrategy.SINGLE),
  subnet_specs=[
    awsx.ec2.SubnetSpecArgs(
      type=awsx.ec2.SubnetType.PUBLIC,
      cidr_mask=19,
      name="public_frontend"
    ),
    awsx.ec2.SubnetSpecArgs(
      type=awsx.ec2.SubnetType.PRIVATE,
      cidr_mask=18,
      name="backend"
    )
  ]
)

access_role = aws.iam.Role(
  "responsive-example-eks-cluster-admin",
  assume_role_policy=json.dumps({
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "",
        "Effect": "Allow",
        "Principal": {
          "AWS": "arn:aws:iam::083511421557:root"
        },
        "Action": "sts:AssumeRole"
      }
    ]
  }),
  tags={
    "clusterAccess": "responsive-example-eks-cluster-admin-usr",
  }
)

role = aws.iam.Role(
  "eks-node-access-role",
  assume_role_policy=json.dumps({
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Principal": {
          "Service": "ec2.amazonaws.com"
        },
        "Action": "sts:AssumeRole"
      }
    ]
  })
)

attachments = [
  ("cr-readonly", "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"),
  ("cni", "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"),
  ("worker", "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy")
]

for name, arn in attachments:
  aws.iam.RolePolicyAttachment(
    f"eks-{name}-attachment",
    role=role.name,
    policy_arn=arn
  )

eks_cluster = eks.Cluster(
  "responsive-example-eks-cluster",
  # Put the cluster in the new VPC created earlier
  vpc_id=eks_vpc.vpc_id,
  # Public subnets will be used for load balancers
  public_subnet_ids=eks_vpc.public_subnet_ids,
  # Private subnets will be used for cluster nodes
  private_subnet_ids=eks_vpc.private_subnet_ids,
  # Change configuration values to change any of the following settings
  instance_type="m5.large",
  desired_capacity=2,
  min_size=1,
  max_size=3,
  # Do not give worker nodes a public IP address
  node_associate_public_ip_address=False,
  create_oidc_provider=True,
  endpoint_private_access=True,
  endpoint_public_access=True,
  fargate=False,
  kubernetes_service_ip_address_range='172.20.0.0/16',
  instance_role=role,
  role_mappings=[
    eks.RoleMappingArgs(
      groups=["responsive:admin-grp"],
      username="responsive:admin-usr",
      role_arn=access_role.arn,
    )
  ],
)

eks_provider = k8s.Provider("eks-provider", kubeconfig=eks_cluster.kubeconfig_json)

k8s_admin_role = k8s.rbac.v1.ClusterRole(
  "cluster-admin-k8s-role",
  metadata={"name": "clusterAdminRole"},
  rules=[{
    "apiGroups": ["*"],
    "resources": ["*"],
    "verbs": ["*"],
  }],
  opts=pulumi.ResourceOptions(provider=eks_provider)
)

k8s_admin_role_binding = k8s.rbac.v1.ClusterRoleBinding(
  "cluster-admin-k8s-role-binding",
  metadata={"name": "clusterAdminRoleBinding"},
  subjects=[{"kind": "User", "name": "responsive:admin-usr"}],
  role_ref={
    "kind": "ClusterRole",
    "name": "clusterAdminRole",
    "apiGroup": "rbac.authorization.k8s.io",
  },
  opts=pulumi.ResourceOptions(provider=eks_provider),
)

# Deploy the services running on k8s

namespace = pulumi_kubernetes.core.v1.Namespace(
  "responsiveNamespace",
  metadata={"name": "responsive"},
  opts=pulumi.ResourceOptions(provider=eks_provider),
)

app_deployment = k8s.apps.v1.Deployment(
  "ExampleDeployment",
  api_version="apps/v1",
  kind="Deployment",
  metadata=k8s.meta.v1.ObjectMetaArgs(
    name="example",
    labels={"app": "example"},
    namespace="responsive"
  ),
  spec=k8s.apps.v1.DeploymentSpecArgs(
    replicas=1,
    selector=k8s.meta.v1.LabelSelectorArgs(
      match_labels={
        "app": "example",
      },
    ),
    template=k8s.core.v1.PodTemplateSpecArgs(
      metadata=k8s.meta.v1.ObjectMetaArgs(
        labels={
          "app": "example",
        },
      ),
      spec=k8s.core.v1.PodSpecArgs(
        containers=[k8s.core.v1.ContainerArgs(
          name="example" + "-container",
          image="public.ecr.aws/x3k6i9w2/responsivedev/example-app",
          image_pull_policy="Always",
          env=[
            k8s.core.v1.EnvVarArgs(
              name="POD_IP",
              value_from=k8s.core.v1.EnvVarSourceArgs(
                field_ref=k8s.core.v1.ObjectFieldSelectorArgs(field_path="status.podIP"))),
          ],
        )],
      ),
    ),
  ),
  opts=pulumi.ResourceOptions(provider=eks_provider)
)

gen_deployment = k8s.apps.v1.Deployment(
  "GeneratorDeployment",
  api_version="apps/v1",
  kind="Deployment",
  metadata=k8s.meta.v1.ObjectMetaArgs(
    name="generator",
    labels={"app": "generator"},
    namespace="responsive"
  ),
  spec=k8s.apps.v1.DeploymentSpecArgs(
    replicas=1,
    selector=k8s.meta.v1.LabelSelectorArgs(
      match_labels={
        "app": "generator",
      },
    ),
    template=k8s.core.v1.PodTemplateSpecArgs(
      metadata=k8s.meta.v1.ObjectMetaArgs(
        labels={
          "app": "generator",
        },
      ),
      spec=k8s.core.v1.PodSpecArgs(
        containers=[k8s.core.v1.ContainerArgs(
          name="generator" + "-container",
          image="public.ecr.aws/x3k6i9w2/responsivedev/example-app",
          image_pull_policy="Always",
          env=[
            k8s.core.v1.EnvVarArgs(
              name="ARGS",
              value="--generator"
          )],
        )],
      ),
    ),
  ),
  opts=pulumi.ResourceOptions(provider=eks_provider)
)

with open('/Users/agavra/dev/responsive-example/streams-app/src/main/resources/bootstrap.properties', 'r') as file:
    file_content = file.read()

config_map = k8s.core.v1.ConfigMap(
    'bootstrap-configmap',
    metadata=k8s.meta.v1.ObjectMetaArgs(
        name='bootstrap-configmap',
        namespace='responsive'
    ),
    data={
        'bootstrap.properties': file_content
    }
)

bootstrap_deployment = k8s.apps.v1.Deployment(
  "BootstrapDeployment",
  api_version="apps/v1",
  kind="Deployment",
  metadata=k8s.meta.v1.ObjectMetaArgs(
    name="bootstrap",
    labels={"app": "bootstrap"},
    namespace="responsive"
  ),
  spec=k8s.apps.v1.DeploymentSpecArgs(
    replicas=1,
    selector=k8s.meta.v1.LabelSelectorArgs(
      match_labels={
        "app": "bootstrap",
      },
    ),
    template=k8s.core.v1.PodTemplateSpecArgs(
      metadata=k8s.meta.v1.ObjectMetaArgs(
        labels={
          "app": "bootstrap",
        },
      ),
      spec=k8s.core.v1.PodSpecArgs(
        containers=[k8s.core.v1.ContainerArgs(
          name="bootstrap" + "-container",
          image="public.ecr.aws/j8q9y0n6/responsiveinc/kafka-client-bootstrap:0.18.0",
          image_pull_policy="Always",
          env=[
            k8s.core.v1.EnvVarArgs(
              name="BOOTSTRAP_ARGS",
              value="-propertiesFile /etc/config/bootstrap.properties -name COUNT -changelogTopic responsive-example-COUNT-changelog"
           )],
           volume_mounts=[
               k8s.core.v1.VolumeMountArgs(
                   name='config-volume',  # Must match the volume name below
                   mount_path='/etc/config',  # Path in the container to mount the volume
               )
           ]
        )],
        volumes=[
            k8s.core.v1.VolumeArgs(
                name='config-volume',  # Must be referenced in the container's volumeMounts
                config_map=k8s.core.v1.ConfigMapVolumeSourceArgs(
                    name=config_map.metadata.name
                ),
            )
        ]
      ),
    ),
  ),
  opts=pulumi.ResourceOptions(provider=eks_provider)
)

# Export values to use elsewhere
pulumi.export("kubeconfig", eks_cluster.kubeconfig)
pulumi.export("vpcId", eks_vpc.vpc_id)
pulumi.export("eksName", eks_cluster.eks_cluster.name)
pulumi.export("clusterAdminRoleArn", access_role.arn)
cmd = pulumi.Output.all(eks_cluster.eks_cluster.name, access_role.arn).apply(
  lambda l: f"aws eks update-kubeconfig --name {l[0]} --role-arn {l[1]}"
)
pulumi.export("updateKubeCmd", cmd)
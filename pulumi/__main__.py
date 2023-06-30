import json

import pulumi
import pulumi_aws as aws
import pulumi_awsx as awsx
import pulumi_eks as eks
import pulumi_kubernetes
import pulumi_kubernetes as k8s

POLICY = """
apiVersion: "application.responsive.dev/v1"
kind: "ResponsivePolicy"
metadata:
  name: example
  namespace: responsive
spec:
  applicationNamespace: responsive
  applicationName: example
  status: POLICY_STATUS_MANAGED
  policyType: DEMO
  demoPolicy:
    maxReplicas: 2
"""

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
          "AWS": "arn:aws:iam::292505934682:root"
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
  instance_type="t3.medium",
  desired_capacity=2,
  min_size=2,
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

k8s.yaml.ConfigGroup(
  "example-policy",
  yaml=[POLICY],
  opts=pulumi.ResourceOptions(provider=eks_provider)
)

deployment = k8s.apps.v1.Deployment(
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
          image="public.ecr.aws/j8q9y0n6/responsivedev/example-app",
          image_pull_policy="Always",
          env=[
            k8s.core.v1.EnvVarArgs(
              name="SASL_JAAS_CONFIG",
              value=config.get_secret("kafkaSaslJaasConfig")
            ),
            k8s.core.v1.EnvVarArgs(
              name="RESPONSIVE_CLIENT_ID",
              value=config.get("responsiveClientId")
            ),
            k8s.core.v1.EnvVarArgs(
              name="RESPONSIVE_CLIENT_SECRET",
              value=config.get_secret("responsiveClientSecret")
            ),
            k8s.core.v1.EnvVarArgs(
              name="API_KEY",
              value=config.get_secret("apiKey")
            ),
            k8s.core.v1.EnvVarArgs(
              name="API_SECRET",
              value=config.get_secret("apiSecret")
            ),
            k8s.core.v1.EnvVarArgs(
              name="GENERATOR_EPS",
              value="500"
            ),
            k8s.core.v1.EnvVarArgs(
              name="STREAMS_EPS",
              value="500"
            ),
            k8s.core.v1.EnvVarArgs(
              name="CONTROLLER_ENDPOINT",
              value="https://example.ctl.us-west-2.aws.cloud.responsive.dev"),
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

# Export values to use elsewhere
pulumi.export("kubeconfig", eks_cluster.kubeconfig)
pulumi.export("vpcId", eks_vpc.vpc_id)
pulumi.export("eksName", eks_cluster.eks_cluster.name)
pulumi.export("clusterAdminRoleArn", access_role.arn)
cmd = pulumi.Output.all(eks_cluster.eks_cluster.name, access_role.arn).apply(
  lambda l: f"aws eks update-kubeconfig --name {l[0]} --role-arn {l[1]}"
)
pulumi.export("updateKubeCmd", cmd)
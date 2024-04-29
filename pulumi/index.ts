import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";
import * as awsx from "@pulumi/awsx";
import * as eks from "@pulumi/eks";
import * as k8s from "@pulumi/kubernetes";

const config = new pulumi.Config()

const eksVpc = new awsx.ec2.Vpc(
    "responsive-example-eks-vpc",
    {
        enableDnsHostnames: true,
        numberOfAvailabilityZones: 2,
        cidrBlock: "10.0.0.0/16",
        natGateways: {strategy: "Single"},
        subnetSpecs: [
            {type: "Public", cidrMask: 19, name: "public_frontend"},
            {type: "Private", cidrMask: 18, name: "backend"}
        ]
    }
)

const accessRole = new aws.iam.Role(
    "responsive-example-eks-cluster-admin",
    {
        assumeRolePolicy: JSON.stringify({
            Version: "2012-10-17",
            Statement: [
                {
                    Sid: "",
                    Effect: "Allow",
                    Principal: {AWS: "arn:aws:iam::083511421557:root"},
                    Action: "sts:AssumeRole"
                }
            ]
        }),
        tags: {clusterAccess: "responsive-example-eks-cluster-admin-usr"}
    }
)

const role = new aws.iam.Role(
    "eks-node-access-role",
    {
        assumeRolePolicy: JSON.stringify({
            Version: "2012-10-17",
            Statement: [
                {
                    Effect: "Allow",
                    Principal: {Service: "ec2.amazonaws.com"},
                    Action: "sts:AssumeRole"
                }
            ]
        }),
    }
)

const attachments : {name: string, arn: string}[] = [
    {name: "cr-readonly", arn: "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"},
    {name: "cni", arn: "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"},
    {name: "worker", arn: "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"}
]

for (let {name, arn} of attachments) {
    new aws.iam.RolePolicyAttachment(
        `eks-${name}-attachment`,
        {role: role.name, policyArn: arn}
    )
}

const eksCluster = new eks.Cluster(
    "responsive-example-eks-cluster",
    {
        vpcId: eksVpc.vpcId,
        publicSubnetIds: eksVpc.publicSubnetIds,
        privateSubnetIds: eksVpc.privateSubnetIds,
        instanceType: 'm5.large',
        desiredCapacity: 2,
        minSize: 1,
        maxSize: 3,

        nodeAssociatePublicIpAddress: false,
        createOidcProvider: true,
        endpointPrivateAccess: true,
        endpointPublicAccess: true,
        fargate: false,

        kubernetesServiceIpAddressRange: '172.20.0.0/16',
        instanceRole: role,
        roleMappings: [{
            groups: ["responsive:admin-grp"],
            username: "responsive:admin-usr",
            roleArn: accessRole.arn
        }]
    }
)

const provider = new k8s.Provider("eks-provider", {kubeconfig: eksCluster.kubeconfigJson})
const k8sAdminRole = new k8s.rbac.v1.ClusterRole(
    "cluster-admin-k8s-role",
    {
        metadata: {name: "clusterAdminRole"},
        rules: [{
            apiGroups: ['*'],
            resources: ['*'],
            verbs: ['*']
        }],
    },
    {provider}
)
const k8sAdminRoleBinding = new k8s.rbac.v1.ClusterRoleBinding(
    "cluster-admin-k8s-role-binding",
    {
        metadata: {name: "clusterAdminRoleBinding"},
        subjects:[{kind: "User", name: "responsive:admin-usr"}],
        roleRef: {
            kind: "ClusterRole",
            name: "clusterAdminRole",
            apiGroup: "rbac.authorization.k8s.io"
        }
    },
    {provider}
)

// --------------------- setup the services --------------------------

const namespace = new k8s.core.v1.Namespace(
    "responsiveNamespace",
    {metadata: {name: "responsive"}},
    {provider}
)

const encode = (str: string):string => Buffer.from(str).toString('base64');
const appSecrets = new k8s.core.v1.Secret(
    "app-secrets",
    {
        metadata: {name: 'app-secrets', namespace: namespace.id},
        data: {
            "__EXT_RESPONSIVE_MONGO_USERNAME": config.requireSecret('responsive_mongo_username').apply(encode),
            "__EXT_RESPONSIVE_MONGO_PASSWORD": config.requireSecret('responsive_mongo_password').apply(encode),
            "__EXT_RESPONSIVE_PLATFORM_API_KEY": config.requireSecret('responsive_platform_key').apply(encode),
            "__EXT_RESPONSIVE_PLATFORM_SECRET": config.requireSecret('responsive_platform_secret').apply(encode),
            "__EXT_RESPONSIVE_MONGO_ENDPOINT": encode(config.require('responsive_mongo_hostname')),
            "KAFKA_API_KEY": config.requireSecret('kafka_api_key').apply(encode),
            "KAFKA_API_SECRET": config.requireSecret('kafka_api_secret').apply(encode),
        },
        type: "Opaque"
    },
    {provider}
)

// --------------------- deploy the services -------------------------

const appLabels = {app: 'example'}
const appDeployment = new k8s.apps.v1.Deployment(
    "ExampleDeployment",
    {
        apiVersion: 'apps/v1',
        kind: "Deployment",
        metadata: {name: 'example', labels: appLabels, namespace: namespace.id},
        spec: {
            replicas: 1,
            selector: {matchLabels: appLabels},
            template: {
                metadata: {labels: appLabels},
                spec: {
                    containers:[{
                        name: 'example-container',
                        image: 'public.ecr.aws/x3k6i9w2/responsivedev/example-app',
                        imagePullPolicy: "Always",
                        env:[
                            {name: "POD_IP", valueFrom: {fieldRef: {fieldPath: "status.podIP"}}}
                        ],
                        envFrom: [{
                            secretRef: {name: appSecrets.metadata.name}
                        }]
                    }]
                }
            }
        }
    },
    {provider}
)

const genLabels = {app: 'generator'}
const genDeployment = new k8s.apps.v1.Deployment(
    "GeneratorDeployment",
    {
        apiVersion: 'apps/v1',
        kind: "Deployment",
        metadata: {name: 'generator', labels: genLabels, namespace: namespace.id},
        spec: {
            replicas: 1,
            selector: {matchLabels: genLabels},
            template: {
                metadata: {labels: genLabels},
                spec: {
                    terminationGracePeriodSeconds: 10,
                    containers:[{
                        name: 'generator-container',
                        image: 'public.ecr.aws/x3k6i9w2/responsivedev/example-app',
                        imagePullPolicy: "Always",
                        env:[
                            {name: "ARGS", value: "--generator"}
                        ],
                        envFrom: [{
                            secretRef: {name: appSecrets.metadata.name}
                        }]
                    }]
                }
            }
        }
    },
    {provider}
)

export const kubeconfig = eksCluster.kubeconfig;
export const vpcId = eksVpc.vpcId;
export const vpcPublicIps = eksVpc.natGateways.apply(nat => nat.map(details => details.publicIp))
export const eksName = eksCluster.eksCluster.name;
export const clusterAdminRoleArn = accessRole.arn;
export const updateKubeCmd = pulumi.all([eksCluster.eksCluster.name, accessRole.arn]).apply(
    vals => `aws eks update-kubeconfig --name ${vals[0]} --role-arn ${vals[1]}`
)
#! /bin/bash

set -eo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)
SECRETS_ROOT="$REPO_ROOT/secrets"
APP_IMAGE="responsive/example-app"

if ! command -v kind &> /dev/null; then
  echo "Kubernetes In Docker (kind) must be installed on your system."
  exit 1
fi

function require_secret () {
  if [ ! -f "$SECRETS_ROOT/$1" ]; then
    echo "---"
    if [ ! -z "$2" ]; then
      echo "ERROR: Required Secret: $2"
    fi
    echo "ERROR: Secret expected in $SECRETS_ROOT/$1"
    echo "This secret will be stored on your KinD cluster and loaded"
    echo "in at runtime using environment variables."
    exit 1
  fi
}

function require_app_image () {
  if [ -z "$(docker images -q $APP_IMAGE 2> /dev/null)" ]; then
    echo "The example app must be built on your system."
    echo "You can built it using gradle:"
    echo "./gradlew :streams-app:jibDockerBuild"
    exit 1
  fi
}

require_app_image
require_secret "responsive-metrics-creds.properties" "Responsive Metrics Key"

if [ ! -f "$REPO_ROOT/kind/app.properties" ]; then
  cp "$REPO_ROOT/kind/default.properties" "$REPO_ROOT/kind/app.properties"
fi

if grep -q "required field" "$REPO_ROOT/kind/app.properties"; then
  echo "The configuration in $REPO_ROOT/kind/app.properties must be filled out"
  exit 1
fi

PREVIOUS_CONTEXT=$(kubectl config current-context 2> /dev/null || true)
CLUSTER_NAME=${CLUSTER_NAME:-kind-responsive}
kind create cluster -n $CLUSTER_NAME

if [ ! -z "$PREVIOUS_CONTEXT" ]; then
  echo ""
  echo "New kubectl context created. To use the previous context, run the following command:"
  echo "$ kubectl config use-context ${PREVIOUS_CONTEXT}"
fi

echo ""
echo "To delete this new KinD cluster, run:"
echo "$ kind delete cluster -n ${CLUSTER_NAME}"

echo ""
echo "* Loading local streams app image onto KinD cluster *"
kind -n $CLUSTER_NAME load docker-image $APP_IMAGE

echo ""
echo "* Creating Responsive Resources on your new KinD cluster *"
kubectl create namespace responsive
kubectl config set-context --current --namespace responsive
kubectl create configmap app-config --from-file "$REPO_ROOT/kind/app.properties"

echo ""
echo "* Creating Credential Secrets *"
kubectl create secret generic app-secrets --from-file "$SECRETS_ROOT"

kubectl apply -f "$REPO_ROOT/kind/resources.yaml"
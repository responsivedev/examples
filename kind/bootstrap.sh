#! /bin/bash

set -eo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)
SECRETS_ROOT="$REPO_ROOT/.secrets"

if ! command -v kind &> /dev/null; then
  echo "Kubernetes In Docker (kind) must be installed on your system."
  exit 1
fi

function require_secret () {
  if [ ! -f "$SECRETS_ROOT/$1" ]; then
    echo "---"
    if [ ! -z "$2" ]; then
      echo "$2"
    fi
    echo "Secret expected in $SECRETS_ROOT/$1"
    exit 0
  fi
}

require_secret "KAFKA_API_KEY" "Confluent Cloud API Key"
require_secret "KAFKA_API_SECRET" "Confluent Cloud API Secret"
require_secret "__EXT_RESPONSIVE_CLIENT_ID" "Responsive Storage Client ID"
require_secret "__EXT_RESPONSIVE_CLIENT_SECRET" "Responsive Storage Secret"
require_secret "__EXT_RESPONSIVE_METRICS_API_KEY" "Responsive Metrics Key"
require_secret "__EXT_RESPONSIVE_METRICS_SECRET" "Responsive Metrics Secret"
require_secret "__EXT_RESPONSIVE_STORAGE_HOSTNAME" "KafkaStreams StateStore Hostname"

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
  echo "---"
  echo "New kubectl context created. To use the previous context, run the following command:"
  echo "$ kubectl config use-context ${PREVIOUS_CONTEXT}"
fi

echo "---"
echo "To delete this new KinD cluster, run:"
echo "$ kind delete cluster -n ${CLUSTER_NAME}"

kubectl create namespace responsive
kubectl config set-context --current --namespace responsive

kubectl create secret generic app-secrets --from-file "$REPO_ROOT/.secrets"
kubectl create configmap app-config --from-file "$REPO_ROOT/kind/app.properties"

kubectl apply -f "$REPO_ROOT/kind/resources.yaml"
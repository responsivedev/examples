#! /bin/bash

set -eo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)

kubectl create configmap app-config -o yaml --dry-run=client \
  --from-file "$REPO_ROOT/kind/app.properties" | kubectl apply -f -

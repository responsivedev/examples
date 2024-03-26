#! /bin/bash

set -eo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)
SECRETS_ROOT="$REPO_ROOT/secrets"

kubectl create secret generic app-secrets -o yaml --dry-run=client \
  --from-file "$SECRETS_ROOT" | kubectl apply -f -

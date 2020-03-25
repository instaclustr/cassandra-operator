#!/usr/bin/env bash

set -uxo pipefail

top_dir="$(git rev-parse --show-toplevel)"
cd $top_dir

echo "Validate repository state"

function check-bundle {
    make bundle
    git diff --quiet
    if [[ $? != 0 ]]; then
        echo "Bundles were not properly built, make sure to run 'make bundle'"
        exit 1
    fi
}

function update-go-modules {
    go get golang.org/x/tools/cmd/goimports && go mod download
}

function check-go-formatting {
    files=$(goimports -l .)
    if [ -n "$files" ]; then
        echo "The following file(s) are not properly formatted or their imports are misaligned. Run go fmt/go imports on your code:"
        echo "$files"
        exit 1
    fi
}

function install-operator-sdk {
    RELEASE_VERSION=v0.16.0
    curl -LO https://github.com/operator-framework/operator-sdk/releases/download/${RELEASE_VERSION}/operator-sdk-${RELEASE_VERSION}-x86_64-linux-gnu
    curl -LO https://github.com/operator-framework/operator-sdk/releases/download/${RELEASE_VERSION}/operator-sdk-${RELEASE_VERSION}-x86_64-linux-gnu.asc
    chmod +x operator-sdk-${RELEASE_VERSION}-x86_64-linux-gnu && mv operator-sdk-${RELEASE_VERSION}-x86_64-linux-gnu operator-sdk
}

function check-open-api {
    ./operator-sdk generate crds
    git diff --quiet --exit-code deploy/crds/cassandraoperator_*_cassandra*_crd.yaml
    if [[ $? != 0 ]]; then
        echo "API files were not regenerated with latest changes, make sure to run 'operator-sdk generate crds'"
        exit 1
    fi
}

function check-helm {
    ./buildenv/prepare-helm

    git diff --quiet --exit-code deploy
    if [[ $? != 0 ]]; then
        echo "Helm files in 'helm' directory where not updated, make sure you have run 'prepare-helm' script and committed all changes"
        exit 1
    fi
}

function check-k8s {
    ./operator-sdk generate k8s
    git diff --quiet --exit-code pkg/apis
    if [[ $? != 0 ]]; then
        echo "k8s files were not regenerated with latest changes, make sure to run 'operator-sdk generate k8s'"
        exit 1
    fi
}

echo "Check bundles"
check-bundle

echo "Update go modules"
update-go-modules

echo "Check go formatting"
check-go-formatting

echo "Install operator-sdk"
install-operator-sdk

echo "Check OpenAPI"
check-open-api

echo "Check k8s"
check-k8s

echo "Check helm"
check-helm

echo "All clean, you may continue"

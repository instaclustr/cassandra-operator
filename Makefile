
.PHONY: all
all: java docker

.PHONY: java
java:
	cd java && mvn -q install

# Obtain operator version from Git
git_revision = $(shell git describe --tags --always --dirty)

# Build cassandra-operator binary
.PHONY: operator
operator:
	cd cmd/manager && go build -ldflags "-X main.version=$(git_revision)"

.PHONY: helm
helm:
	cp deploy/crds/*_crd.yaml helm/cassandra-operator/crds \
	    && cp deploy/{configmap.yaml,role.yaml,role_binding.yaml} helm/cassandra-operator/templates \
	    && cp deploy/cassandra/{psp.yaml,psp_performance.yaml} helm/cassandra/templates \
	    && ./buildenv/create-helm-repo


# Build Docker images
.PHONY: docker
docker: java
	$(MAKE) -C $@

# Render YAML bundles from official manifests
.PHONY: bundle
bundle:
	buildenv/bundle


.DEFAULT_GOAL := all

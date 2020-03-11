
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
	./buildenv/prepare-helm


# Build Docker images
.PHONY: docker
docker: java
	$(MAKE) -C $@

# Render YAML bundles from official manifests
.PHONY: bundle
bundle:
	buildenv/bundle


.DEFAULT_GOAL := all

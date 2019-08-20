
# Figure out the path to the root of the repository
rootdir = $(shell git rev-parse --show-toplevel)
# Obtain operator version from Git
git_revision = $(shell git describe --tags --always --dirty)

.PHONY: cassandra-operator
cassandra-operator:
	cd $(rootdir) && docker build \
		--build-arg version=$(git_revision) \
		-t $(DOCKER_REGISTRY)cassandra-operator \
		-f docker/cassandra-operator/Dockerfile \
		.

.PHONY: clean
clean:
	# no op

.DEFAULT_GOAL := cassandra-operator

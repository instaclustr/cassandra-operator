
rootdir = $(shell git rev-parse --show-toplevel)
git_revision = $(shell git describe --tags --always --dirty)

.PHONY: cassandra-operator
cassandra-operator:
	cd $(rootdir) && docker build \
		--build-arg version=$(git_revision) \
		-t $(DOCKER_REPO)/cassandra-operator \
		-f docker/cassandra-operator/Dockerfile \
		.

.PHONY: clean
clean:
	# no op

.DEFAULT_GOAL := cassandra-operator

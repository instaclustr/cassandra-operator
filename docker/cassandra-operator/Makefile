
# Figure out the path to the root of the repository
rootdir = $(shell git rev-parse --show-toplevel)

.PHONY: cassandra-operator
cassandra-operator:
	cd $(rootdir) && docker build \
		-t $(DOCKER_REGISTRY)cassandra-operator \
		-f docker/cassandra-operator/Dockerfile \
		.

.PHONY: clean
clean:
	# no op

.DEFAULT_GOAL := cassandra-operator


.PHONY: cassandra-operator
cassandra-operator:
	docker build \
		-t $(DOCKER_REGISTRY)cassandra-operator \
		.

.PHONY: clean
clean:
	# no op

.DEFAULT_GOAL := cassandra-operator

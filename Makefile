
.PHONY: all
all: java docker

.PHONY: java
java:
	cd java && mvn install

# Build cassandra-operator binary
.PHONY: operator
operator:
	cd cmd/manager && go build

# Build Docker images
.PHONY: docker
docker: java
	$(MAKE) -C $@


.DEFAULT_GOAL := all

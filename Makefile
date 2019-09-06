
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

# Build Docker images
.PHONY: docker
docker: java
	$(MAKE) -C $@


.DEFAULT_GOAL := all

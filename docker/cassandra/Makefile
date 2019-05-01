include ../base-openjre/Makefile

CASSANDRA_VERSION := 3.11.3

CASSANDRA_K8S_ADDONS_VERSION := 1.0-SNAPSHOT
CASSANDRA_K8S_ADDONS_JAR := cassandra-k8s-addons-$(CASSANDRA_K8S_ADDONS_VERSION).jar
RENDERED_DOCKERFILE := Dockerfile-cassandra

# Cassandra/K8s Addons
ifeq ($(CASSANDRA_K8S_ADDONS_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
# mark addons-jar target as .PHONY to force a re-copy on every build
$(info cassandra-k8s-addons SNAPSHOT version specified ($(CASSANDRA_K8S_ADDONS_VERSION)). Marking JAR target as .PHONY. This will re-download the artifact.)
.PHONY: $(CASSANDRA_K8S_ADDONS_JAR)
endif

$(CASSANDRA_K8S_ADDONS_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-k8s-addons:$(CASSANDRA_K8S_ADDONS_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true

# Cassandra
.PHONY: cassandra
cassandra: $(CASSANDRA_K8S_ADDONS_JAR)

	cp Dockerfile.template $(RENDERED_DOCKERFILE)
	sed -i "s {{openjre_image}} $(OPENJRE_IMAGE) g" $(RENDERED_DOCKERFILE)

	docker build \
		-f $(RENDERED_DOCKERFILE) \
		--build-arg cassandra_version=$(CASSANDRA_VERSION) \
		--build-arg cassandra_k8s_addons_jar=$(CASSANDRA_K8S_ADDONS_JAR) \
		-t $(DOCKER_REPO)cassandra \
		-t $(DOCKER_REPO)cassandra:$(CASSANDRA_VERSION) \
		.

	rm $(RENDERED_DOCKERFILE)

.PHONY: clean
clean:
	rm -f $(CASSANDRA_K8S_ADDONS_JAR)
	rm -f $(RENDERED_DOCKERFILE)

.DEFAULT_GOAL := cassandra

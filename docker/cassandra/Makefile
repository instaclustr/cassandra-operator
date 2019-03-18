include ../base-openjre/Makefile

CASSANDRA_VERSION := 3.11.3

CASSANDRA_K8S_ADDONS_VERSION := 1.0-SNAPSHOT
CASSANDRA_K8S_ADDONS_JAR := cassandra-k8s-addons-$(CASSANDRA_K8S_ADDONS_VERSION).jar

ifeq ($(CASSANDRA_K8S_ADDONS_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking JAR target as .PHONY)
.PHONY: $(CASSANDRA_K8S_ADDONS_JAR)
endif

$(CASSANDRA_K8S_ADDONS_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-k8s-addons:$(CASSANDRA_K8S_ADDONS_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true
	
.PHONY: cassandra
cassandra: $(CASSANDRA_K8S_ADDONS_JAR)
	docker build \
		--build-arg cassandra_version=$(CASSANDRA_VERSION) \
		--build-arg cassandra_k8s_addons_jar=$(CASSANDRA_K8S_ADDONS_JAR) \
		--build-arg openjre_image="$(OPENJRE_IMAGE)" \
		-t $(DOCKER_REPO)cassandra \
		-t $(DOCKER_REPO)cassandra:$(CASSANDRA_VERSION) \
		.

.PHONY: clean
clean:
	rm $(CASSANDRA_K8S_ADDONS_JAR)

.DEFAULT_GOAL := cassandra

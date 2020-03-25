include ../base-openjre/Makefile

CASSANDRA_SIDECAR_VERSION := 1.5.0
CASSANDRA_SIDECAR_JAR := cassandra-k8s-sidecar-$(CASSANDRA_SIDECAR_VERSION).jar

ifeq ($(CASSANDRA_SIDECAR_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking sidecar JAR target as .PHONY)
.PHONY: $(CASSANDRA_SIDECAR_JAR)
endif

$(CASSANDRA_SIDECAR_JAR):
	rm -rf $(CASSANDRA_SIDECAR_JAR)
	mvn dependency:copy -Dartifact=com.instaclustr:cassandra-k8s-sidecar:$(CASSANDRA_SIDECAR_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true -Dmdep.overIfNewer=true

.PHONY: cassandra-sidecar
cassandra-sidecar: $(CASSANDRA_SIDECAR_JAR)
	docker build \
		--build-arg cassandra_sidecar_jar=$(CASSANDRA_SIDECAR_JAR) \
		--build-arg openjre_image="$(OPENJRE_IMAGE)" \
		-t $(DOCKER_REGISTRY)cassandra-sidecar \
		-t $(DOCKER_REGISTRY)cassandra-sidecar:$(CASSANDRA_SIDECAR_VERSION) \
		.

.PHONY: clean
clean:
	rm $(CASSANDRA_SIDECAR_JAR)

.DEFAULT_GOAL := cassandra-sidecar

include ../base-openjre/Makefile

CASSANDRA_SIDECAR_VERSION := 1.0-SNAPSHOT
CASSANDRA_SIDECAR_JAR := cassandra-sidecar-$(CASSANDRA_SIDECAR_VERSION).jar
RENDERED_DOCKERFILE := Dockerfile-cassandra-sidecar

ifeq ($(CASSANDRA_SIDECAR_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
# mark sidecar-jar target as .PHONY to force a re-copy on every build
$(info cassandra-sidecar SNAPSHOT version specified ($(CASSANDRA_SIDECAR_VERSION)). Marking JAR target as .PHONY. This will re-download the artifact.)
.PHONY: $(CASSANDRA_SIDECAR_JAR)
endif

$(CASSANDRA_SIDECAR_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-sidecar:$(CASSANDRA_SIDECAR_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true
	
.PHONY: cassandra-sidecar
cassandra-sidecar: $(CASSANDRA_SIDECAR_JAR)

	cp Dockerfile.template $(RENDERED_DOCKERFILE)
	sed -i "s {openjre_image} $(OPENJRE_IMAGE) g" $(RENDERED_DOCKERFILE)

	docker build \
		-f $(RENDERED_DOCKERFILE) \
		--build-arg cassandra_sidecar_jar=$(CASSANDRA_SIDECAR_JAR) \
		-t $(DOCKER_REPO)cassandra-sidecar \
		-t $(DOCKER_REPO)cassandra-sidecar:$(CASSANDRA_SIDECAR_VERSION) \
		.

	rm $(RENDERED_DOCKERFILE)

.PHONY: clean
clean:
	rm -f $(CASSANDRA_SIDECAR_JAR)
	rm -f $(RENDERED_DOCKERFILE)


.DEFAULT_GOAL := cassandra-sidecar

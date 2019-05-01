include ../base-openjre/Makefile

CASSANDRA_OPERATOR_VERSION := 1.0-SNAPSHOT
CASSANDRA_OPERATOR_JAR := cassandra-operator-$(CASSANDRA_OPERATOR_VERSION).jar
RENDERED_DOCKERFILE := Dockerfile-cassandra-operator

ifeq ($(CASSANDRA_OPERATOR_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking JAR target as .PHONY)
.PHONY: $(CASSANDRA_OPERATOR_JAR)
endif

$(CASSANDRA_OPERATOR_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-operator:$(CASSANDRA_OPERATOR_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true
	
.PHONY: cassandra-operator
cassandra-operator: $(CASSANDRA_OPERATOR_JAR)

	cp Dockerfile.template $(RENDERED_DOCKERFILE)
	sed -i "s {openjre_image} $(OPENJRE_IMAGE) g" $(RENDERED_DOCKERFILE)

	docker build \
		-f $(RENDERED_DOCKERFILE) \
		--build-arg cassandra_operator_jar=$(CASSANDRA_OPERATOR_JAR) \
		-t $(DOCKER_REPO)cassandra-operator \
		-t $(DOCKER_REPO)cassandra-operator:$(CASSANDRA_OPERATOR_VERSION) \
		.

	rm $(RENDERED_DOCKERFILE)

.PHONY: clean
clean:
	rm -f $(CASSANDRA_OPERATOR_JAR)
	rm -f $(RENDERED_DOCKERFILE)

.DEFAULT_GOAL := cassandra-operator

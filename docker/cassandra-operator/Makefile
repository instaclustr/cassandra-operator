include ../base-openjre/Makefile

CASSANDRA_OPERATOR_VERSION := 1.0-SNAPSHOT
CASSANDRA_OPERATOR_JAR := cassandra-operator-$(CASSANDRA_OPERATOR_VERSION).jar

ifeq ($(CASSANDRA_OPERATOR_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking JAR target as .PHONY)
.PHONY: $(CASSANDRA_OPERATOR_JAR)
endif

$(CASSANDRA_OPERATOR_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-operator:$(CASSANDRA_OPERATOR_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true
	
.PHONY: cassandra-operator
cassandra-operator: $(CASSANDRA_OPERATOR_JAR)
	$(BUILD_COMMAND) \
		--build-arg cassandra_operator_jar=$(CASSANDRA_OPERATOR_JAR) \
		--build-arg openjre_image="$(OPENJRE_IMAGE)" \
		-t $(DOCKER_REPO)cassandra-operator \
		-t $(DOCKER_REPO)cassandra-operator:$(CASSANDRA_OPERATOR_VERSION) \
		.

.PHONY: clean
clean:
	rm $(CASSANDRA_OPERATOR_JAR)

.DEFAULT_GOAL := cassandra-operator

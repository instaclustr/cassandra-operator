INSTACLUSTR_ICARUS_VERSION := 1.0.6
INSTACLUSTR_ICARUS_JAR := icarus-$(INSTACLUSTR_ICARUS_VERSION).jar
OPENJRE_IMAGE := $(if $(OPENJRE_IMAGE),$(OPENJRE_IMAGE),"base-openjre:buster-11.0.16-8-1-deb10u1")

ifeq ($(INSTACLUSTR_ICARUS_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking sidecar JAR target as .PHONY)
.PHONY: $(INSTACLUSTR_ICARUS_JAR)
endif

$(INSTACLUSTR_ICARUS_JAR):
	rm -rf $(INSTACLUSTR_ICARUS_JAR)
	$(CURDIR)/../mvnw dependency:copy -Dartifact=com.instaclustr:icarus:$(INSTACLUSTR_ICARUS_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true -Dmdep.overIfNewer=true

.PHONY: cassandra-sidecar
cassandra-sidecar: $(INSTACLUSTR_ICARUS_JAR)
	docker build \
		--build-arg instaclustr_icarus_jar=$(INSTACLUSTR_ICARUS_JAR) \
		--build-arg openjre_image="$(OPENJRE_IMAGE)" \
		-t $(DOCKER_REGISTRY)instaclustr-icarus \
		-t $(DOCKER_REGISTRY)instaclustr-icarus:$(INSTACLUSTR_ICARUS_VERSION) \
		.

.PHONY: clean
clean:
	rm $(INSTACLUSTR_ICARUS_JAR)

.DEFAULT_GOAL := cassandra-sidecar

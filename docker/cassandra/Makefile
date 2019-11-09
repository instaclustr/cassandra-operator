include ../base-openjre/Makefile

CASSANDRA_VERSION := $(if $(CASSANDRA_VERSION),$(CASSANDRA_VERSION),"3.11.5")
CASSANDRA_EXPORTER_VERSION := $(if $(CASSANDRA_EXPORTER_VERSION),$(CASSANDRA_EXPORTER_VERSION),"0.9.9")
CASSANDRA_K8S_ADDONS_VERSION := 1.0.0
CASSANDRA_K8S_ADDONS_JAR := cassandra-k8s-addons-$(CASSANDRA_K8S_ADDONS_VERSION).jar

ifeq ($(CASSANDRA_K8S_ADDONS_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking JAR target as .PHONY)
.PHONY: $(CASSANDRA_K8S_ADDONS_JAR)
endif

$(CASSANDRA_K8S_ADDONS_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr:cassandra-k8s-addons:$(CASSANDRA_K8S_ADDONS_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true

C_APACHE_MIRROR_URL := $(if $(C_APACHE_MIRROR_URL),$(C_APACHE_MIRROR_URL),"https://dl.bintray.com/apache/cassandra/pool/main/c/cassandra")
INSTALL_CASSANDRA_EXPORTER := $(if $(INSTALL_CASSANDRA_EXPORTER),$(INSTALL_CASSANDRA_EXPORTER),"true")

.PHONY: cassandra
cassandra: $(CASSANDRA_K8S_ADDONS_JAR)
	@echo \"$(OPENJRE_IMAGE)\"
	docker build \
		--build-arg cassandra_version=$(CASSANDRA_VERSION) \
		--build-arg cassandra_exporter_version=$(CASSANDRA_EXPORTER_VERSION) \
		--build-arg cassandra_k8s_addons_jar=$(CASSANDRA_K8S_ADDONS_JAR) \
		--build-arg openjre_image="$(OPENJRE_IMAGE)" \
		--build-arg apache_mirror_url="$(C_APACHE_MIRROR_URL)" \
		--build-arg install_cassandra_exporter="$(INSTALL_CASSANDRA_EXPORTER)" \
		-t $(DOCKER_REGISTRY)cassandra \
		-t $(DOCKER_REGISTRY)cassandra:$(CASSANDRA_VERSION) \
		.

.PHONY: clean
clean:
	rm $(CASSANDRA_K8S_ADDONS_JAR)

.DEFAULT_GOAL := cassandra

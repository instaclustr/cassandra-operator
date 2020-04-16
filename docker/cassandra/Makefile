CASSANDRA_3_OPENJRE_IMAGE := $(if $(OPENJRE_IMAGE),$(OPENJRE_IMAGE),"base-openjre:stretch-8u232-b09-1-deb9u1")
CASSANDRA_3_VERSION := $(if $(CASSANDRA_VERSION),$(CASSANDRA_VERSION),"3.11.6")
CASSANDRA_3_VERSION_IMAGE_TAG := $(if $(CASSANDRA_VERSION),$(subst ~,-,$(CASSANDRA_VERSION)),"3.11.6")

CASSANDRA_4_OPENJRE_IMAGE := $(if $(CASSANDRA_4_OPENJRE_IMAGE),$(CASSANDRA_4_OPENJRE_IMAGE),"base-openjre:buster-11.0.6-10-1-deb10u1")
CASSANDRA_4_VERSION=$(if $(CASSANDRA_VERSION),$(CASSANDRA_VERSION),"4.0~alpha3")
CASSANDRA_4_VERSION_IMAGE_TAG := $(if $(CASSANDRA_4_VERSION),$(subst ~,-,$(CASSANDRA_4_VERSION)),"4.0~alpha3")

CASSANDRA_EXPORTER_VERSION := $(if $(CASSANDRA_EXPORTER_VERSION),$(CASSANDRA_EXPORTER_VERSION),"0.9.10")
CASSANDRA_K8S_ADDONS_VERSION := $(if $(CASSANDRA_K8S_ADDONS_VERSION),$(CASSANDRA_K8S_ADDONS_VERSION),"1.0.2")

CASSANDRA_IMAGE_VERSION := $(if $(CASSANDRA_IMAGE_VERSION),$(CASSANDRA_IMAGE_VERSION),"1.0.0")

C_APACHE_MIRROR_URL := $(if $(C_APACHE_MIRROR_URL),$(C_APACHE_MIRROR_URL),"https://dl.bintray.com/apache/cassandra/pool/main/c/cassandra")

.PHONY: cassandra-3
cassandra-3:
	rm -rf cassandra-3/jars
	$(CURDIR)/../mvnw dependency:copy -Dartifact=com.instaclustr:cassandra-3-k8s-addons:1.0.2 -DoutputDirectory=cassandra-3/jars -Dmdep.stripClassifier=true -Dmdep.overIfNewer=true
	$(CURDIR)/../mvnw dependency:copy -Dartifact=com.instaclustr:cassandra-k8s-addons:1.0.1 -DoutputDirectory=cassandra-3/jars -Dmdep.stripClassifier=true -Dmdep.overIfNewer=true
	docker build \
        --build-arg cassandra_major_version=3 \
		--build-arg cassandra_version=$(CASSANDRA_3_VERSION) \
		--build-arg openjre_image="$(CASSANDRA_3_OPENJRE_IMAGE)" \
		--build-arg apache_mirror_url="$(C_APACHE_MIRROR_URL)" \
		--build-arg install_cassandra_exporter=true \
		--build-arg cassandra_exporter_version=$(CASSANDRA_EXPORTER_VERSION) \
		-t $(DOCKER_REGISTRY)cassandra-${CASSANDRA_3_VERSION_IMAGE_TAG} \
		-t $(DOCKER_REGISTRY)cassandra-${CASSANDRA_3_VERSION_IMAGE_TAG}:$(CASSANDRA_IMAGE_VERSION) \
		.

.PHONY: cassandra-4
cassandra-4:
	rm -rf cassandra-4/jars
	$(CURDIR)/../mvnw dependency:copy -Dartifact=com.instaclustr:cassandra-4-k8s-addons:1.0.1 -DoutputDirectory=cassandra-4/jars -Dmdep.stripClassifier=true -Dmdep.overIfNewer=true
	$(CURDIR)/../mvnw dependency:copy -Dartifact=com.instaclustr:cassandra-k8s-addons:1.0.1 -DoutputDirectory=cassandra-4/jars -Dmdep.stripClassifier=true -Dmdep.overIfNewer=true
	docker build \
	    --build-arg cassandra_major_version=4 \
		--build-arg cassandra_version=${CASSANDRA_4_VERSION} \
		--build-arg openjre_image="$(CASSANDRA_4_OPENJRE_IMAGE)" \
		--build-arg apache_mirror_url="$(C_APACHE_MIRROR_URL)" \
		--build-arg install_cassandra_exporter=false \
		-t $(DOCKER_REGISTRY)cassandra-${CASSANDRA_4_VERSION_IMAGE_TAG} \
		-t $(DOCKER_REGISTRY)cassandra-${CASSANDRA_4_VERSION_IMAGE_TAG}:$(CASSANDRA_IMAGE_VERSION) \
		.

.PHONY: clean
clean:
	rm $(CASSANDRA_K8S_ADDONS_JAR)

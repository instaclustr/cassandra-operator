IMAGES = base-openjre base-openjre-11 cassandra cassandra-4 cassandra-sidecar cassandra-operator
BUILD_IMAGES = circle-build-env

C_IMAGE_VERSION := $(if $(C_IMAGE_VERSION),$(C_IMAGE_VERSION),"1.0.0")

.PHONY: all
all: $(IMAGES)

.PHONY: build
build: $(BUILD_IMAGES)

.PHONY: $(IMAGES)
$(IMAGES):
	$(MAKE) -C $@

.PHONY: $(BUILD_IMAGES)
$(BUILD_IMAGES):
	$(MAKE) -C $@

.PHONY: base-openjre-11
base-openjre-11:
	DEBIAN_RELEASE=buster OPENJRE_BASE_IMAGE=debian:buster OPENJRE_VERSION=11.0.6+10-1~deb10u1 OPENJRE_IMAGE_TAG=buster-11.0.6-10-1-deb10u1 OPENJDK_PACKAGE=openjdk-11-jre-headless CASSANDRA_IMAGE_VERSION=${C_IMAGE_VERSION} $(MAKE) -C base-openjre

.PHONY: cassandra-4
cassandra-4:
	INSTALL_CASSANDRA_EXPORTER=false CASSANDRA_VERSION=4.0~alpha3 OPENJRE_IMAGE=base-openjre:buster-11.0.6-10-1-deb10u1 CASSANDRA_K8S_ADDONS_VERSION=1.0.1 CASSANDRA_K8S_ADDONS_ARTIFACT=cassandra-4-k8s-addons CASSANDRA_IMAGE_VERSION=${C_IMAGE_VERSION} $(MAKE) -C cassandra

cassandra-4: base-openjre-11

cassandra cassandra-sidecar cassandra-operator: base-openjre

.DEFAULT_GOAL := all

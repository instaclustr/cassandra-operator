IMAGES = base-openjre base-openjre-11 cassandra-3 cassandra-4 cassandra-sidecar cassandra-operator
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
	DEBIAN_RELEASE=buster OPENJRE_BASE_IMAGE=debian:buster OPENJRE_VERSION=11.0.9.1+1-1~deb10u2 OPENJRE_IMAGE_TAG=buster-11.0.9.1-1-1-deb10u2 OPENJDK_PACKAGE=openjdk-11-jre-headless CASSANDRA_IMAGE_VERSION=${C_IMAGE_VERSION} $(MAKE) -C base-openjre

.PHONY: cassandra-3
cassandra-3:
	$(MAKE) -C cassandra cassandra-3

.PHONY: cassandra-4
cassandra-4:
	$(MAKE) -C cassandra cassandra-4

cassandra-4: base-openjre-11

cassandra-3: base-openjre

cassandra-sidecar: base-openjre-11

.DEFAULT_GOAL := all

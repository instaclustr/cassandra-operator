# to build buster with java 11, execute
# DEBIAN_RELEASE=buster OPENJRE_BASE_IMAGE=debian:buster OPENJRE_VERSION=11.0.6+10-1~deb10u1 OPENJRE_IMAGE_TAG=buster-11.0.6-10-1-deb10u1 OPENJRE_IMAGE_TAG=buster OPENJDK_PACKAGE=openjdk-11-jre-headless make

DEBIAN_RELEASE := stretch
OPENJRE_VERSION := $(if $(OPENJRE_VERSION),$(OPENJRE_VERSION),"8u332-ga-1~deb9u1")
OPENJRE_IMAGE_TAG := $(if $(OPENJRE_IMAGE_TAG),$(OPENJRE_IMAGE_TAG),$(DEBIAN_RELEASE)-$(subst ~,-,$(OPENJRE_VERSION)))
OPENJRE_IMAGE := $(DOCKER_REGISTRY)base-openjre:$(OPENJRE_IMAGE_TAG)
OPENJRE_BASE_IMAGE := $(if $(OPENJRE_BASE_IMAGE),$(OPENJRE_BASE_IMAGE),"debian:"$(DEBIAN_RELEASE))
OPENJDK_PACKAGE := $(if $(OPENJDK_PACKAGE),$(OPENJDK_PACKAGE),"openjdk-8-jre-headless")

.PHONY: base-openjre
base-openjre:
	docker build \
		--build-arg OPENJRE_BASE_IMAGE=$(OPENJRE_BASE_IMAGE) \
		--build-arg openjre_version=$(OPENJRE_VERSION) \
		--build-arg openjre_package=$(OPENJDK_PACKAGE) \
		-t $(DOCKER_REGISTRY)base-openjre \
		-t $(OPENJRE_IMAGE) \
		.

.DEFAULT_GOAL := base-openjre

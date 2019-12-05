OPENJRE_VERSION := $(if $(OPENJRE_VERSION),$(OPENJRE_VERSION),"8u232-b09-1~deb9u1")
OPENJRE_IMAGE_TAG := stretch-$(subst ~,-,$(OPENJRE_VERSION))
OPENJRE_IMAGE := $(DOCKER_REGISTRY)base-openjre:$(OPENJRE_IMAGE_TAG)
OPENJRE_BASE_IMAGE := $(if $(OPENJRE_BASE_IMAGE),$(OPENJRE_BASE_IMAGE),"debian:stretch")

.PHONY: base-openjre
base-openjre:
	docker build \
		--build-arg OPENJRE_BASE_IMAGE=$(OPENJRE_BASE_IMAGE) \
		--build-arg openjre_version=$(OPENJRE_VERSION) \
		-t $(DOCKER_REGISTRY)base-openjre \
		-t $(OPENJRE_IMAGE) \
		.

.DEFAULT_GOAL := base-openjre

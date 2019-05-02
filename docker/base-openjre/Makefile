OPENJRE_VERSION := "8u212-b01-1~deb9u1"
OPENJRE_IMAGE_TAG := stretch-$(subst ~,-,$(OPENJRE_VERSION))
OPENJRE_IMAGE := $(DOCKER_REPO)base-openjre:$(OPENJRE_IMAGE_TAG)

.PHONY: base-openjre
base-openjre:
	docker build \
		--build-arg openjre_version=$(OPENJRE_VERSION) \
		-t $(DOCKER_REPO)base-openjre \
		-t $(OPENJRE_IMAGE) \
		.

.DEFAULT_GOAL := base-openjre

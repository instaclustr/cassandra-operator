IMAGE_TAG := 2.0.2
IMAGE := $(DOCKER_REGISTRY)jdk8-gcloud-sdk:$(IMAGE_TAG)

.PHONY: circle-build-env
circle-build-env:
	docker build \
		-t $(IMAGE) \
		.

.DEFAULT_GOAL := circle-build-env

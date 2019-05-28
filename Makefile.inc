BUILD_COMMAND ?= docker build

# DOCKER_REPO should be either blank (for local images) or end with a trailing slash (for remote images)
# TODO: add shash automatically?
DOCKER_REPO ?=
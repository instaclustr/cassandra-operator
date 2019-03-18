
.PHONY: all
all: java docker

.PHONY: java
java:
	cd java && mvn install
	
.PHONY: docker
docker: java
	$(MAKE) -C $@


.DEFAULT_GOAL := all

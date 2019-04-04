IMAGES = base-openjre cassandra cassandra-sidecar cassandra-operator

.PHONY: all
all: $(IMAGES)

.PHONY: $(IMAGES)
$(IMAGES):
	$(MAKE) -C $@
        
cassandra cassandra-sidecar cassandra-operator: base-openjre

.DEFAULT_GOAL := all

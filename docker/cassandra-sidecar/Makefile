include ../base-openjre/Makefile

CASSANDRA_SIDECAR_VERSION := 1.0.0
CASSANDRA_SIDECAR_JAR := cassandra-sidecar-$(CASSANDRA_SIDECAR_VERSION).jar
CASSANDRA_BACKUP_RESTORE_VERSION := 1.0.0
CASSANDRA_BACKUP_RESTORE_JAR := cassandra-backup-restore-$(CASSANDRA_BACKUP_RESTORE_VERSION).jar

ifeq ($(CASSANDRA_SIDECAR_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking sidecar JAR target as .PHONY)
.PHONY: $(CASSANDRA_SIDECAR_JAR)
endif

ifeq ($(CASSANDRA_BACKUP_RESTORE_VERSION:%-SNAPSHOT=SNAPSHOT),SNAPSHOT)
$(info SNAPSHOT version specified. Marking backup-restore JAR target as .PHONY)
.PHONY: $(CASSANDRA_BACKUP_RESTORE_JAR)
endif

$(CASSANDRA_SIDECAR_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-sidecar:$(CASSANDRA_SIDECAR_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true

$(CASSANDRA_BACKUP_RESTORE_JAR):
	mvn dependency:copy -Dartifact=com.instaclustr.cassandra-operator:cassandra-backup-restore:$(CASSANDRA_BACKUP_RESTORE_VERSION) -DoutputDirectory=. -Dmdep.stripClassifier=true

.PHONY: cassandra-sidecar
cassandra-sidecar: $(CASSANDRA_SIDECAR_JAR) $(CASSANDRA_BACKUP_RESTORE_JAR)
	docker build \
		--build-arg cassandra_sidecar_jar=$(CASSANDRA_SIDECAR_JAR) \
		--build-arg cassandra_backup_restore_jar=$(CASSANDRA_BACKUP_RESTORE_JAR) \
		--build-arg openjre_image="$(OPENJRE_IMAGE)" \
		-t $(DOCKER_REGISTRY)cassandra-sidecar \
		-t $(DOCKER_REGISTRY)cassandra-sidecar:$(CASSANDRA_SIDECAR_VERSION) \
		.

.PHONY: clean
clean:
	rm $(CASSANDRA_SIDECAR_JAR)
	rm $(CASSANDRA_BACKUP_RESTORE)


.DEFAULT_GOAL := cassandra-sidecar

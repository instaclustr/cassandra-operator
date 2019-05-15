package controller

const (
	NewLine = "\n"

	MEBIBYTE = 1 << 20
	GIBIBYTE = 1 << 30

	CassandraContainerName                              = "cassandra"
	CassandraInternodePort                              = 7000
	CassandraCqlPort                                    = 9042
	CassandraJMXPort                                    = 7199
	CassandraPrometheusPort                             = 9500
	CassandraContainerCqlReadinessProbeScriptPath       = "/usr/bin/cql-readiness-probe"
	CassandraContainerVolumeMountPath                   = "/var/lib/cassandra"
	CassandraReadinessProbeInitialDelayInSeconds        = 60
	CassandraReadinessProbeInitialDelayTimeoutInSeconds = 5

	SidecarContainerName                   = "sidecar"
	SidecarPort                            = 4567
	SidecarContainerVolumeMountPath        = "/var/lib/cassandra"
	SidecarContainerPodInfoVolumeMountPath = "/etc/pod-info"

	StatefulSetServiceName = "cassandra"

	PodInfoVolumeName = "pod-info"

	ConfigMapEndpointSnitch                = "org.apache.cassandra.locator.GossipingPropertyFileSnitch"
	ConfigMapSeedProviderClass             = "com.instaclustr.cassandra.k8s.SeedProvider"
	ConfigMapOperatorOverridesYamlPath     = "cassandra.yaml.d/001-operator-overrides.yaml"
	ConfigMapJVMMemoryGcOptionsFilePath    = "jvm.options.d/001-jvm-memory-gc.options"
	ConfigMapCassandraEnvExporterPath      = "cassandra-env.sh.d/001-cassandra-exporter.sh"
	ConfigMapCassandraEnvLimitsPath        = "cassandra-env.sh.d/002-cassandra-limits.sh"
	ConfigMapCassandraRackDCPropertiesPath = "cassandra-rackdc.properties"
	ConfigMapOperatorVolumeName            = "operator-config-volume"
	ConfigMapOperatorVolumeMountPath       = "/tmp/operator-config"
)

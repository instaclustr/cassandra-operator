package cassandradatacenter

import (
	"context"
	"fmt"
	"regexp"
	"strings"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"

	"github.com/pkg/errors"
	"gopkg.in/yaml.v2"
	corev1 "k8s.io/api/core/v1"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
)

const (
	MEBIBYTE = 1 << 20
	GIBIBYTE = 1 << 30
)

func createOrUpdateOperatorConfigMap(rctx *reconciliationRequestContext, seedNodesService *corev1.Service) (*corev1.Volume, error) {
	configMap := &corev1.ConfigMap{ObjectMeta: DataCenterResourceMetadata(rctx.cdc, "operator-config")}

	logger := rctx.logger.WithValues("ConfigMap.Name", configMap.Name)

	volumeSource := &corev1.ConfigMapVolumeSource{LocalObjectReference: corev1.LocalObjectReference{Name: configMap.Name}}

	opresult, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, configMap, func() error {
		addFileFn := func(path string, data string) {
			configMapVolumeAddTextFile(configMap, volumeSource, path, data)
		}

		err := addCassandraYamlOverrides(rctx.cdc, seedNodesService, addFileFn)
		if err != nil {
			return errors.Wrap(err, "adding Cassandra YAML overrides")
		}

		addCassandraJVMOptions(rctx.cdc, addFileFn)

		addPrometheusSupport(rctx.cdc, addFileFn)

		if err := controllerutil.SetControllerReference(rctx.cdc, configMap, rctx.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	// Only log if something has changed
	if opresult != controllerutil.OperationResultNone {
		logger.Info(fmt.Sprintf("ConfigMap %s %s.", configMap.Name, opresult))
	}

	configVolume := &corev1.Volume{
		Name:         "operator-config-volume",
		VolumeSource: corev1.VolumeSource{ConfigMap: volumeSource},
	}

	return configVolume, nil
}

func createOrUpdateCassandraRackConfig(rctx *reconciliationRequestContext, rack *cluster.Rack) (*corev1.Volume, error) {

	configMap := &corev1.ConfigMap{ObjectMeta: RackMetadata(rctx.cdc, rack, "rack-config")}

	logger := rctx.logger.WithValues("ConfigMap.Name", configMap.Name)

	volumeSource := &corev1.ConfigMapVolumeSource{LocalObjectReference: corev1.LocalObjectReference{Name: configMap.Name}}

	opresult, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, configMap, func() error {
		addFileFn := func(path string, data string) {
			configMapVolumeAddTextFile(configMap, volumeSource, path, data)
		}

		addCassandraGossipingPropertyFileSnitchProperties(rctx.cdc, rack, addFileFn)

		if err := controllerutil.SetControllerReference(rctx.cdc, configMap, rctx.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	// Only log if something has changed
	if opresult != controllerutil.OperationResultNone {
		logger.Info(fmt.Sprintf("ConfigMap %s %s.", configMap.Name, opresult))
	}

	configVolume := &corev1.Volume{
		Name:         "operator-rack-config-volume-" + rack.Name,
		VolumeSource: corev1.VolumeSource{ConfigMap: volumeSource},
	}

	return configVolume, nil
}

func addCassandraGossipingPropertyFileSnitchProperties(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, rack *cluster.Rack, addFileFn func(path string, data string)) {
	var writer strings.Builder

	_, _ = fmt.Fprintln(&writer, "# generated by cassandra-operator")

	properties := map[string]string{
		"dc":           cdc.DataCenter,
		"rack":         rack.Name,
		"prefer_local": "true",
	}

	writeProperty := func(key string, value string) {
		_, _ = fmt.Fprintf(&writer, "%s=%s\n", key, value)
	}

	for key, value := range properties {
		writeProperty(key, value)
	}

	addFileFn("cassandra-rackdc.properties", writer.String())
}

func addPrometheusSupport(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, addFileFn func(path string, data string)) {
	if cdc.Spec.PrometheusSupport && !isCassandra4(cdc) {
		addFileFn(
			"cassandra-env.sh.d/001-cassandra-exporter.sh",
			"JVM_OPTS=\"${JVM_OPTS} -javaagent:${CASSANDRA_HOME}/agents/cassandra-exporter-agent.jar=@${CASSANDRA_CONF}/cassandra-exporter.conf\"",
		)
	}
}

func addCassandraYamlOverrides(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, seedNodesService *corev1.Service, addFileFn func(path string, data string)) error {
	type SeedProvider struct {
		ClassName  string              `yaml:"class_name"`
		Parameters []map[string]string `yaml:"parameters"`
	}

	type CassandraConfig struct {
		ClusterName    string         `yaml:"cluster_name"`
		ListenAddress  *string        `yaml:"listen_address"`
		RPCAddress     *string        `yaml:"rpc_address"`
		SeedProvider   []SeedProvider `yaml:"seed_provider"`
		EndpointSnitch string         `yaml:"endpoint_snitch"`
		DiskAccessMode string         `yaml:"disk_access_mode,omitempty"`
		Authenticator  string         `yaml:"authenticator,omitempty"`
		Authorizer     string         `yaml:"authorizer,omitempty"`
		RoleManager    string         `yaml:"role_manager"`
	}

	cc := &CassandraConfig{
		ClusterName:   cdc.Cluster,
		ListenAddress: nil, // let C* discover the listen address
		RPCAddress:    nil, // let C* discover the rpc address
		SeedProvider: []SeedProvider{
			{
				ClassName: "com.instaclustr.cassandra.k8s.SeedProvider",
				Parameters: []map[string]string{
					{"service": seedNodesService.Name},
				},
			},
		},
		EndpointSnitch: "org.apache.cassandra.locator.GossipingPropertyFileSnitch",
	}

	if cdc.Spec.CassandraAuth == nil {
		cc.Authenticator = "AllowAllAuthenticator"
		cc.Authorizer = "AllowAllAuthorizer"
		cc.RoleManager = "CassandraRoleManager"
	} else {
		cc.Authenticator = cdc.Spec.CassandraAuth.Authenticator
		cc.Authorizer = cdc.Spec.CassandraAuth.Authorizer
		cc.RoleManager = cdc.Spec.CassandraAuth.RoleManager
	}

	// Set disk_access_mode to 'mmap' only when user specifies `optimizeKernelParams: true`.
	if cdc.Spec.OptimizeKernelParams {
		cc.DiskAccessMode = "mmap"
	}

	data, err := yaml.Marshal(cc)
	if err != nil {
		// we're serializing a known structure to YAML -- if that fails...
		return errors.Wrap(err, "marshalling Cassandra YAML overrides")
	}

	addFileFn("cassandra.yaml.d/001-operator-overrides.yaml", string(data))

	return nil
}

func isCassandra4(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) bool {
	split := strings.Split(cdc.Spec.CassandraImage, "/")
	return strings.HasPrefix(split[len(split)-1], "cassandra-4")
}

func addCassandraJVMOptions(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, addFileFn func(path string, data string)) {

	var hasCustomGCOptions = false

	// skip our GC settings in case they are set externally
	if cdc.Spec.UserConfigMapVolumeSource != nil {
		for _, item := range cdc.Spec.UserConfigMapVolumeSource.Items {
			if item.Key == "gc" || item.Path == "jvm.options.d/gc.options" {
				hasCustomGCOptions = true
				break
			}
		}
	}

	if hasCustomGCOptions {
		return
	}

	memoryLimit := cdc.Spec.Resources.Requests.Memory().Value()

	jvmHeapSize := maxInt64(minInt64(memoryLimit/2, GIBIBYTE), minInt64(memoryLimit/4, 8*GIBIBYTE))

	youngGenSize := youngGen(jvmHeapSize)

	useG1GC := jvmHeapSize > 8*GIBIBYTE

	var writer strings.Builder

	_, _ = fmt.Fprintf(&writer, "-Xms%d\n", jvmHeapSize) // min heap size
	_, _ = fmt.Fprintf(&writer, "-Xmx%d\n", jvmHeapSize) // max heap size

	// copied from stock jvm.options
	if !useG1GC {
		_, _ = fmt.Fprintf(&writer, "-Xmn%d\n", youngGenSize) // young gen size

		if !isCassandra4(cdc) {
			_, _ = fmt.Fprintln(&writer, "-XX:+UseParNewGC")
		}

		_, _ = fmt.Fprintln(&writer, "-XX:+UseConcMarkSweepGC")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSParallelRemarkEnabled")
		_, _ = fmt.Fprintln(&writer, "-XX:SurvivorRatio=8")
		_, _ = fmt.Fprintln(&writer, "-XX:MaxTenuringThreshold=1")
		_, _ = fmt.Fprintln(&writer, "-XX:CMSInitiatingOccupancyFraction=75")
		_, _ = fmt.Fprintln(&writer, "-XX:+UseCMSInitiatingOccupancyOnly")
		_, _ = fmt.Fprintln(&writer, "-XX:CMSWaitDuration=10000")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSParallelInitialMarkEnabled")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSEdenChunksRecordAlways")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSClassUnloadingEnabled")

	} else {
		_, _ = fmt.Fprintln(&writer, "-XX:+UseG1GC")
		_, _ = fmt.Fprintln(&writer, "-XX:G1RSetUpdatingPauseTimePercent=5")
		_, _ = fmt.Fprintln(&writer, "-XX:MaxGCPauseMillis=500")

		if jvmHeapSize > 16*GIBIBYTE {
			_, _ = fmt.Fprintln(&writer, "-XX:InitiatingHeapOccupancyPercent=70")
		}

		// TODO: tune -XX:ParallelGCThreads, -XX:ConcGCThreads
	}

	// OOM Error handling
	_, _ = fmt.Fprintln(&writer, "-XX:+HeapDumpOnOutOfMemoryError")
	_, _ = fmt.Fprintln(&writer, "-XX:+CrashOnOutOfMemoryError")

	// TODO: maybe tune -Dcassandra.available_processors=number_of_processors - Wait till we build C* for Java 11
	// not sure if k8s exposes the right number of CPU cores inside the container

	addFileFn("jvm.options.d/001-jvm-memory-gc.options", writer.String())
}

func youngGen(jvmHeapSize int64) int64 {

	coreCount := int64(4) // TODO

	return minInt64(coreCount*MEBIBYTE, jvmHeapSize/4)
}

func minInt64(a int64, b int64) int64 {
	if a < b {
		return a
	}
	return b
}

func maxInt64(a int64, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

func configMapVolumeAddTextFile(configMap *corev1.ConfigMap, volumeSource *corev1.ConfigMapVolumeSource, path string, data string) {
	encodedKey := regexp.MustCompile("\\W").ReplaceAllLiteralString(path, "_")

	// lazy init
	if configMap.Data == nil {
		configMap.Data = make(map[string]string)
	}

	configMap.Data[encodedKey] = data
	volumeSource.Items = append(volumeSource.Items, corev1.KeyToPath{Key: encodedKey, Path: path})
}

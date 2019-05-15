package cassandradatacenter

import (
	"context"
	"fmt"
	"k8s.io/api/apps/v1beta2"
	"regexp"
	"strings"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"

	"gopkg.in/yaml.v2"
)

var log = logf.Log.WithName("controller_cassandradatacenter")

// Add creates a new CassandraDataCenter Controller and adds it to the Manager. The Manager will set fields on the Controller
// and Start it when the Manager is Started.
func Add(mgr manager.Manager) error {
	return add(mgr, newReconciler(mgr))
}

// newReconciler returns a new reconcile.Reconciler
func newReconciler(mgr manager.Manager) reconcile.Reconciler {
	return &CassandraDataCenterReconciler{client: mgr.GetClient(), scheme: mgr.GetScheme()}
}

// add adds a new Controller to mgr with r as the reconcile.Reconciler
func add(mgr manager.Manager, r reconcile.Reconciler) error {
	// Create a new controller
	c, err := controller.New("cassandradatacenter-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	// Watch for changes to primary resource CassandraDataCenter
	err = c.Watch(&source.Kind{Type: &cassandraoperatorv1alpha1.CassandraDataCenter{}}, &handler.EnqueueRequestForObject{})
	if err != nil {
		return err
	}

	// Watch for changes to secondary resources and requeue the owner CassandraDataCenter
	for _, t := range []runtime.Object{&corev1.Service{}, &v1beta2.StatefulSet{}, &corev1.ConfigMap{}} {
		err = c.Watch(&source.Kind{Type: t}, &handler.EnqueueRequestForOwner{
			IsController: true,
			OwnerType:    &cassandraoperatorv1alpha1.CassandraDataCenter{},
		})
		if err != nil {
			return err
		}
	}

	return nil
}

var _ reconcile.Reconciler = &CassandraDataCenterReconciler{}

// CassandraDataCenterReconciler reconciles a CassandraDataCenter object
type CassandraDataCenterReconciler struct {
	// This client, initialized using mgr.Client() above, is a split client
	// that reads objects from the cache and writes to the apiserver
	client client.Client
	scheme *runtime.Scheme
}

// TODO: better name? This "conflicts" with corev1.VolumeMount
type VolumeMount struct {
	Volume    corev1.Volume
	MountPath string
}

// Reconcile reads that state of the cluster for a CassandraDataCenter object and makes changes based on the state read
// and what is in the CassandraDataCenter.Spec
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (r *CassandraDataCenterReconciler) Reconcile(request reconcile.Request) (reconcile.Result, error) {
	reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
	reqLogger.Info("Reconciling CassandraDataCenter")

	// Fetch the CassandraDataCenter instance
	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	if err := r.client.Get(context.TODO(), request.NamespacedName, cdc); err != nil {
		if errors.IsNotFound(err) {
			// Request object not found, could have been deleted after reconcile request.
			// Owned objects are automatically garbage collected. For additional cleanup logic use finalizers.
			// Return and don't requeue
			return reconcile.Result{}, nil
		}
		// Error reading the object - requeue the request.
		return reconcile.Result{}, err
	}

	// get (or create a new, unique) Cluster for this CDC
	cluster := &cassandraoperatorv1alpha1.CassandraCluster{}
	_ = cluster
	//if err := r.getCluster(cdc, cluster); err != nil {
	//	return reconcile.Result{}, err
	//}

	nodesService, err := r.createOrUpdateNodesService(cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	seedNodesService, err := r.createOrUpdateSeedNodesService(cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	configMapVolumeMount, err := r.createOrUpdateOperatorConfigMap(cdc, seedNodesService)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := r.createOrUpdateStatefulSet(cdc, []*VolumeMount{configMapVolumeMount})
	if err != nil {
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet

	return reconcile.Result{}, nil
}

//func (r *CassandraDataCenterReconciler) getCluster(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, cluster *cassandraoperatorv1alpha1.CassandraCluster) error {
//	updateClusterReference := func() error {
//		cdc.Spec.Cluster = &corev1.LocalObjectReference{Name: cluster.Name}
//
//		return r.client.Update(context.TODO(), cdc)
//	}
//
//	createClusterObject := func(name string) error {
//		cluster := &cassandraoperatorv1alpha1.CassandraCluster{
//			ObjectMeta: metav1.ObjectMeta{
//				Name: name,
//				Namespace: cdc.Namespace,
//			},
//		}
//
//		return r.client.Create(context.TODO(), cluster)
//	}
//
//
//
//	for {
//		switch cdc.Spec.Cluster.(type) {
//		case corev1.LocalObjectReference:
//			// get existing cluster object (or error)
//			nsName := types.NamespacedName{
//				Namespace: cdc.Namespace,
//				Name:      cdc.Spec.Cluster.(corev1.LocalObjectReference).Name}
//
//			return r.client.Get(context.TODO(), nsName, cluster)
//
//
//		case nil:
//			// create new CassandraCluster object with a random name
//			for {
//				if err := createClusterObject("cassandra-" + petname.Generate(3, "-")); err != nil {
//					if errors.IsAlreadyExists(err) {
//						continue // try a new name
//					}
//
//					return err
//				}
//
//				break
//			}
//
//		case string:
//			// use existing (or create) named CassandraCluster object
//			if err := r.createClusterObject(cdc.Spec.Cluster.(string), cdc); err != nil {
//				if errors.IsAlreadyExists(err) {
//					break
//				}
//
//				return err
//			}
//
//		default:
//			panic(fmt.Sprintf("Unknown type %v",reflect.TypeOf(cdc.Spec.Cluster)))
//		}
//
//		// update the reference to skip (attempted) creation next time
//		if err := updateClusterReference(); err != nil {
//			return err
//		}
//	}
//}

//func (r *CassandraDataCenterReconciler) createClusterObject(name string, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) error {
//	cluster := &cassandraoperatorv1alpha1.CassandraCluster{
//		ObjectMeta: metav1.ObjectMeta{
//			Name: name,
//			Namespace: cdc.Namespace,
//		},
//	}
//
//	if err := r.client.Create(context.TODO(), cluster); err != nil {
//		return err
//	}
//
//	cdc.Spec.Cluster = &corev1.LocalObjectReference{Name: cluster.Name}
//
//	if err := r.client.Update(context.TODO(), cdc); err != nil {
//		return err
//	}
//
//	return nil
//}

func dataCenterLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		"cassandra-operator.instaclustr.com/datacenter": cdc.Name,
		"app.kubernetes.io/managed-by":                  "com.instaclustr.cassandra-operator",
	}
}

func dataCenterResourceMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	return metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Name + suffix,
		Labels:    dataCenterLabels(cdc),
	}
}

func (r *CassandraDataCenterReconciler) createOrUpdateNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {
	nodesService := &corev1.Service{ObjectMeta: dataCenterResourceMetadata(cdc, "nodes")}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, nodesService, func(_ runtime.Object) error {
		nodesService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports: []corev1.ServicePort{
				{
					Name: "cql",
					Port: 9042,
				},
				{
					Name: "jmx",
					Port: 7199,
				},
			},
			Selector: dataCenterLabels(cdc),
		}

		if err := controllerutil.SetControllerReference(cdc, nodesService, r.scheme); err != nil {
			return err
		}

		return nil
	})
	if err != nil {
		return nil, err
	}

	return nodesService, err
}

func (r *CassandraDataCenterReconciler) createOrUpdateSeedNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {
	seedNodesService := &corev1.Service{ObjectMeta: dataCenterResourceMetadata(cdc, "seeds")}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, seedNodesService, func(_ runtime.Object) error {
		seedNodesService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports: []corev1.ServicePort{
				{
					Name: "internode",
					Port: 7000,
				},
			},
			Selector:                 dataCenterLabels(cdc),
			PublishNotReadyAddresses: true,
		}

		if err := controllerutil.SetControllerReference(cdc, seedNodesService, r.scheme); err != nil {
			return err
		}

		return nil
	})
	if err != nil {
		return nil, err
	}

	return seedNodesService, err
}

func (r *CassandraDataCenterReconciler) createOrUpdateStatefulSet(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumeMounts []*VolumeMount) (*v1beta2.StatefulSet, error) {
	dataVolumeClaim := corev1.PersistentVolumeClaim{
		ObjectMeta: metav1.ObjectMeta{Name: "data-volume"},
		Spec:       cdc.Spec.DataVolumeClaimSpec,
	}

	podInfoVolume := corev1.Volume{
		Name: "pod-info",
		VolumeSource: corev1.VolumeSource{
			DownwardAPI: &corev1.DownwardAPIVolumeSource{
				Items: []corev1.DownwardAPIVolumeFile{
					{Path: "labels", FieldRef: &corev1.ObjectFieldSelector{FieldPath: "metadata.labels"}},
					{Path: "annotations", FieldRef: &corev1.ObjectFieldSelector{FieldPath: "metadata.annotations"}},
					{Path: "namespace", FieldRef: &corev1.ObjectFieldSelector{FieldPath: "metadata.namespace"}},
					{Path: "name", FieldRef: &corev1.ObjectFieldSelector{FieldPath: "metadata.name"}},
				},
			},
		},
	}

	cassandraContainer := corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Args:            []string{},
		Ports: []corev1.ContainerPort{
			{Name: "internode", ContainerPort: 7000},
			{Name: "cql", ContainerPort: 9042},
			{Name: "jmx", ContainerPort: 7199},
		},
		Resources: cdc.Spec.Resources,
		SecurityContext: &corev1.SecurityContext{
			Capabilities: &corev1.Capabilities{
				Add: []corev1.Capability{"IPC_LOCK", "SYS_RESOURCE"},
			},
		},
		ReadinessProbe: &corev1.Probe{
			Handler: corev1.Handler{
				Exec: &corev1.ExecAction{
					Command: []string{"/usr/bin/cql-readiness-probe"},
				},
			},
			InitialDelaySeconds: 60,
			TimeoutSeconds:      5,
		},
		VolumeMounts: []corev1.VolumeMount{
			{Name: dataVolumeClaim.Name, MountPath: "/var/lib/cassandra"},
		},
	}

	if cdc.Spec.PrometheusSupport == true {
		cassandraContainer.Ports = append(cassandraContainer.Ports, corev1.ContainerPort{Name: "promql", ContainerPort: 9500})
	}

	sidecarContainer := corev1.Container{
		Name:            "sidecar",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports: []corev1.ContainerPort{
			{Name: "http", ContainerPort: 4567},
		},
		VolumeMounts: []corev1.VolumeMount{
			{Name: dataVolumeClaim.Name, MountPath: "/var/lib/cassandra"},
			{Name: podInfoVolume.Name, MountPath: "/etc/pod-info"},
		},
	}

	podSpec := corev1.PodSpec{
		Volumes:          []corev1.Volume{podInfoVolume},
		ImagePullSecrets: cdc.Spec.ImagePullSecrets,
	}

	// add all config Volumes and their VolumeMounts
	for _, vm := range volumeMounts {
		cassandraContainer.VolumeMounts = append(cassandraContainer.VolumeMounts, corev1.VolumeMount{
			Name:      vm.Volume.Name,
			MountPath: vm.MountPath,
		})

		// provide access to config map volumes in the sidecar, these reside in /tmp though and are not overlayed into /etc/cassandra
		// TODO: rework this
		sidecarContainer.VolumeMounts = append(sidecarContainer.VolumeMounts, corev1.VolumeMount{
			Name:      vm.Volume.Name,
			MountPath: vm.MountPath,
		})

		// entrypoint takes mount paths as arguments
		cassandraContainer.Args = append(cassandraContainer.Args, vm.MountPath)

		podSpec.Volumes = append(podSpec.Volumes, vm.Volume)
	}

	// this is a deep copy
	podSpec.Containers = []corev1.Container{cassandraContainer, sidecarContainer}

	statefulSet := &v1beta2.StatefulSet{ObjectMeta: dataCenterResourceMetadata(cdc)}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, statefulSet, func(_ runtime.Object) error {
		if statefulSet.Spec.Replicas != nil && *statefulSet.Spec.Replicas != cdc.Spec.Nodes {
			// TODO: scale safely

		}

		podLabels := dataCenterLabels(cdc)

		statefulSet.Spec = v1beta2.StatefulSetSpec{
			ServiceName: "cassandra",
			Replicas:    &cdc.Spec.Nodes,
			Selector:    &metav1.LabelSelector{MatchLabels: podLabels},
			Template: corev1.PodTemplateSpec{
				ObjectMeta: metav1.ObjectMeta{Labels: podLabels},
				Spec:       podSpec,
			},
			VolumeClaimTemplates: []corev1.PersistentVolumeClaim{dataVolumeClaim},
		}

		if err := controllerutil.SetControllerReference(cdc, statefulSet, r.scheme); err != nil {
			return err
		}

		return nil
	})
	if err != nil {
		return nil, err
	}

	return statefulSet, err
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

func configMapVolumeAddBinaryFile(configMap *corev1.ConfigMap, volumeSource *corev1.ConfigMapVolumeSource, path string, data []byte) {
	encodedKey := regexp.MustCompile("\\W").ReplaceAllLiteralString(path, "_")

	// lazy init
	if configMap.BinaryData == nil {
		configMap.BinaryData = make(map[string][]byte)
	}

	configMap.BinaryData[encodedKey] = data
	volumeSource.Items = append(volumeSource.Items, corev1.KeyToPath{Key: encodedKey, Path: path})
}

func (r *CassandraDataCenterReconciler) createOrUpdateOperatorConfigMap(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, seedNodesService *corev1.Service) (*VolumeMount, error) {
	// TODO: should these two be wrapped up into a single struct, since they always need to be passed around together to the various addXYZ functions?
	configMap := &corev1.ConfigMap{ObjectMeta: dataCenterResourceMetadata(cdc, "operator-config")}
	volumeSource := &corev1.ConfigMapVolumeSource{LocalObjectReference: corev1.LocalObjectReference{Name: configMap.Name}}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, configMap, func(_ runtime.Object) error {
		// cassandra.yaml overrides
		if err := addCassandraYamlOverrides(cdc, seedNodesService, configMap, volumeSource); err != nil {
			return err
		}

		// GossipingPropertyFileSnitch config
		if err := addCassandraGPFSnitchProperties(cdc, configMap, volumeSource); err != nil {
			return err
		}

		// Prometheus support
		if cdc.Spec.PrometheusSupport {
			configMapVolumeAddTextFile(configMap, volumeSource, "cassandra-env.sh.d/001-cassandra-exporter.sh",
				"JVM_OPTS=\"${JVM_OPTS} -javaagent:${CASSANDRA_HOME}/agents/cassandra-exporter-agent.jar=@${CASSANDRA_CONF}/cassandra-exporter.conf\"")
		}

		// tune ulimits
		// unlimited locked memory
		// TODO: move this into the image -- it should be the default
		// TODO: other limits too?
		configMapVolumeAddTextFile(configMap, volumeSource, "cassandra-env.sh.d/002-cassandra-limits.sh",
			"ulimit -l unlimited\n")

		// JVM options
		if err := addCassandraJVMOptions(cdc, configMap, volumeSource); err != nil {
			return err
		}

		if err := controllerutil.SetControllerReference(cdc, configMap, r.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	volumeMount := &VolumeMount{
		Volume: corev1.Volume{
			Name:         "operator-config-volume",
			VolumeSource: corev1.VolumeSource{ConfigMap: volumeSource},
		},
		MountPath: "/tmp/operator-config",
	}

	return volumeMount, nil
}

func addCassandraYamlOverrides(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, seedNodesService *corev1.Service, configMap *corev1.ConfigMap, volumeSource *corev1.ConfigMapVolumeSource) error {
	type SeedProvider struct {
		ClassName  string              `yaml:"class_name"`
		Parameters []map[string]string `yaml:"parameters"`
	}

	type CassandraConfig struct {
		ClusterName   string  `yaml:"cluster_name"`
		ListenAddress *string `yaml:"listen_address"`
		RPCAddress    *string `yaml:"rpc_address"`

		SeedProvider []SeedProvider `yaml:"seed_provider"`

		EndpointSnitch string `yaml:"endpoint_snitch"`
	}

	data, err := yaml.Marshal(&CassandraConfig{
		ClusterName: cdc.Spec.Cluster,

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

		EndpointSnitch: "org.apache.cassandra.locator.GossipingPropertyFileSnitch", // TODO: custom snitch implementation?
	})

	if err != nil {
		return err
	}

	configMapVolumeAddTextFile(configMap, volumeSource, "cassandra.yaml.d/001-operator-overrides.yaml", string(data))

	return nil
}

func addCassandraGPFSnitchProperties(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, configMap *corev1.ConfigMap, volumeSource *corev1.ConfigMapVolumeSource) error {
	var writer strings.Builder

	_, _ = fmt.Fprintln(&writer, "# generated by cassandra-operator") // min heap size

	writeProperty := func(key string, value string) {
		_, _ = fmt.Fprintf(&writer, "%s=%s\n", key, value)
	}

	writeProperty("dc", cdc.Name)
	writeProperty("rack", "rack1")
	writeProperty("prefer_local", "true")

	configMapVolumeAddTextFile(configMap, volumeSource, "cassandra-rackdc.properties", writer.String())

	return nil
}

// is this seriously not built-in?
func min(a int64, b int64) int64 {
	if a < b {
		return a
	}
	return b
}

func max(a int64, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

const MEBIBYTE = 1 << 20
const GIBIBYTE = 1 << 30

func addCassandraJVMOptions(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, configMap *corev1.ConfigMap, volumeSource *corev1.ConfigMapVolumeSource) error {
	coreCount := int64(4) // TODO

	// TODO: should this be Limits or Requests?
	memoryLimit := cdc.Spec.Resources.Limits.Memory().Value() // Value() gives bytes

	jvmHeapSize := max(
		min(memoryLimit/2, GIBIBYTE),
		min(memoryLimit/4, 8*GIBIBYTE))

	youngGenSize := min(
		coreCount*MEBIBYTE,
		jvmHeapSize/4)

	useG1GC := jvmHeapSize > 8*GIBIBYTE

	var writer strings.Builder

	_, _ = fmt.Fprintf(&writer, "-Xms%d\n", jvmHeapSize) // min heap size
	_, _ = fmt.Fprintf(&writer, "-Xmx%d\n", jvmHeapSize) // max heap size

	// copied from stock jvm.options
	if !useG1GC {
		_, _ = fmt.Fprintf(&writer, "-Xmn%d\n", youngGenSize) // young gen size

		_, _ = fmt.Fprintln(&writer, "-XX:+UseParNewGC")
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

	configMapVolumeAddTextFile(configMap, volumeSource, "jvm.options.d/001-jvm-memory-gc.options", writer.String())

	return nil
}

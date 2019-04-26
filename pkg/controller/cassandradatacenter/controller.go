package cassandradatacenter

import (
	"context"
	"k8s.io/api/apps/v1beta2"
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
	err := r.client.Get(context.TODO(), request.NamespacedName, cdc)
	if err != nil {
		if errors.IsNotFound(err) {
			// Request object not found, could have been deleted after reconcile request.
			// Owned objects are automatically garbage collected. For additional cleanup logic use finalizers.
			// Return and don't requeue
			return reconcile.Result{}, nil
		}
		// Error reading the object - requeue the request.
		return reconcile.Result{}, err
	}

	nodesService, err := r.createOrUpdateNodesService(cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	seedNodesService, err := r.createOrUpdateSeedNodesService(cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	configMapVolumeMount, err := r.createOrUpdateOperatorConfigMap(cdc)

	statefulSet, err := r.createOrUpdateStatefulSet(cdc, []*VolumeMount{configMapVolumeMount})
	if err != nil {
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet


	return reconcile.Result{}, nil
}

func dataCenterLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		"cassandra-operator.instaclustr.com/datacenter": cdc.Name,
		"app.kubernetes.io/managed-by": "com.instaclustr.cassandra-operator",
	}
}

func dataCenterResourceMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	return metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name: "cassandra-" + cdc.Name + suffix,
		Labels: dataCenterLabels(cdc),
	}
}

func (r *CassandraDataCenterReconciler) createOrUpdateNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {
	nodesService := &corev1.Service{ObjectMeta: dataCenterResourceMetadata(cdc, "nodes"),}

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

		return nil
	})
	if err != nil {
		return nil, err
	}

	if err := controllerutil.SetControllerReference(cdc, nodesService, r.scheme); err != nil {
		return nil, err
	}

	return nodesService, err
}

func (r *CassandraDataCenterReconciler) createOrUpdateSeedNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {
	seedNodesService := &corev1.Service{ObjectMeta: dataCenterResourceMetadata(cdc, "seeds"),}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, seedNodesService, func(_ runtime.Object) error {
		seedNodesService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports: []corev1.ServicePort{
				{
					Name:"internode",
					Port:7000,
				},
			},
			Selector: dataCenterLabels(cdc),
			PublishNotReadyAddresses: true,
		}

		return nil
	})
	if err != nil {
		return nil, err
	}

	if err := controllerutil.SetControllerReference(cdc, seedNodesService, r.scheme); err != nil {
		return nil, err
	}

	return seedNodesService, err
}

func (r *CassandraDataCenterReconciler) createOrUpdateStatefulSet(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumeMounts []*VolumeMount) (*v1beta2.StatefulSet, error) {
	dataVolumeClaim := corev1.PersistentVolumeClaim{
		ObjectMeta: metav1.ObjectMeta{Name:"data-volume"},
		Spec:       cdc.Spec.DataVolumeClaimSpec,
	}


	volumeMounts = append(volumeMounts,
		// Downward API pod info volume
		&VolumeMount{
			Volume:corev1.Volume{
				Name: "pod-info",
				VolumeSource: corev1.VolumeSource{
					DownwardAPI: &corev1.DownwardAPIVolumeSource{
						Items: []corev1.DownwardAPIVolumeFile{
							{Path:"labels", FieldRef:&corev1.ObjectFieldSelector{FieldPath:"metadata.labels"},},
							{Path:"annotations", FieldRef:&corev1.ObjectFieldSelector{FieldPath:"metadata.annotations"},},
							{Path:"namespace", FieldRef:&corev1.ObjectFieldSelector{FieldPath:"metadata.namespace"},},
							{Path:"name", FieldRef:&corev1.ObjectFieldSelector{FieldPath:"metadata.name"},},
						},
					},
				},
			},
			MountPath:"/etc/pod-info",
		},
	)

	cassandraContainer := corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports: []corev1.ContainerPort{
			{Name: "internode", ContainerPort: 7000,},
			{Name: "cql", ContainerPort: 9042,},
			{Name: "jmx", ContainerPort: 7199,},
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
			{
				Name:      dataVolumeClaim.Name,
				MountPath: "/var/lib/cassandra",
			},
		},
	}

	if cdc.Spec.PrometheusSupport == true {
		cassandraContainer.Ports = append(cassandraContainer.Ports, corev1.ContainerPort{
			Name:"promql",
			ContainerPort:9500,
		})
	}

	sidecarContainer := corev1.Container{
		Name:            "sidecar",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports: []corev1.ContainerPort{
			{Name: "http", ContainerPort: 4567,},
		},
		VolumeMounts: []corev1.VolumeMount{
			{
				Name:      dataVolumeClaim.Name,
				MountPath: "/var/lib/cassandra",
			},
		},
	}

	podSpec := corev1.PodSpec{
		Containers: []corev1.Container{cassandraContainer, sidecarContainer},
		Volumes: []corev1.Volume{},
		ImagePullSecrets:cdc.Spec.ImagePullSecrets,
	}

	// add all Volumes and their VolumeMounts
	for _, vm := range volumeMounts {
		cassandraContainer.VolumeMounts = append(cassandraContainer.VolumeMounts, corev1.VolumeMount{
			Name:vm.Volume.Name,
			MountPath:vm.MountPath,
		})

		podSpec.Volumes = append(podSpec.Volumes, vm.Volume)
	}

	statefulSet := &v1beta2.StatefulSet{ObjectMeta: dataCenterResourceMetadata(cdc),}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, statefulSet, func(_ runtime.Object) error {
		if statefulSet.Spec.Replicas != nil && *statefulSet.Spec.Replicas != cdc.Spec.Replicas {
			// TODO: scale safely

		}

		podLabels := dataCenterLabels(cdc)

		statefulSet.Spec = v1beta2.StatefulSetSpec{
			ServiceName: "cassandra",
			Replicas:    &cdc.Spec.Replicas,
			Selector:    &metav1.LabelSelector{MatchLabels:podLabels},
			Template: corev1.PodTemplateSpec{
				ObjectMeta: metav1.ObjectMeta{Labels:podLabels,},
				Spec: podSpec,
			},
			VolumeClaimTemplates: []corev1.PersistentVolumeClaim{dataVolumeClaim,},
		}

		return nil
	})
	if err != nil {
		return nil, err
	}

	if err := controllerutil.SetControllerReference(cdc, statefulSet, r.scheme); err != nil {
		return nil, err
	}

	return statefulSet, err
}

//func addFile

func (r *CassandraDataCenterReconciler) createOrUpdateOperatorConfigMap(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*VolumeMount, error) {
	configMap := &corev1.ConfigMap{ObjectMeta: dataCenterResourceMetadata(cdc, "operator-config"),}

	volumeSource := &corev1.ConfigMapVolumeSource{LocalObjectReference:corev1.LocalObjectReference{Name: configMap.Name}}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), r.client, configMap, func(_ runtime.Object) error {
		configMap.Data = map[string]string{
			"test": "echo hello world; exit 1",
		}

		volumeSource.Items = append(volumeSource.Items, corev1.KeyToPath{Key: "test", Path: "cassandra-env.sh.d/001-cassandra-exporter.sh"})

		// cassandra.yaml overrides
		{

		}

		// GossipingPropertyFileSnitch config


		return nil
	})

	if err != nil {
		return nil, err
	}

	volumeMount := &VolumeMount{
		Volume: corev1.Volume{
			Name: "operator-config-volume",
			VolumeSource:corev1.VolumeSource{ConfigMap:volumeSource},
		},
		MountPath: "/tmp/operator-config",
	}

	return volumeMount, nil
}
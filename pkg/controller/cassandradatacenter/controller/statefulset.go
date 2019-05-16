package controller

import (
	"context"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"k8s.io/api/apps/v1beta2"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

const (
	SidecarPort                                         = 4567
	SidecarContainerVolumeMountPath                     = "/var/lib/cassandra"
	SidecarContainerPodInfoVolumeMountPath              = "/etc/pod-info"
	CassandraContainerCqlReadinessProbeScriptPath       = "/usr/bin/cql-readiness-probe"
	CassandraContainerVolumeMountPath                   = "/var/lib/cassandra"
	CassandraReadinessProbeInitialDelayInSeconds        = 60
	CassandraReadinessProbeInitialDelayTimeoutInSeconds = 5
)

func CreateOrUpdateStatefulSet(reconciler *CassandraDataCenterReconciler, cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumeMounts VolumeMounts) (*v1beta2.StatefulSet, error) {

	dataVolumeClaim := getDataVolumeClaim(cdc.Spec.DataVolumeClaimSpec)

	podInfoVolume := getPodInfoVolume()

	cassandraContainer := newCassandraContainer(cdc, dataVolumeClaim)
	sidecarContainer := newSidecarContainer(cdc, dataVolumeClaim, podInfoVolume)

	addMountsToCassandra(cassandraContainer, volumeMounts)
	addMountsToSidecar(sidecarContainer, volumeMounts)

	podSpec := getPodSpec(cdc, podInfoVolume, []corev1.Container{*cassandraContainer, *sidecarContainer})
	addMountsToPodSpec(podSpec, volumeMounts)

	statefulSet := &v1beta2.StatefulSet{ObjectMeta: DataCenterResourceMetadata(cdc)}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), reconciler.client, statefulSet, func(_ runtime.Object) error {
		if statefulSet.Spec.Replicas != nil && *statefulSet.Spec.Replicas != cdc.Spec.Nodes {
			// TODO: scale safely
		}

		statefulSet.Spec = getStatefulSetSpec(cdc, podSpec, dataVolumeClaim)

		if err := controllerutil.SetControllerReference(cdc, statefulSet, reconciler.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	return statefulSet, err
}

func getStatefulSetSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, podSpec *corev1.PodSpec, dataVolumeClaim *corev1.PersistentVolumeClaim) v1beta2.StatefulSetSpec {

	podLabels := DataCenterLabels(cdc)

	return v1beta2.StatefulSetSpec{
		ServiceName: "cassandra",
		Replicas:    &cdc.Spec.Nodes,
		Selector:    &metav1.LabelSelector{MatchLabels: podLabels},
		Template: corev1.PodTemplateSpec{
			ObjectMeta: metav1.ObjectMeta{Labels: podLabels},
			Spec:       *podSpec,
		},
		VolumeClaimTemplates: []corev1.PersistentVolumeClaim{*dataVolumeClaim},
	}
}

func getPodSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, podInfoVolume *corev1.Volume, containers []corev1.Container) *corev1.PodSpec {

	podSpec := &corev1.PodSpec{
		Volumes:          []corev1.Volume{*podInfoVolume},
		ImagePullSecrets: cdc.Spec.ImagePullSecrets,
	}

	// this is a deep copy
	podSpec.Containers = containers

	return podSpec
}

func newCassandraContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim) *corev1.Container {
	container := &corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Args:            []string{},
		Ports: []corev1.ContainerPort{
			{Name: "internode", ContainerPort: CassandraInternodePort},
			{Name: "cql", ContainerPort: CassandraCQLPort},
			{Name: "jmx", ContainerPort: CassandraJMXPort},
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
					Command: []string{CassandraContainerCqlReadinessProbeScriptPath},
				},
			},
			InitialDelaySeconds: CassandraReadinessProbeInitialDelayInSeconds,
			TimeoutSeconds:      CassandraReadinessProbeInitialDelayTimeoutInSeconds,
		},
		VolumeMounts: []corev1.VolumeMount{
			{Name: dataVolumeClaim.Name, MountPath: CassandraContainerVolumeMountPath},
		},
	}

	if cdc.Spec.PrometheusSupport == true {
		container.Ports = append(container.Ports, corev1.ContainerPort{Name: "promql", ContainerPort: CassandraPrometheusPort})
	}

	return container
}

func addMountsToCassandra(cassandraContainer *corev1.Container, volumeMounts VolumeMounts) {
	for _, vm := range volumeMounts {
		cassandraContainer.VolumeMounts = append(cassandraContainer.VolumeMounts, corev1.VolumeMount{
			Name:      vm.Volume.Name,
			MountPath: vm.MountPath,
		})

		// entrypoint takes mount paths as arguments
		cassandraContainer.Args = append(cassandraContainer.Args, vm.MountPath)
	}
}

func addMountsToSidecar(sidecarContainer *corev1.Container, volumeMounts VolumeMounts) {
	for _, vm := range volumeMounts {
		// provide access to config map volumes in the sidecar, these reside in /tmp though and are not overlayed into /etc/cassandra
		// TODO: rework this
		sidecarContainer.VolumeMounts = append(sidecarContainer.VolumeMounts, corev1.VolumeMount{
			Name:      vm.Volume.Name,
			MountPath: vm.MountPath,
		})
	}
}

func addMountsToPodSpec(podSpec *corev1.PodSpec, volumeMounts VolumeMounts) {
	for _, vm := range volumeMounts {
		podSpec.Volumes = append(podSpec.Volumes, vm.Volume)
	}
}

func newSidecarContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, podInfoVolume *corev1.Volume) *corev1.Container {

	return &corev1.Container{
		Name:            "sidecar",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports: []corev1.ContainerPort{
			{Name: "http", ContainerPort: SidecarPort},
		},
		VolumeMounts: []corev1.VolumeMount{
			{Name: dataVolumeClaim.Name, MountPath: SidecarContainerVolumeMountPath},
			{Name: podInfoVolume.Name, MountPath: SidecarContainerPodInfoVolumeMountPath},
		},
	}
}

func getPodInfoVolume() *corev1.Volume {
	return &corev1.Volume{
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
}

func getDataVolumeClaim(dataVolumeClaimSpec corev1.PersistentVolumeClaimSpec) *corev1.PersistentVolumeClaim {
	return &corev1.PersistentVolumeClaim{
		ObjectMeta: metav1.ObjectMeta{Name: "data-volume"},
		Spec:       dataVolumeClaimSpec,
	}
}

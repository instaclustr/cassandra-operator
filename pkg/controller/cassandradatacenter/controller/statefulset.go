package controller

import (
	"context"
	"fmt"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	//"strings"
	"sync"
)


const DataVolumeMountPath = "/var/lib/cassandra"

const SidecarApiPort = 4567

var sidecarClientOptions = sidecar.ClientOptions{
	Port:   SidecarApiPort,
	Secure: false,
}

func createOrUpdateStatefulSet(rctx *reconciliationRequestContext, configVolume *corev1.Volume) (*v1beta2.StatefulSet, error) {
	statefulSet := &v1beta2.StatefulSet{ObjectMeta: DataCenterResourceMetadata(rctx.cdc)}

	logger := rctx.logger.WithValues("StatefulSet.Name", statefulSet.Name)

	result, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, statefulSet, func(obj runtime.Object) error {

		if err := controllerutil.SetControllerReference(rctx.cdc, statefulSet, rctx.scheme); err != nil {
			return err
		}

		dataVolumeClaim := newDataVolumeClaim(&rctx.cdc.Spec.DataVolumeClaimSpec)

		podInfoVolume := newPodInfoVolume()

		cassandraContainer := newCassandraContainer(rctx.cdc, dataVolumeClaim, configVolume)
		sidecarContainer := newSidecarContainer(rctx.cdc, dataVolumeClaim, podInfoVolume)

		sysctlLimitsContainer := newSysctlLimitsContainer(rctx.cdc)

		podSpec := newPodSpec(rctx.cdc,
			[]corev1.Volume{*podInfoVolume, *configVolume},
			[]corev1.Container{*cassandraContainer, *sidecarContainer},
			[]corev1.Container{*sysctlLimitsContainer})

		statefulSetSpec := newStatefulSetSpec(rctx.cdc, podSpec, dataVolumeClaim)

		if statefulSet.CreationTimestamp.IsZero() {
			// creating a new StatefulSet -- just set the Spec and we're done
			statefulSet.Spec = *statefulSetSpec
			return nil
		}
		
		//if err := scaleStatefulSet(rctx, statefulSet, statefulSetSpec); err != nil {
		//	return err
		//}

		return nil
	})

	if err != nil {
		return nil, err
	}

	logger.Info(fmt.Sprintf("StatefulSet %s", result))

	return statefulSet, err
}


func newStatefulSetSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, podSpec *corev1.PodSpec, dataVolumeClaim *corev1.PersistentVolumeClaim) *v1beta2.StatefulSetSpec {
	podLabels := DataCenterLabels(cdc)

	return &v1beta2.StatefulSetSpec{
		ServiceName: "cassandra", // TODO: correct service name? this service should already exist (apparently)
		Replicas:    &cdc.Spec.Nodes,
		Selector:    &metav1.LabelSelector{MatchLabels: podLabels},
		Template: corev1.PodTemplateSpec{
			ObjectMeta: metav1.ObjectMeta{Labels: podLabels},
			Spec:       *podSpec,
		},
		VolumeClaimTemplates: []corev1.PersistentVolumeClaim{*dataVolumeClaim},
		PodManagementPolicy: v1beta2.OrderedReadyPodManagement,
	}
}

func newPodSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumes []corev1.Volume, containers []corev1.Container, initContainers []corev1.Container) *corev1.PodSpec {
	// TODO: should this spec be fully exposed into the CDC.Spec?
	podSpec := &corev1.PodSpec{
		Volumes: volumes,
		Containers: containers,
		InitContainers: initContainers,
		ImagePullSecrets: cdc.Spec.ImagePullSecrets,
	}

	return podSpec
}

func newCassandraContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, configVolume *corev1.Volume) *corev1.Container {
	const OperatorConfigVolumeMountPath = "/tmp/operator-config"

	container := &corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Args:            []string{OperatorConfigVolumeMountPath},
		Ports: []corev1.ContainerPort{
			{Name: "internode", ContainerPort: 7000},
			{Name: "cql", ContainerPort: 9042},
			{Name: "jmx", ContainerPort: 7199},
		},
		Resources: cdc.Spec.Resources,
		SecurityContext: &corev1.SecurityContext{
			Capabilities: &corev1.Capabilities{
				Add: []corev1.Capability{
					"IPC_LOCK", // C* wants to mlock/mlockall
					"SYS_RESOURCE", // permit ulimit adjustments
				},
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
			{Name: dataVolumeClaim.Name, MountPath: DataVolumeMountPath},
			{Name: configVolume.Name, MountPath: OperatorConfigVolumeMountPath},
		},
	}

	if cdc.Spec.PrometheusSupport == true {
		container.Ports = append(container.Ports, corev1.ContainerPort{Name: "promql", ContainerPort: 9500})
	}

	return container
}


func newSidecarContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, podInfoVolume *corev1.Volume) *corev1.Container {
	return &corev1.Container{
		Name:            "sidecar",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports: []corev1.ContainerPort{
			{Name: "http", ContainerPort: SidecarApiPort},
		},
		VolumeMounts: []corev1.VolumeMount{
			{Name: dataVolumeClaim.Name, MountPath: DataVolumeMountPath},
			{Name: podInfoVolume.Name, MountPath: "/etc/pod-info"},
		},
	}
}

func newSysctlLimitsContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) *corev1.Container {
	return &corev1.Container{
		Name:            "sysctl-limits",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		SecurityContext: &corev1.SecurityContext{
			Privileged: func () *bool { b := true; return &b }(),
		},
		Command: []string{"bash", "-xuec"},
		Args: []string{
			`sysctl -w vm.max_map_count=1048575 || true`,
		},
	}
}

func newPodInfoVolume() *corev1.Volume {
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

func newDataVolumeClaim(dataVolumeClaimSpec *corev1.PersistentVolumeClaimSpec) *corev1.PersistentVolumeClaim {
	return &corev1.PersistentVolumeClaim{
		ObjectMeta: metav1.ObjectMeta{Name: "data-volume"},
		Spec:       *dataVolumeClaimSpec,
	}
}

//func scaleStatefulSet(rctx *reconciliationRequestContext, existingStatefulSet *v1beta2.StatefulSet, newStatefulSetSpec *v1beta2.StatefulSetSpec) error {
//
//
//	var existingSpecReplicas int32
//
//	if existingStatefulSet.Spec.Replicas == nil {
//		existingSpecReplicas = 0
//	} else {
//		existingSpecReplicas = *existingStatefulSet.Spec.Replicas
//	}
//
//	desiredSpecReplicas := rctx.cdc.Spec.Nodes
//
//	currentReplicas := existingStatefulSet.Status.Replicas // number of created pods
//
//	if currentReplicas != existingSpecReplicas {
//			log.Info("skipping StatefulSet reconciliation as it is undergoing scaling operations", "current", currentReplicas, "existing", existingSpecReplicas)
//			return nil
//		}
//
//	allPods, err := existingPods(rctx.client, rctx.cdc)
//
//	if err != nil {
//		return errors.New("unable to list pods")
//	}
//
//	if allRun, notRunningPods := allPodsAreRunning(allPods); allRun == false && len(notRunningPods) > 0 {
//		log.Info("Skipping reconciliation as some pods are not running yet: " + strings.Join(notRunningPods, " "))
//		return nil
//	}
//
//	clients := sidecar.SidecarClients(allPods, sidecar.DefaultSidecarClientOptions)
//	statuses := cassandraStatuses(clients)
//
//	if erroredCassandras := errorneousCassandras(statuses); len(erroredCassandras) > 0 {
//		return errors.New("skipping StatefulSet reconciliation as the status of some Cassandra nodes could not be determined")
//	}
//
//	if existingStatefulSet.Spec.Replicas != nil {
//		podsInIncorrectCassandraState := podsInIncorrectCassandraState(cdc.Spec.Nodes, existingSpecReplicas, statuses)
//
//		if len(podsInIncorrectCassandraState) > 0 {
//			log.Info("skipping StatefulSet reconciliation as some Cassandra Pods are not in the correct mode: " + strings.Join(podsInIncorrectCassandraState, ","))
//			return nil
//		}
//	}
//
//	if desiredSpecReplicas > existingSpecReplicas {
//			cdc.Spec.Nodes = existingSpecReplicas + 1
//			return setControllerReference(statefulSet, cdc, podSpec, dataVolumeClaim, reconciler)
//		} else if desiredSpecReplicas < existingSpecReplicas {
//
//			newestPod := allPods[len(allPods)-1]
//
//			decommissioned := decommissionedCassandras(statuses)
//
//			if len(decommissioned) == 0 {
//
//				log.Info("No Cassandra nodes have been decommissioned. Decommissioning the newest one " + newestPod.Name)
//
//				if clientForNewestPod := sidecar.ClientFromPods(clients, newestPod); clientForNewestPod != nil {
//					if _, err := clientForNewestPod.DecommissionNode(); err != nil {
//						return errors.New(fmt.Sprintf("Unable to decommission node %s: %v", newestPod.Name, err))
//					}
//				} else {
//					return errors.New(fmt.Sprintf("Client for pod %s to decommission does not exist.", newestPod.Name))
//				}
//			} else if len(decommissioned) == 1 {
//
//				log.Info("Decommissioned Cassandra node found. Scaling StatefulSet down.")
//
//				if decommissioned[0].Name != newestPod.Name {
//					return errors.New("skipping StatefulSet reconciliation as the DataCenter contains one decommissioned Cassandra node, but it is not the newest, " +
//						"decommissioned pod: " + decommissioned[0].Name + ", expecting Pod" + newestPod.Name)
//				}
//
//				cdc.Spec.Nodes = existingSpecReplicas - 1
//			} else {
//				return errors.New("skipping StatefulSet reconciliation as the DataCenter contains more than one decommissioned Cassandra node: " +
//					strings.Join(podsToString(decommissioned), ","))
//			}
//		} else {
//			log.Info("Replaced namespaced StatefulSet.")
//		}
//
//	return existingStatefulSet
//}

// helpers

func existingPods(c client.Client, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) ([]corev1.Pod, error) {

	podList := corev1.PodList{}

	listOps := &client.ListOptions{
		Namespace:     cdc.Namespace,
		LabelSelector: labels.SelectorFromSet(DataCenterLabels(cdc)),
	}

	if err := c.List(context.TODO(), listOps, &podList); err != nil {
		return nil, err
	}

	return podList.Items, nil
}

func allPodsAreRunning(pods []corev1.Pod) (bool, []string) {

	podsNotInRunningPhase := make([]corev1.Pod, 0)

	for _, pod := range pods {
		if pod.Status.Phase != "Running" {
			podsNotInRunningPhase = append(podsNotInRunningPhase, pod)
		}
	}

	var notRunningPodNames []string

	if len(podsNotInRunningPhase) > 0 {

		for _, p := range podsNotInRunningPhase {
			notRunningPodNames = append(notRunningPodNames, p.GetObjectMeta().GetName())
		}

		return false, notRunningPodNames
	}

	return true, notRunningPodNames
}

func cassandraStatuses(podClients map[*corev1.Pod]*sidecar.Client) map[*corev1.Pod]sidecar.OperationMode {

	podByOperationMode := make(map[*corev1.Pod]sidecar.OperationMode)

	var wg sync.WaitGroup

	wg.Add(len(podClients))

	for pod, c := range podClients {
		go func(pod *corev1.Pod, client *sidecar.Client) {
			if response, err := client.GetStatus(); err != nil {
				podByOperationMode[pod] = sidecar.OPERATION_MODE_ERROR
			} else {
				podByOperationMode[pod] = response.OperationMode
			}

			wg.Done()
		}(pod, c)
	}

	wg.Wait()

	return podByOperationMode
}

func cassandrasInState(statuses map[*corev1.Pod]sidecar.OperationMode, state sidecar.OperationMode) []*corev1.Pod {

	var cassandrasInState []*corev1.Pod

	for pod, status := range statuses {
		if status == state {
			cassandrasInState = append(cassandrasInState, pod)
		}
	}

	return cassandrasInState
}

func errorneousCassandras(statuses map[*corev1.Pod]sidecar.OperationMode) []*corev1.Pod {
	return cassandrasInState(statuses, sidecar.OPERATION_MODE_ERROR)
}

func decommissionedCassandras(statuses map[*corev1.Pod]sidecar.OperationMode) []*corev1.Pod {
	return cassandrasInState(statuses, sidecar.OPERATION_MODE_DECOMMISSIONED)
}

func podsInIncorrectCassandraState(desiredReplicas int32, existingReplicas int32, statuses map[*corev1.Pod]sidecar.OperationMode) []string {

	scaleUpOperationModes := []sidecar.OperationMode{sidecar.OPERATION_MODE_NORMAL}
	scaleDownOperationModes := []sidecar.OperationMode{sidecar.OPERATION_MODE_NORMAL, sidecar.OPERATION_MODE_DECOMMISSIONED}

	var podsByIncorrectCassandraMode []string

	for pod, status := range statuses {
		if desiredReplicas >= existingReplicas {
			if !contains(scaleUpOperationModes, status) {
				podsByIncorrectCassandraMode = append(podsByIncorrectCassandraMode, pod.ObjectMeta.Name)
			}
		} else if !contains(scaleDownOperationModes, status) {
			podsByIncorrectCassandraMode = append(podsByIncorrectCassandraMode, pod.ObjectMeta.Name)
		}
	}

	return podsByIncorrectCassandraMode
}

func podsToString(pods []*corev1.Pod) []string {

	podNames := make([]string, 0)

	for _, pod := range pods {
		podNames = append(podNames, pod.ObjectMeta.Name)
	}

	return podNames
}

// go does not have this built-in
func contains(a []sidecar.OperationMode, x sidecar.OperationMode) bool {
	for _, n := range a {
		if x == n {
			return true
		}
	}
	return false
}

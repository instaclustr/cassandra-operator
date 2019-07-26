package cassandradatacenter

import (
	"context"
	"errors"
	"fmt"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/nodestate"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"strings"
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

		return scaleStatefulSet(rctx, statefulSet, statefulSetSpec)
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
		PodManagementPolicy:  v1beta2.OrderedReadyPodManagement,
	}
}

func newPodSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumes []corev1.Volume, containers []corev1.Container, initContainers []corev1.Container) *corev1.PodSpec {
	// TODO: should this spec be fully exposed into the CDC.Spec?
	podSpec := &corev1.PodSpec{
		Volumes:          volumes,
		Containers:       containers,
		InitContainers:   initContainers,
		ImagePullSecrets: cdc.Spec.ImagePullSecrets,
	}

	return podSpec
}

func newCassandraContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, configVolume *corev1.Volume) *corev1.Container {
	const (
		OperatorConfigVolumeMountPath = "/tmp/operator-config"
		UserConfigVolumePath = "/etc/cassandra/cassandra.yaml.d/003-user-overrides.yaml"
	)

	container := &corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Args:            []string{OperatorConfigVolumeMountPath, UserConfigVolumePath},
		Ports: []corev1.ContainerPort{
			{Name: "internode", ContainerPort: 7000},
			{Name: "cql", ContainerPort: 9042},
			{Name: "jmx", ContainerPort: 7199},
		},
		Resources: cdc.Spec.Resources,
		SecurityContext: &corev1.SecurityContext{
			Capabilities: &corev1.Capabilities{
				Add: []corev1.Capability{
					"IPC_LOCK",     // C* wants to mlock/mlockall
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

	if cdc.Spec.UserConfigMap.Size() != 0 {
		container.VolumeMounts = append(container.VolumeMounts,	corev1.VolumeMount{Name: cdc.Spec.UserConfigMap.Name, MountPath: UserConfigVolumePath})
	}

	if cdc.Spec.PrometheusSupport == true {
		container.Ports = append(container.Ports, corev1.ContainerPort{Name: "promql", ContainerPort: 9500})
	}

	return container
}

func newSidecarContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, podInfoVolume *corev1.Volume) *corev1.Container {
	container := &corev1.Container{
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

	if len(cdc.Spec.BackupSecrets) != 0 {
		container.EnvFrom = []corev1.EnvFromSource{
			{SecretRef: &corev1.SecretEnvSource{LocalObjectReference: corev1.LocalObjectReference{Name: cdc.Spec.BackupSecrets}}},
		}
	}

	return container
}

func newSysctlLimitsContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) *corev1.Container {
	return &corev1.Container{
		Name:            "sysctl-limits",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		SecurityContext: &corev1.SecurityContext{
			Privileged: func() *bool { b := true; return &b }(),
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

func scaleStatefulSet(rctx *reconciliationRequestContext, existingStatefulSet *v1beta2.StatefulSet, newStatefulSetSpec *v1beta2.StatefulSetSpec) error {

	var (
		existingSpecReplicas, // number of replicas set in the current spec
		currentReplicas, // currently running replicas
		desiredSpecReplicas int32 // the new requested spec
	)

	// Get all replicas numbers
	desiredSpecReplicas = rctx.cdc.Spec.Nodes
	currentReplicas = existingStatefulSet.Status.Replicas
	if existingStatefulSet.Spec.Replicas != nil {
		existingSpecReplicas = *existingStatefulSet.Spec.Replicas
	}
	// TODO: do we even need to do/log anything if we're good? if not, maybe even not get here at all
	if desiredSpecReplicas == existingSpecReplicas {
		log.Info("Replaced namespaced StatefulSet.")
		return nil
	}

	// Get pods, clients and statuses map
	allPods, err := ExistingPods(rctx.client, rctx.cdc)
	if err != nil {
		return errors.New("unable to list pods")
	}
	clients := sidecar.SidecarClients(allPods, &sidecar.DefaultSidecarClientOptions)
	statuses := cassandraStatuses(clients)
	decommissionedNodes := decommissionedCassandras(statuses)

	// Check the current replicas/pod/cassandra state
	if valid, err := checkState(existingSpecReplicas, currentReplicas, desiredSpecReplicas, allPods, statuses); !valid {
		return err
	}

	// Scale
	if desiredSpecReplicas > existingSpecReplicas {

		// Scale up
		existingStatefulSet.Spec = *newStatefulSetSpec
		return controllerutil.SetControllerReference(rctx.cdc, existingStatefulSet, rctx.scheme)

	} else if desiredSpecReplicas < existingSpecReplicas {

		// Scale down
		newestPod := allPods[len(allPods)-1]
		if len(decommissionedNodes) == 0 {
			log.Info("No Cassandra nodes have been decommissioned. Decommissioning the newest one " + newestPod.Name)
			if clientForNewestPod := sidecar.ClientFromPods(clients, newestPod); clientForNewestPod != nil {
				if _, err := clientForNewestPod.StartOperation(&sidecar.DecommissionRequest{}); err != nil {
					return errors.New(fmt.Sprintf("Unable to decommission node %s: %v", newestPod.Name, err))
				}
			} else {
				return errors.New(fmt.Sprintf("Client for pod %s to decommission does not exist.", newestPod.Name))
			}
		} else if len(decommissionedNodes) == 1 {
			log.Info("Decommissioned Cassandra node found. Scaling StatefulSet down.")
			if decommissionedNodes[0].Name != newestPod.Name {
				return errors.New("skipping StatefulSet reconciliation as the DataCenter contains one decommissionedNodes Cassandra node, but it is not the newest, " +
					"decommissionedNodes pod: " + decommissionedNodes[0].Name + ", expecting Pod" + newestPod.Name)
			}

			existingStatefulSet.Spec = *newStatefulSetSpec
		} else {
			// TODO: are we sure about this? Even having 2 decommissioned nodes should be fine for reconciling the stateful set
			return errors.New("skipping StatefulSet reconciliation as the DataCenter contains more than one decommissionedNodes Cassandra node: " +
				strings.Join(podsToString(decommissionedNodes), ","))
		}
	}

	return nil
}

// helpers

func ExistingPods(c client.Client, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) ([]corev1.Pod, error) {

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

	var notRunningPodNames []string

	for _, pod := range pods {
		if pod.Status.Phase != corev1.PodRunning {
			notRunningPodNames = append(notRunningPodNames, pod.Name)
		}
	}

	if len(notRunningPodNames) > 0 {
		return false, notRunningPodNames
	}

	return true, notRunningPodNames
}

func cassandraStatuses(podClients map[*corev1.Pod]*sidecar.Client) map[*corev1.Pod]nodestate.NodeState {

	podByOperationMode := make(map[*corev1.Pod]nodestate.NodeState)

	var wg sync.WaitGroup

	wg.Add(len(podClients))

	for pod, c := range podClients {
		go func(pod *corev1.Pod, client *sidecar.Client) {
			if response, err := client.Status(); err != nil {
				podByOperationMode[pod] = nodestate.ERROR
			} else {
				podByOperationMode[pod] = response.NodeState
			}

			wg.Done()
		}(pod, c)
	}

	wg.Wait()

	return podByOperationMode
}

func nodesInState(statuses map[*corev1.Pod]nodestate.NodeState, state nodestate.NodeState) []*corev1.Pod {

	var podsInState []*corev1.Pod

	for pod, status := range statuses {
		if status == state {
			podsInState = append(podsInState, pod)
		}
	}

	return podsInState
}

func decommissionedCassandras(statuses map[*corev1.Pod]nodestate.NodeState) []*corev1.Pod {
	return nodesInState(statuses, nodestate.DECOMMISSIONED)
}

func badPods(desiredReplicas int32, existingReplicas int32, statuses map[*corev1.Pod]nodestate.NodeState) map[string]nodestate.NodeState {

	scaleUpOperationModes := []nodestate.NodeState{nodestate.NORMAL}
	scaleDownOperationModes := []nodestate.NodeState{nodestate.NORMAL, nodestate.DECOMMISSIONED}

	opModes := scaleUpOperationModes
	if desiredReplicas < existingReplicas {
		opModes = scaleDownOperationModes
	}

	podsInBadState := make(map[string]nodestate.NodeState)
	for pod, status := range statuses {
		if !contains(opModes, status) {
			podsInBadState[pod.Name] = status
		}
	}

	return podsInBadState
}

func checkState(
	existingSpecReplicas, currentReplicas, desiredSpecReplicas int32,
	allPods []corev1.Pod,
	statuses map[*corev1.Pod]nodestate.NodeState) (valid bool, err error) {

	// check if current running # of pods match the current spec
	if currentReplicas != existingSpecReplicas {
		log.Info("skipping StatefulSet reconciliation as it is undergoing scaling operations", "current", currentReplicas, "existing", existingSpecReplicas)
		return false, nil
	}

	// check if all pods are in Running mode
	if allRun, notRunningPods := allPodsAreRunning(allPods); allRun == false {
		log.Info("Skipping reconciliation as some pods are not running yet: " + strings.Join(notRunningPods, " "))
		return false, nil
	}

	// check if all Cassandras are ok to scale
	if existingSpecReplicas > 0 {
		badPods := badPods(desiredSpecReplicas, existingSpecReplicas, statuses)
		if len(badPods) > 0 {
			log.Info("skipping StatefulSet reconciliation as some Cassandra Pods are not in the correct mode")
			for pod, status := range badPods {
				log.Info(fmt.Sprintf("Pod: %v, Status: %v", pod, status))
			}
			return false, nil
		}
	}

	return true, nil
}

func podsToString(pods []*corev1.Pod) []string {
	podNames := make([]string, 0)
	for _, pod := range pods {
		podNames = append(podNames, pod.Name)
	}
	return podNames
}

// go does not have this built-in
func contains(a []nodestate.NodeState, x nodestate.NodeState) bool {
	for _, n := range a {
		if x == n {
			return true
		}
	}
	return false
}

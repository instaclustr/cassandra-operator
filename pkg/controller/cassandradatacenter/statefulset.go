package cassandradatacenter

import (
	"context"
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"
	"github.com/instaclustr/cassandra-operator/pkg/common/nodestate"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	"k8s.io/api/apps/v1"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	errors2 "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
)

const (
	DataVolumeMountPath           = "/var/lib/cassandra"
	OperatorConfigVolumeMountPath = "/tmp/operator-config"
	RackConfigVolumeMountPath     = "/tmp/cassandra-rack-config"
	UserConfigVolumeMountPath     = "/tmp/user-config"
	UserSecretVolumeMountPath     = "/tmp/user-secret"
	BackupSecretVolumeMountPath   = "/tmp/backup-secret"
)

func createOrUpdateStatefulSet(rctx *reconciliationRequestContext, configVolume *corev1.Volume) (*v1beta2.StatefulSet, error) {

	// Find a rack to reconcile
	rack, err := findRackToReconcile(rctx)
	if rack == nil || err != nil {
		return nil, err
	}

	// Init rack-relevant info
	statefulSet := &v1beta2.StatefulSet{ObjectMeta: RackMetadata(rctx, rack)}
	logger := rctx.logger.WithValues("StatefulSet.Name", statefulSet.Name)

	result, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, statefulSet, func(obj runtime.Object) error {

		if err := controllerutil.SetControllerReference(rctx.cdc, statefulSet, rctx.scheme); err != nil {
			return err
		}

		dataVolumeClaim := newDataVolumeClaim(&rctx.cdc.Spec.DataVolumeClaimSpec)
		podInfoVolume := newPodInfoVolume()
		backupSecretVolume := newBackupSecretVolume(rctx)
		userSecretVolume := newUserSecretVolume(rctx)
		userConfigVolume := newUserConfigVolume(rctx)
		rackConfigVolume, err := createOrUpdateCassandraRackConfig(rctx, rack)
		if err != nil {
			return err
		}

		cassandraContainer := newCassandraContainer(rctx.cdc, dataVolumeClaim, configVolume, rackConfigVolume, userSecretVolume, userConfigVolume)
		sidecarContainer := newSidecarContainer(rctx.cdc, dataVolumeClaim, podInfoVolume, backupSecretVolume)

		sysctlLimitsContainer := newSysctlLimitsContainer(rctx.cdc)

		podSpec := newPodSpec(rctx.cdc, rack,
			[]corev1.Volume{*podInfoVolume, *configVolume, *rackConfigVolume},
			[]corev1.Container{*cassandraContainer, *sidecarContainer},
			[]corev1.Container{*sysctlLimitsContainer})

		if backupSecretVolume != nil {
			podSpec.Volumes = append(podSpec.Volumes, *backupSecretVolume)
		}

		if userSecretVolume != nil {
			podSpec.Volumes = append(podSpec.Volumes, *userSecretVolume)
		}

		if userConfigVolume != nil {
			podSpec.Volumes = append(podSpec.Volumes, *userConfigVolume)
		}

		statefulSetSpec := newStatefulSetSpec(rctx.cdc, podSpec, dataVolumeClaim, rack)

		return scaleStatefulSet(rctx, statefulSet, statefulSetSpec, rack)
	})

	if err != nil {
		return nil, err
	}

	logger.Info(fmt.Sprintf("StatefulSet %s", result))

	return statefulSet, err
}

func newStatefulSetSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, podSpec *corev1.PodSpec, dataVolumeClaim *corev1.PersistentVolumeClaim, rack *cluster.Rack) *v1beta2.StatefulSetSpec {
	podLabels := RackLabels(cdc, rack)
	return &v1beta2.StatefulSetSpec{
		ServiceName: "cassandra", // TODO: correct service name? this service should already exist (apparently)
		Replicas:    &rack.Replicas,
		Selector:    &metav1.LabelSelector{MatchLabels: podLabels},
		Template: corev1.PodTemplateSpec{
			ObjectMeta: metav1.ObjectMeta{Labels: podLabels},
			Spec:       *podSpec,
		},
		VolumeClaimTemplates: []corev1.PersistentVolumeClaim{*dataVolumeClaim},
		PodManagementPolicy:  v1beta2.OrderedReadyPodManagement,
	}
}

func newPodSpec(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, rack *cluster.Rack, volumes []corev1.Volume, containers []corev1.Container, initContainers []corev1.Container) *corev1.PodSpec {
	// TODO: should this spec be fully exposed into the CDC.Spec?
	podSpec := &corev1.PodSpec{
		Volumes:            volumes,
		Containers:         containers,
		InitContainers:     initContainers,
		ImagePullSecrets:   cdc.Spec.ImagePullSecrets,
		ServiceAccountName: cdc.Spec.ServiceAccountName,
		NodeSelector:       rack.NodeLabels,
	}

	return podSpec
}

func newCassandraContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, configVolume, rackConfigVolume *corev1.Volume, userSecretVolume, userConfigVolume *corev1.Volume) *corev1.Container {
	container := &corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Args:            []string{OperatorConfigVolumeMountPath, RackConfigVolumeMountPath},
		Ports: []corev1.ContainerPort{
			{Name: "internode", ContainerPort: 7000},
			{Name: "internode-tls", ContainerPort: 7001},
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
		Env: cdc.Spec.CassandraEnv,
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
			{Name: rackConfigVolume.Name, MountPath: RackConfigVolumeMountPath},
		},
	}

	if userConfigVolume != nil {
		container.Args = append(container.Args, UserConfigVolumeMountPath)
		container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: userConfigVolume.Name, MountPath: UserConfigVolumeMountPath})
	}

	if userSecretVolume != nil {
		container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: userSecretVolume.Name, MountPath: UserSecretVolumeMountPath})
	}

	if cdc.Spec.PrometheusSupport == true {
		container.Ports = append(container.Ports, corev1.ContainerPort{Name: "promql", ContainerPort: 9500})
	}

	return container
}

func newSidecarContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, dataVolumeClaim *corev1.PersistentVolumeClaim, podInfoVolume *corev1.Volume, backupSecretVolume *corev1.Volume) *corev1.Container {
	container := &corev1.Container{
		Name:            "sidecar",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports: []corev1.ContainerPort{
			{Name: "http", ContainerPort: sidecar.DefaultSidecarClientOptions.Port},
		},
		Env: cdc.Spec.SidecarEnv,
		VolumeMounts: []corev1.VolumeMount{
			{Name: dataVolumeClaim.Name, MountPath: DataVolumeMountPath},
			{Name: podInfoVolume.Name, MountPath: "/etc/pod-info"},
		},
	}

	if backupSecretVolume != nil {
		container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: backupSecretVolume.Name, MountPath: BackupSecretVolumeMountPath})
	}

	return container
}

func newSysctlLimitsContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) *corev1.Container {
	return &corev1.Container{
		Name:            "sysctl-limits",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		SecurityContext: &corev1.SecurityContext{
			Privileged: &cdc.Spec.PrivilegedSupported,
		},
		Command: []string{"bash", "-xuec"},
		Args: []string{
			`sysctl -w vm.max_map_count=1048575 || true`,
		},
	}
}

func newUserConfigVolume(rctx *reconciliationRequestContext) *corev1.Volume {
	if rctx.cdc.Spec.UserConfigMapVolumeSource == nil {
		return nil
	}

	return &corev1.Volume{
		Name:         rctx.cdc.Spec.UserConfigMapVolumeSource.Name,
		VolumeSource: corev1.VolumeSource{ConfigMap: rctx.cdc.Spec.UserConfigMapVolumeSource},
	}
}

func newUserSecretVolume(rctx *reconciliationRequestContext) *corev1.Volume {
	if rctx.cdc.Spec.UserSecretVolumeSource == nil {
		return nil
	}
	return &corev1.Volume{
		Name:         rctx.cdc.Spec.UserSecretVolumeSource.SecretName,
		VolumeSource: corev1.VolumeSource{Secret: rctx.cdc.Spec.UserSecretVolumeSource},
	}
}

func newBackupSecretVolume(rctx *reconciliationRequestContext) *corev1.Volume {
	if rctx.cdc.Spec.BackupSecretVolumeSource == nil {
		return nil
	}
	return &corev1.Volume{
		Name:         rctx.cdc.Spec.BackupSecretVolumeSource.SecretName,
		VolumeSource: corev1.VolumeSource{Secret: rctx.cdc.Spec.BackupSecretVolumeSource},
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

func scaleStatefulSet(rctx *reconciliationRequestContext, existingStatefulSet *v1beta2.StatefulSet, newStatefulSetSpec *v1beta2.StatefulSetSpec, rack *cluster.Rack) error {

	var (
		currentSpecReplicas, // number of replicas set in the current spec
		currentStatusReplicas, // currently running replicas
		desiredSpecReplicas int32 // the new requested spec replicas
	)

	// Get all replicas numbers
	desiredSpecReplicas = rack.Replicas
	currentStatusReplicas = existingStatefulSet.Status.Replicas
	if existingStatefulSet.Spec.Replicas != nil {
		currentSpecReplicas = *existingStatefulSet.Spec.Replicas
	}

	// Get pods, clients and statuses
	allPods, err := AllPodsInCDC(rctx.client, rctx.cdc)
	if err != nil {
		log.Info("unable to list pods in the cdc")
		return err
	}
	podsInRack, err := AllPodsInRack(rctx.client, rctx.cdc.Namespace, RackLabels(rctx.cdc, rack))
	if err != nil {
		log.Info(fmt.Sprintf("unable to list pods in rack %v", rack.Name))
		return err
	}
	clients := sidecar.SidecarClients(allPods, &sidecar.DefaultSidecarClientOptions)
	statuses := cassandraStatuses(clients)
	decommissionedNodes := decommissionedCassandras(statuses)

	// Check the current replicas/pods/cassandra state
	if valid := checkState(currentSpecReplicas, currentStatusReplicas, desiredSpecReplicas, allPods, statuses); !valid {
		return ErrorCDCNotReady
	}

	if existingStatefulSet.CreationTimestamp.IsZero() {
		// creating a new StatefulSet -- just set the Spec and we're done
		existingStatefulSet.Spec = *newStatefulSetSpec
		return nil
	}

	// Scale
	if desiredSpecReplicas > currentSpecReplicas {
		// Scale up
		existingStatefulSet.Spec = *newStatefulSetSpec
		return controllerutil.SetControllerReference(rctx.cdc, existingStatefulSet, rctx.scheme)
	} else if desiredSpecReplicas < currentSpecReplicas {
		// Scale down
		newestPod := podsInRack[len(podsInRack)-1]
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
				return fmt.Errorf("skipping StatefulSet reconciliation as the DataCenter contains one decommissioned Cassandra node, but it is not the newest in this rack (%v), "+
					"might belong to a different rack (%v); decommissioned pod: %v, expected pod: %v",
					rack.Name, decommissionedNodes[0].Labels[rackKey], decommissionedNodes[0].Name, newestPod.Name)
			}

			existingStatefulSet.Spec = *newStatefulSetSpec
		} else {
			return errors.New("skipping StatefulSet reconciliation as the DataCenter contains more than one decommissioned Cassandra node: " +
				strings.Join(podsToString(decommissionedNodes), ","))
		}
	}

	return nil
}

// helpers

func AllPodsInCDC(c client.Client, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) ([]corev1.Pod, error) {
	return getPods(c, cdc.Namespace, DataCenterLabels(cdc))
}

func AllPodsInRack(c client.Client, namespace string, rackLabels map[string]string) ([]corev1.Pod, error) {
	return getPods(c, namespace, rackLabels)
}

func getPods(c client.Client, namespace string, l map[string]string) ([]corev1.Pod, error) {
	podList := corev1.PodList{}
	listOps := &client.ListOptions{
		Namespace:     namespace,
		LabelSelector: labels.SelectorFromSet(l),
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

	return len(notRunningPodNames) == 0, notRunningPodNames
}

func cassandraStatuses(podClients map[*corev1.Pod]*sidecar.Client) map[*corev1.Pod]nodestate.NodeState {

	podByOperationMode := make(map[*corev1.Pod]nodestate.NodeState)

	var wg sync.WaitGroup
	var mu sync.Mutex

	wg.Add(len(podClients))

	for pod, c := range podClients {
		go func(pod *corev1.Pod, client *sidecar.Client) {
			mu.Lock()
			if response, err := client.Status(); err != nil {
				podByOperationMode[pod] = nodestate.ERROR
			} else {
				podByOperationMode[pod] = response.NodeState
			}
			mu.Unlock()
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
	currentSpecReplicas, currentStatusReplicas, desiredSpecReplicas int32,
	allPods []corev1.Pod,
	statuses map[*corev1.Pod]nodestate.NodeState) bool {

	// check if current running # of pods match the current spec of the stateful set
	if currentStatusReplicas != currentSpecReplicas {
		log.Info("skipping StatefulSet reconciliation as it is undergoing scaling operations", "current", currentStatusReplicas, "expected", currentSpecReplicas)
		return false
	}

	// check if all pods in the cluster are in a Running mode
	if allRun, notRunningPods := allPodsAreRunning(allPods); allRun == false {
		log.Info("Skipping reconciliation as some pods are not running yet: " + strings.Join(notRunningPods, " "))
		return false
	}

	// check if all Cassandras are ok to scale
	if currentSpecReplicas > 0 {
		badPods := badPods(desiredSpecReplicas, currentSpecReplicas, statuses)
		if len(badPods) > 0 {
			log.Info("skipping StatefulSet reconciliation as some Cassandra pods are not in the running mode")
			for pod, status := range badPods {
				log.Info(fmt.Sprintf("Pod: '%v', Status: '%v'", pod, status))
			}
			return false
		}
	}

	return true
}

func podsToString(pods []*corev1.Pod) []string {
	var podNames []string
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

func rackExist(name string, sets []v1.StatefulSet) bool {
	for _, set := range sets {
		if set.Labels[rackKey] == name {
			return true
		}
	}

	return false
}

func findRackToReconcile(rctx *reconciliationRequestContext) (*cluster.Rack, error) {

	// This currently works with the following logic:
	// 1. Build the racks distribution numbers.
	// 2. Fetch the running stateful sets sorted by number of currently running nodes (or in reverse when scaling down)
	// 3. Check if all racks (stateful sets) have been created. If not, create a new missing one
	// 4. If all racks are present, cycle through and check if the expected distribution
	// replicas match the status, if not - reconcile by adding 1 to the current spec of the set.

	racksDistribution := cluster.BuildRacksDistribution(rctx.cdc.Spec)

	// Get all stateful sets, sorted
	sets, err := getStatefulSets(rctx)
	if err != nil {
		log.Error(err, "Can't find Stateful Sets")
		return nil, err
	}

	// check if all required racks are built. If not, create a missing one.
	for _, rack := range racksDistribution {
		if !rackExist(rack.Name, sets) {
			// make sure that replicas in the new set is <= 1 otherwise the set may launch more than 1 pod at a time,
			// which may end up with uneven replicas distribution
			if rack.Replicas > 1 {
				rack.Replicas = 1
			}
			return rack, nil
		}
	}

	// Otherwise, we have all stateful sets running. Let's see which one we should reconcile.
	for _, sts := range sets {
		rack := racksDistribution.GetRack(sts.Labels[rackKey])
		if rack == nil {
			log.Info("couldn't find the rack %v in the distribution\n", sts.Labels[rackKey])
			continue
		}
		if rack.Replicas != *sts.Spec.Replicas {
			// reconcile
			// update the number of replicas in the rack with the current spec +1 or -1 depending on scale up or down.
			if scaleUp(rctx) {
				rack.Replicas = *sts.Spec.Replicas + 1
			} else if scaleDown(rctx) {
				rack.Replicas = *sts.Spec.Replicas - 1
			}
			return rack, nil
		}
	}

	// if we're here, no rack needs to be reconciled
	return nil, nil

}

func getStatefulSets(rctx *reconciliationRequestContext) ([]v1.StatefulSet, error) {
	sts := &v1.StatefulSetList{}
	if err := rctx.client.List(context.TODO(), &client.ListOptions{Namespace: rctx.cdc.Namespace}, sts); err != nil {
		if errors2.IsNotFound(err) {
			return []v1.StatefulSet{}, nil
		}
		return []v1.StatefulSet{}, err
	}

	if scaleUp(rctx) {
		// Scaling up
		return sortAscending(sts.Items), nil
	} else if scaleDown(rctx) {
		// Scaling down
		return sortDescending(sts.Items), nil
	}

	// if all nodes present or not scaling, no need to sort
	return sts.Items, nil
}

func scaleUp(rctx *reconciliationRequestContext) bool {
	allPods, err := AllPodsInCDC(rctx.client, rctx.cdc)
	if err != nil {
		return false
	}
	return int32(len(allPods)) < rctx.cdc.Spec.Nodes
}

func scaleDown(rctx *reconciliationRequestContext) bool {
	allPods, err := AllPodsInCDC(rctx.client, rctx.cdc)
	if err != nil {
		return false
	}
	return int32(len(allPods)) > rctx.cdc.Spec.Nodes
}

func sortAscending(sets []v1.StatefulSet) (s []v1.StatefulSet) {
	// Sort sets from lowest to highest numerically by the number of the nodes in the set
	sort.SliceStable(sets, func(i, j int) bool {
		return sets[i].Status.Replicas < sets[j].Status.Replicas
	})

	return sets
}

func sortDescending(sets []v1.StatefulSet) (s []v1.StatefulSet) {
	// Sort sets from highest to lowest numerically by the number of the nodes in the set
	sort.SliceStable(sets, func(i, j int) bool {
		return sets[i].Status.Replicas > sets[j].Status.Replicas
	})

	return sets
}


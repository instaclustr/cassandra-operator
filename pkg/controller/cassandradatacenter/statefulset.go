package cassandradatacenter

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"
	"github.com/instaclustr/cassandra-operator/pkg/common/nodestate"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/apps/v1"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	errors2 "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
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
	statefulSet.Labels = StatefulsetLabels(rctx.cdc)

	logger := rctx.logger.WithValues("StatefulSet.Name", statefulSet.Name)

	result, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, statefulSet, func(obj runtime.Object) error {

		if err := controllerutil.SetControllerReference(rctx.cdc, statefulSet, rctx.scheme); err != nil {
			return err
		}

		emptyDirVolume := newEmptyDirVolume(rctx.cdc.Spec.DummyVolume)
		dataVolumeClaim := newPersistenceVolumeClaim(rctx.cdc.Spec.DataVolumeClaimSpec)
		podInfoVolume := newPodInfoVolume()
		backupSecretVolume := newBackupSecretVolume(rctx)
		userSecretVolume := newUserSecretVolume(rctx)
		userConfigVolume := newUserConfigVolume(rctx)
		rackConfigVolume, err := createOrUpdateCassandraRackConfig(rctx, rack)
		if err != nil {
			return err
		}

		cassandraContainer := newCassandraContainer(rctx.cdc, dataVolumeClaim, emptyDirVolume, configVolume, rackConfigVolume, userSecretVolume, userConfigVolume)
		sidecarContainer := newSidecarContainer(rctx.cdc, dataVolumeClaim, emptyDirVolume, podInfoVolume, backupSecretVolume)

		restoreContainer, err := newRestoreContainer(rctx.cdc, rctx.client, dataVolumeClaim, emptyDirVolume, backupSecretVolume)
		if err != nil {
			return err
		}

		var initContainers []corev1.Container

		if restoreContainer != nil {
			initContainers = append(initContainers, *restoreContainer)
		}

		// Create sysctl init container only when user specifies `optimizeKernelParams: true`.
		if rctx.cdc.Spec.OptimizeKernelParams {
			initContainers = append(initContainers, *newSysctlLimitsContainer(rctx.cdc))
		}

		sc := &corev1.PodSecurityContext{}
		// Set the pod's FSGroup if specified by the user.
		if rctx.cdc.Spec.FSGroup != 0 {
			sc.FSGroup = &rctx.cdc.Spec.FSGroup
		}

		podVolumes := []corev1.Volume{*podInfoVolume, *configVolume, *rackConfigVolume}

		if dataVolumeClaim == nil {
			podVolumes = append(podVolumes, *emptyDirVolume)
		}

		podSpec := newPodSpec(rctx.cdc, rack,
			podVolumes,
			[]corev1.Container{*cassandraContainer, *sidecarContainer},
			initContainers,
			sc,
		)

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

func newStatefulSetSpec(
	cdc *cassandraoperatorv1alpha1.CassandraDataCenter,
	podSpec *corev1.PodSpec,
	dataVolumeClaim *corev1.PersistentVolumeClaim,
	rack *cluster.Rack) *v1beta2.StatefulSetSpec {
	podRackLabels := RackLabels(cdc, rack)
	podLabels := PodTemplateSpecLabels(cdc)
	statefulSetSpec := &v1beta2.StatefulSetSpec{
		ServiceName: "cassandra", // TODO: correct service name? this service should already exist (apparently)
		Replicas:    &rack.Replicas,
		Selector:    &metav1.LabelSelector{MatchLabels: podRackLabels},
		Template: corev1.PodTemplateSpec{
			ObjectMeta: metav1.ObjectMeta{Labels: mergeLabelMaps(podLabels, podRackLabels)},
			Spec:       *podSpec,
		},
		PodManagementPolicy: v1beta2.OrderedReadyPodManagement,
	}

	if dataVolumeClaim != nil {
		statefulSetSpec.VolumeClaimTemplates = []corev1.PersistentVolumeClaim{*dataVolumeClaim}
	}

	return statefulSetSpec
}

func newPodSpec(
	cdc *cassandraoperatorv1alpha1.CassandraDataCenter,
	rack *cluster.Rack,
	volumes []corev1.Volume,
	containers []corev1.Container,
	initContainers []corev1.Container,
	securityContext *corev1.PodSecurityContext) *corev1.PodSpec {
	// TODO: should this spec be fully exposed into the CDC.Spec?
	podSpec := &corev1.PodSpec{
		Volumes:            volumes,
		Containers:         containers,
		InitContainers:     initContainers,
		ImagePullSecrets:   cdc.Spec.ImagePullSecrets,
		ServiceAccountName: cdc.Spec.ServiceAccountName,
		NodeSelector:       rack.NodeLabels,
		SecurityContext:    securityContext,
	}

	return podSpec
}

func newCassandraContainer(
	cdc *cassandraoperatorv1alpha1.CassandraDataCenter,
	dataVolumeClaim *corev1.PersistentVolumeClaim,
	emptyDirVolume *corev1.Volume,
	configVolume, rackConfigVolume, userSecretVolume, userConfigVolume *corev1.Volume) *corev1.Container {
	container := &corev1.Container{
		Name:            "cassandra",
		Image:           cdc.Spec.CassandraImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports:           ports{internodePort, internodeTlsPort, cqlPort, jmxPort}.asContainerPorts(),
		Resources:       *cdc.Spec.Resources,
		Args:            []string{OperatorConfigVolumeMountPath, RackConfigVolumeMountPath},
		Env:             cdc.Spec.CassandraEnv,
		ReadinessProbe: &corev1.Probe{
			Handler: corev1.Handler{
				Exec: &corev1.ExecAction{
					Command: []string{"/usr/bin/cql-readiness-probe"},
				},
			},
			InitialDelaySeconds: 60,
			TimeoutSeconds:      5,
		},
	}

	var volumeMounts = []corev1.VolumeMount{
		{Name: configVolume.Name, MountPath: OperatorConfigVolumeMountPath},
		{Name: rackConfigVolume.Name, MountPath: RackConfigVolumeMountPath},
	}

	if dataVolumeClaim == nil {
		volumeMounts = append(volumeMounts, corev1.VolumeMount{Name: emptyDirVolume.Name, MountPath: DataVolumeMountPath})
	} else {
		volumeMounts = append(volumeMounts, corev1.VolumeMount{Name: dataVolumeClaim.Name, MountPath: DataVolumeMountPath})
	}

	container.VolumeMounts = volumeMounts

	// Create C* container with capabilities required for performance tweaks only when user
	// specifies `optimizeKernelParams: true`.
	if cdc.Spec.OptimizeKernelParams {
		container.SecurityContext = &corev1.SecurityContext{
			Capabilities: &corev1.Capabilities{
				Add: []corev1.Capability{
					"IPC_LOCK",     // C* wants to mlock/mlockall
					"SYS_RESOURCE", // permit ulimit adjustments
				},
			},
		}
		container.Env = append(container.Env, corev1.EnvVar{
			Name:  "MEMORY_LOCK",
			Value: "true",
		})
	}

	if userConfigVolume != nil {
		container.Args = append(container.Args, UserConfigVolumeMountPath)
		container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: userConfigVolume.Name, MountPath: UserConfigVolumeMountPath})
	}

	if userSecretVolume != nil {
		container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: userSecretVolume.Name, MountPath: UserSecretVolumeMountPath})
	}

	if cdc.Spec.PrometheusSupport == true {
		container.Ports = append(container.Ports, promqlPort.asContainerPort())
	}

	return container
}

func newSidecarContainer(
	cdc *cassandraoperatorv1alpha1.CassandraDataCenter,
	dataVolumeClaim *corev1.PersistentVolumeClaim,
	emptyDirVolume *corev1.Volume,
	podInfoVolume *corev1.Volume,
	backupSecretVolume *corev1.Volume) *corev1.Container {
	container := &corev1.Container{
		Name:            "sidecar",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Ports:           sidecarPort.asContainerPorts(),
		Env:             cdc.Spec.SidecarEnv,
	}

	if cdc.Spec.SidecarResources != nil {
		container.Resources = *cdc.Spec.SidecarResources
	}

	var volumeMounts = []corev1.VolumeMount{
		{Name: podInfoVolume.Name, MountPath: "/etc/pod-info"},
	}

	if dataVolumeClaim == nil {
		volumeMounts = append(volumeMounts, corev1.VolumeMount{Name: emptyDirVolume.Name, MountPath: DataVolumeMountPath})
	} else {
		volumeMounts = append(volumeMounts, corev1.VolumeMount{Name: dataVolumeClaim.Name, MountPath: DataVolumeMountPath})
	}

	container.VolumeMounts = volumeMounts

	if backupSecretVolume != nil {
		container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: backupSecretVolume.Name, MountPath: BackupSecretVolumeMountPath})
	}

	return container
}

func newSysctlLimitsContainer(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) *corev1.Container {
	return &corev1.Container{
		Name:            "sysctl-limits",
		Image:           "busybox:latest",
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		SecurityContext: &corev1.SecurityContext{
			Privileged: boolPointer(true),
		},
		Command: []string{"sh", "-xuec"},
		Args: []string{
			`sysctl -w vm.max_map_count=1048575`,
		},
	}
}

func newRestoreContainer(
	cdc *cassandraoperatorv1alpha1.CassandraDataCenter,
	client client.Client,
	dataVolumeClaim *corev1.PersistentVolumeClaim,
	emptyDirVolume *corev1.Volume,
	backupSecretVolume *corev1.Volume,
) (*corev1.Container, error) {

	// no restores
	if len(cdc.Spec.RestoreFromBackup) == 0 {
		return nil, nil
	}

	backup := &cassandraoperatorv1alpha1.CassandraBackup{}
	if err := client.Get(context.TODO(), types.NamespacedName{Name: cdc.Spec.RestoreFromBackup, Namespace: cdc.Namespace}, backup); err != nil {
		return nil, err
	}

	if len(backup.Spec.CDC) == 0 {
		return nil, errors.New(fmt.Sprintf("cdc field in backup CRD %s was not set!", cdc.Spec.RestoreFromBackup))
	}

	if len(backup.Spec.SnapshotTag) == 0 {
		return nil, errors.New(fmt.Sprintf("snapshotTag field in backup CRD %s was not set!", cdc.Spec.RestoreFromBackup))
	}

	if len(backup.Spec.StorageLocation) == 0 {
		return nil, errors.New(fmt.Sprintf("storageLocation field in backup CRD %s was not set!", cdc.Spec.RestoreFromBackup))
	}

	restoreArgs := []string{
		"restore",
		"--snapshot-tag=" + backup.Spec.SnapshotTag,
		"--storage-location=" + backup.Spec.StorageLocation + "/" + backup.Spec.CDC,
	}

	container := &corev1.Container{
		Name:            "restore",
		Image:           cdc.Spec.SidecarImage,
		ImagePullPolicy: cdc.Spec.ImagePullPolicy,
		Args:            restoreArgs,
		Env:             cdc.Spec.SidecarEnv,
	}

	var volumeMounts []corev1.VolumeMount

	if dataVolumeClaim == nil {
		volumeMounts = []corev1.VolumeMount{{Name: emptyDirVolume.Name, MountPath: DataVolumeMountPath}}
	} else {
		volumeMounts = []corev1.VolumeMount{{Name: dataVolumeClaim.Name, MountPath: DataVolumeMountPath}}
	}

	container.VolumeMounts = volumeMounts

	// check backupSecretVolume only if backup type is GCP
	if backup.Spec.IsGcpBackup() {
		if backupSecretVolume != nil {
			container.VolumeMounts = append(container.VolumeMounts, corev1.VolumeMount{Name: backupSecretVolume.Name, MountPath: BackupSecretVolumeMountPath})
		} else {
			return nil, errors.New(fmt.Sprintf("Restoring backup from %s is not possible because backupSecretVolumeSource field in CDC %s was not set!", cdc.Spec.RestoreFromBackup, cdc.Name))
		}
	}

	return container, nil
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

func newEmptyDirVolume(emptyDir *corev1.EmptyDirVolumeSource) *corev1.Volume {
	return &corev1.Volume{
		Name: "cache-volume",
		VolumeSource: corev1.VolumeSource{
			EmptyDir: emptyDir,
		},
	}
}

func newPersistenceVolumeClaim(persistentVolumeClaimSpec *corev1.PersistentVolumeClaimSpec) *corev1.PersistentVolumeClaim {

	if persistentVolumeClaimSpec == nil {
		return nil
	}

	return &corev1.PersistentVolumeClaim{
		ObjectMeta: metav1.ObjectMeta{Name: "data-volume"},
		Spec:       *persistentVolumeClaimSpec,
	}
}

func scaleStatefulSet(
	rctx *reconciliationRequestContext,
	existingStatefulSet *v1beta2.StatefulSet,
	newStatefulSetSpec *v1beta2.StatefulSetSpec,
	rack *cluster.Rack) error {

	var (
		currentSpecReplicas, // number of replicas set in the current spec
		desiredSpecReplicas int32 // the new requested spec replicas
	)

	// Get all replicas numbers
	desiredSpecReplicas = rack.Replicas
	if existingStatefulSet.Spec.Replicas != nil {
		currentSpecReplicas = *existingStatefulSet.Spec.Replicas
	}

	// Get pods, clients and states
	podsInRack, err := AllPodsInRack(rctx.client, rctx.cdc.Namespace, RackLabels(rctx.cdc, rack))
	if err != nil {
		log.Info(fmt.Sprintf("unable to list pods in rack %v", rack.Name))
		return err
	}
	states := cassandraStates(rctx.sidecarClients)
	decommissionedNodes := nodesInState(states, nodestate.DECOMMISSIONED)

	if existingStatefulSet.CreationTimestamp.IsZero() {
		// creating a new StatefulSet -- just set the Spec and we're done
		existingStatefulSet.Spec = *newStatefulSetSpec
		return nil
	}

	// Scale
	if desiredSpecReplicas > currentSpecReplicas {
		// Scale up

		rctx.recorder.Event(
			rctx.cdc,
			corev1.EventTypeNormal,
			"SuccessEvent",
			fmt.Sprintf("Scaling up %s from %d to %d nodes.", rctx.cdc.Name, currentSpecReplicas, desiredSpecReplicas))

		existingStatefulSet.Spec = *newStatefulSetSpec
		return controllerutil.SetControllerReference(rctx.cdc, existingStatefulSet, rctx.scheme)
	} else if desiredSpecReplicas < currentSpecReplicas {

		rctx.recorder.Event(
			rctx.cdc,
			corev1.EventTypeNormal,
			"SuccessEvent",
			fmt.Sprintf("Scaling down %s from %d to %d nodes.", rctx.cdc.Name, currentSpecReplicas, desiredSpecReplicas))

		// Scale down
		newestPod := podsInRack[len(podsInRack)-1]
		if len(decommissionedNodes) == 0 {
			log.Info("No Cassandra nodes have been decommissioned. Decommissioning the newest one " + newestPod.Name)
			if clientForNewestPod := sidecar.ClientFromPods(rctx.sidecarClients, newestPod); clientForNewestPod != nil {
				if _, err := clientForNewestPod.StartOperation(&sidecar.DecommissionRequest{}); err != nil {

					rctx.recorder.Event(
						rctx.cdc,
						corev1.EventTypeWarning,
						"FailureEvent",
						fmt.Sprintf("Node %s was unable to be decommissioned: %v", newestPod.Name, err))
				}

				rctx.recorder.Event(
					rctx.cdc,
					corev1.EventTypeNormal,
					"SuccessEvent",
					fmt.Sprintf("Decommissioning of node %s was started.", newestPod.Name))
			} else {
				return fmt.Errorf("client for pod %s to decommission does not exist", newestPod.Name)
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
			rctx.recorder.Event(
				rctx.cdc,
				corev1.EventTypeWarning,
				"FailureEvent",
				fmt.Sprintf("Unable to decommission a node as than one Cassandra node is already decommissioned."))
		}
	}

	return nil
}

func cassandraStates(podClients map[*corev1.Pod]*sidecar.Client) map[*corev1.Pod]nodestate.NodeState {

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

type nodeStates []nodestate.NodeState

var scaleUpOperationModes = nodeStates{nodestate.NORMAL}
var scaleDownOperationModes = nodeStates{nodestate.NORMAL, nodestate.DECOMMISSIONED}

func (states nodeStates) asString() string {

	var stringStates []string

	for _, state := range states {
		stringStates = append(stringStates, string(state))
	}

	return strings.Join(stringStates, ",")
}

func (states nodeStates) contains(state nodestate.NodeState) bool {
	for _, n := range states {
		if state == n {
			return true
		}
	}
	return false
}

func badNodes(rctx *reconciliationRequestContext) (map[string]nodestate.NodeState, string) {

	// return map of [bad pods name]: [their state]
	// return list of desired states appropriate to the current scaling operation as a string

	states := cassandraStates(rctx.sidecarClients)

	opModes := scaleUpOperationModes
	if rctx.operation == scalingDown {
		opModes = scaleDownOperationModes
	}

	podsInBadState := make(map[string]nodestate.NodeState)
	for pod, state := range states {
		if !opModes.contains(state) {
			podsInBadState[pod.Name] = state
		}
	}

	return podsInBadState, opModes.asString()
}

func checkClusterHealth(rctx *reconciliationRequestContext) (bool, error) {

	// Check the cluster health. If the cluster is not ready, do not reconcile and just wait.

	// 1. Check all the currently existing pods, and make sure they're all in a Running state.
	if allRun, notRunningPods := allPodsAreRunning(rctx.allPods); !allRun {
		log.Info("Skipping reconciliation as some pods are not running yet: " + strings.Join(notRunningPods, " "))
		return false, ErrorClusterNotReady
	}

	// 2. Check all the currently existing C*, and make sure they're all in either NORMAL or DECOMMISSIONED state.
	if badNodes, desiredNodeStates := badNodes(rctx); len(badNodes) > 0 {
		log.Info(fmt.Sprintf("skipping StatefulSet reconciliation as some Cassandra nodes are not in modes %s", desiredNodeStates))
		for pod, status := range badNodes {
			log.Info(fmt.Sprintf("Pod: '%v', Status: '%v'", pod, status))
		}
		return false, ErrorClusterNotReady
	}

	// 3. Check that all stateful sets are not undergoing scale operations.
	for _, set := range rctx.sets {
		if set.Status.Replicas != *set.Spec.Replicas {
			log.Info("skipping StatefulSet reconciliation as it is undergoing scaling operations", "current", set.Status.Replicas, "expected", set.Spec.Replicas)
			return false, ErrorClusterNotReady
		}
	}

	return true, nil
}

func findRackToReconcile(rctx *reconciliationRequestContext) (*cluster.Rack, error) {

	// 1. Build the racks distribution numbers.
	racksDistribution := cluster.BuildRacksDistribution(rctx.cdc.Spec)

	// 2. check if all required racks are built. If not, create a missing one.
	for _, rack := range racksDistribution {
		if !rackExist(rack.Name, rctx.sets) {
			// make sure that replicas in the new set are capped at 1, otherwise the set may launch more than 1 pod at a time,
			// which may end up with uneven replicas distribution. We still honor the rack.Replicas == 0 for cases where
			// distribution places 0 pods in a rack (i.e. 2 nodes in 3 racks, for example) and we still want to create an
			// empty stateful set in this case.
			if rack.Replicas > 1 {
				rack.Replicas = 1
			}
			return rack, nil
		}
	}

	// 3. Otherwise, we have all stateful sets running. Let's see which one we should reconcile.
	for _, sts := range rctx.sets {
		rack := racksDistribution.GetRack(sts.Labels[rackKey])
		if rack == nil {
			continue
		}
		if rack.Replicas != *sts.Spec.Replicas {
			// reconcile this rack.
			// update the number of replicas in the rack with the current spec +1 or -1 depending on scale up or down.
			if rctx.operation == scalingUp {
				rack.Replicas = *sts.Spec.Replicas + 1
			} else if rctx.operation == scalingDown {
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

	if rctx.operation == scalingUp {
		return sortStatefulSetsAscending(sts.Items), nil
	} else if rctx.operation == scalingDown {
		return sortStatefulSetsDescending(sts.Items), nil
	}

	// if all nodes present or not scaling, no need to sort
	return sts.Items, nil
}

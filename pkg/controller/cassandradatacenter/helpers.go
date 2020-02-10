package cassandradatacenter

import (
	"context"
	"sort"
	"strings"

	"github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	v1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

func sortStatefulSetsAscending(sets []v1.StatefulSet) (s []v1.StatefulSet) {
	// Sort sets from lowest to highest numerically by the number of the nodes in the set
	sort.SliceStable(sets, func(i, j int) bool {
		return sets[i].Status.Replicas < sets[j].Status.Replicas
	})
	return sets
}

func sortStatefulSetsDescending(sets []v1.StatefulSet) (s []v1.StatefulSet) {
	// Sort sets from highest to lowest numerically by the number of the nodes in the set
	sort.SliceStable(sets, func(i, j int) bool {
		return sets[i].Status.Replicas > sets[j].Status.Replicas
	})
	return sets
}

func AllPodsInCDC(c client.Client, cdc *v1alpha1.CassandraDataCenter) ([]corev1.Pod, error) {
	return getPods(c, cdc.Namespace, DataCenterLabels(cdc))
}

func AllDeletedPods(c client.Client, cdc *v1alpha1.CassandraDataCenter) ([]corev1.Pod, error) {
	if pods, err := AllPodsInCDC(c, cdc); err != nil {
		return nil, err
	} else {
		var deletedPods []corev1.Pod
		for _, pod := range pods {
			if pod.GetDeletionTimestamp() != nil {
				deletedPods = append(deletedPods, pod)
			}
		}

		return deletedPods, nil
	}
}

func AllPodsInRack(c client.Client, namespace string, rackLabels map[string]string) ([]corev1.Pod, error) {
	return getPods(c, namespace, rackLabels)
}

func getPods(c client.Client, namespace string, labels client.MatchingLabels) ([]corev1.Pod, error) {
	podList := corev1.PodList{}

	listOps := []client.ListOption{
		client.InNamespace(namespace),
		labels,
	}

	if err := c.List(context.TODO(), &podList, listOps...); err != nil {
		return nil, err
	}

	pods := podList.Items

	return pods, nil
}

func allPodsAreRunning(pods []corev1.Pod) (bool, []string) {
	// return whether all pods are running and the list of pods that are not running
	var notRunningPodNames []string
	for _, pod := range pods {
		if pod.Status.Phase != corev1.PodRunning {
			notRunningPodNames = append(notRunningPodNames, pod.Name)
		}
	}
	return len(notRunningPodNames) == 0, notRunningPodNames
}

func podsToString(pods []*corev1.Pod) string {
	// return the list of pod names as a string
	var podNames []string
	for _, pod := range pods {
		podNames = append(podNames, pod.Name)
	}
	return strings.Join(podNames, ",")
}

func rackExist(name string, sets []v1.StatefulSet) bool {
	// check if a rack exists in the list of racks
	for _, set := range sets {
		if set.Labels[RackKey] == name {
			return true
		}
	}
	return false
}

// boolPointer returns a pointer to bool b.
func boolPointer(b bool) *bool {
	return &b
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

func remove(list []string, s string) []string {
	for i, v := range list {
		if v == s {
			list = append(list[:i], list[i+1:]...)
		}
	}
	return list
}

package cassandradatacenter

import (
	"context"
	"sort"
	"strings"

	"github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	v1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/labels"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

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

func AllPodsInCDC(c client.Client, cdc *v1alpha1.CassandraDataCenter) ([]corev1.Pod, error) {
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
		if set.Labels[rackKey] == name {
			return true
		}
	}
	return false
}

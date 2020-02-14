package e2e

import (
	goctx "context"
	"fmt"
	"time"

	cop "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"
	"github.com/instaclustr/cassandra-operator/pkg/common/nodestate"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/util/wait"
)

func (ctx *testingContext) provisionCassandraDataCenter(cdc *cop.CassandraDataCenter) *cop.CassandraDataCenter {

	ctx.t.Log("Running test " + ctx.t.Name())

	cdc.ObjectMeta.Namespace = ctx.namespace

	ctx.createDataCenter(cdc)
	ctx.waitForDataCenter(cdc)
	ctx.waitForAllInNormalState(cdc)

	return cdc
}

func (ctx *testingContext) createDataCenter(cdc *cop.CassandraDataCenter) {

	ctx.addDataCenterCleaner(cdc.DataCenter)

	if err := ctx.f.Client.Create(goctx.TODO(), cdc, ctx.cleanupOptions); err != nil {
		ctx.t.Fatalf("Unable to create Cassandra DC object of %d node(s): %v", cdc.Spec.Nodes, err)
	}
}

func (ctx *testingContext) updateDataCenter(cdc *cop.CassandraDataCenter) {
	if err := ctx.f.Client.Update(goctx.TODO(), cdc); err != nil {
		ctx.t.Fatalf("Unable to update Cassandra DC object of %d node(s): %v", cdc.Spec.Nodes, err)
	}
}

func (ctx *testingContext) selectorForDataCenter(cdc *cop.CassandraDataCenter) map[string]string {
	return map[string]string{
		"app.kubernetes.io/managed-by":                  "com.instaclustr.cassandra-operator",
		"cassandra-operator.instaclustr.com/cluster":    cdc.Cluster,
		"cassandra-operator.instaclustr.com/datacenter": cdc.DataCenter,
	}
}

func (ctx *testingContext) sidecars(cdc *cop.CassandraDataCenter) (map[*v1.Pod]*sidecar.Client, error) {
	options := metav1.ListOptions{LabelSelector: labels.Set(ctx.selectorForDataCenter(cdc)).String()}

	if pods, err := ctx.f.KubeClient.CoreV1().Pods(ctx.namespace).List(options); err != nil {
		return nil, err
	} else {
		return sidecar.SidecarClients(pods.Items, &sidecar.DefaultSidecarClientOptions), nil
	}
}

//
// scalings
//

func (ctx *testingContext) scaleDataCenterDown(cdc *cop.CassandraDataCenter) {
	ctx.scaleDataCenter(cdc, cdc.Spec.Nodes, cdc.Spec.Nodes-1)
}

func (ctx *testingContext) scaleDataCenterUp(cdc *cop.CassandraDataCenter) {
	ctx.scaleDataCenter(cdc, cdc.Spec.Nodes, cdc.Spec.Nodes+1)
}

func (ctx *testingContext) scaleDataCenter(cdc *cop.CassandraDataCenter, from, to int32) {

	ctx.t.Log(fmt.Sprintf("Deploying cluster with %v nodes\n", from))

	ctx.t.Log(fmt.Sprintf("Done deployment, scaling from %v to %v\n", from, to))

	cdc.Spec.Nodes = to

	ctx.updateDataCenter(cdc)
	ctx.waitForDataCenter(cdc)
	ctx.waitForAllInNormalState(cdc)
}

//
// waitings
//

func (ctx *testingContext) waitForAllInState(cdc *cop.CassandraDataCenter, state nodestate.NodeState) {

	sidecars, err := ctx.sidecars(cdc)

	if err != nil {
		ctx.t.Fatalf("error getting pod's sidecars: %v", err)
	}

	pollingErr := wait.Poll(time.Second*5, time.Second*600, func() (done bool, err error) {
		for _, client := range sidecars {
			if status, err := client.Status(); err != nil {
				return false, err
			} else if status.NodeState != state {
				fmt.Printf("node '%v' is in status '%v', waiting to get it to %v\n", client.Host, status.NodeState, state)
				return false, nil
			}
		}

		return true, nil
	})

	if pollingErr != nil {
		ctx.t.Fatalf("All nodes were not transitioned to state %s: %v", state, pollingErr)
	}
}

func (ctx *testingContext) waitForAllInNormalState(cdc *cop.CassandraDataCenter) {
	ctx.waitForAllInState(cdc, nodestate.NORMAL)
}

func (ctx *testingContext) waitForDataCenter(cdc *cop.CassandraDataCenter) {
	rackDistribution := cluster.BuildRacksDistribution(cdc.Spec)
	for _, rack := range rackDistribution {
		statefulSetName := statefulSetName(cdc, rack)
		ctx.t.Log("Waiting for statefulset " + statefulSetName + " to be deployed")
		ctx.waitForStatefulset(statefulSetName, rack.Replicas)
	}
}

func (ctx *testingContext) waitForStatefulset(statefulSetName string, expectedReplicas int32) {

	err := wait.Poll(time.Second*5, time.Second*600, func() (done bool, err error) {
		statefulSet, err := ctx.f.KubeClient.AppsV1().StatefulSets(ctx.namespace).Get(statefulSetName, metav1.GetOptions{})

		if err != nil {
			if apierrors.IsNotFound(err) {
				ctx.t.Logf("Waiting for availability of %s statefulset in namespace %s", statefulSetName, ctx.namespace)
				return false, nil
			}
			return false, err
		}

		replicas := statefulSet.Status.Replicas
		readyReplicas := statefulSet.Status.ReadyReplicas

		if replicas != expectedReplicas {
			ctx.t.Logf("All replicas of %s statefulset in namespace %s are started but some of them are not ready - (%d/%d)", statefulSetName, ctx.namespace, readyReplicas, expectedReplicas)
			return false, nil
		}

		if replicas != readyReplicas {
			ctx.t.Logf("Waiting for full availability of %s statefulset in namespace %s - (%d/%d)", statefulSetName, ctx.namespace, readyReplicas, replicas)
			return false, nil
		}

		return true, nil
	})

	if err != nil {
		ctx.t.Fatalf("Timeout for statefulset %s in namespace %s has occurred: %v", statefulSetName, ctx.namespace, err)
	}

	ctx.t.Logf("statefulset %s in namespace %s is fully available.", statefulSetName, ctx.namespace)
}

package e2e

import (
	"github.com/instaclustr/cassandra-operator/pkg/apis"
	"github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	framework "github.com/operator-framework/operator-sdk/pkg/test"
	"testing"
)

const (
	name            = "test-dc-cassandra"
	statefulSetName = "cassandra-test-dc-cassandra"
)

func TestCassandra(t *testing.T) {

	if err := framework.AddToFrameworkScheme(apis.AddToScheme, defaultNewCassandraDataCenterList()); err != nil {
		t.Fatalf("Failed to add custom resource scheme to framework: %v", err)
	}

	t.Run("scaling up", CassandraScalingUp)
	t.Run("scaling down", CassandraScalingDown)
}

func deployCassandra(t *testing.T, nodes int32) (*framework.TestCtx, *framework.Framework, *v1alpha1.CassandraDataCenter, string){

	t.Log("Running test " + t.Name())

	ctx, f, cleanupOptions, namespace := initialise(t)

	cassandraDC := defaultNewCassandraDataCenter(name, namespace, 3)

	createCassandraDataCenter(t, f, cleanupOptions, cassandraDC)
	waitForStatefulset(t, f, statefulSetName, cassandraDC)
	checkAllNodesInNormalMode(t, f, namespace)

	return ctx, f, cassandraDC, namespace
}

func CassandraScalingUp(t *testing.T) {
	t.Log("Running test " + t.Name())
	scaleCluster(t, 2, 3)
}

func CassandraScalingDown(t *testing.T) {
	t.Log("Running test " + t.Name())
	scaleCluster(t, 3, 2)
}

func scaleCluster(t *testing.T, from, to int32) {

	ctx, f, cassandraDC, namespace := deployCassandra(t, from)

	// scale, from "from" to "to" nodes

	t.Logf("Scaling from %v to %v\n", from, to)
	cassandraDC.Spec.Nodes = to
	updateCassandraDataCenter(t, f, cassandraDC)

	// wait until scaling is done and check all nodes are in normal
	waitForStatefulset(t, f, statefulSetName, cassandraDC)
	checkAllNodesInNormalMode(t, f, namespace)

	// Cleanup after test
	ctx.Cleanup()
}

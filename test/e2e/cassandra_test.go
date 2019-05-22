package e2e

import (
	"github.com/instaclustr/cassandra-operator/pkg/apis"
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

	t.Run("deploy", CassandraDeploymentTest)
	t.Run("scaling", CassandraScaling)
}

func CassandraDeploymentTest(t *testing.T) {

	ctx, f, cleanupOptions, namespace := initialise(t)
	defer ctx.Cleanup()

	cassandraDC := defaultNewCassandraDataCenter(name, namespace, 3)

	createCassandraDataCenter(t, f, cleanupOptions, cassandraDC)
	waitForStatefulset(t, f, statefulSetName, cassandraDC)
	checkAllNodesInNormalMode(t, f, namespace)
}

func CassandraScaling(t *testing.T) {

	ctx, f, cleanupOptions, namespace := initialise(t)

	defer ctx.Cleanup()

	cassandraDC := defaultNewCassandraDataCenter(name, namespace, 3)

	createCassandraDataCenter(t, f, cleanupOptions, cassandraDC)
	waitForStatefulset(t, f, statefulSetName, cassandraDC)
	checkAllNodesInNormalMode(t, f, namespace)

	// scale up, from 3 to 4 nodes

	cassandraDC.Spec.Nodes = 4
	updateCassandraDataCenter(t, f, cassandraDC)

	// wait until scaling up is done and check all nodes are in normal

	waitForStatefulset(t, f, statefulSetName, cassandraDC)
	checkAllNodesInNormalMode(t, f, namespace)

	// scale back to 3

	cassandraDC.Spec.Nodes = 3
	updateCassandraDataCenter(t, f, cassandraDC)

	waitForStatefulset(t, f, statefulSetName, cassandraDC)
	checkAllNodesInNormalMode(t, f, namespace)
}

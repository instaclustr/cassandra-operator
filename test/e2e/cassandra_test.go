package e2e

import (
	"fmt"
	"github.com/instaclustr/cassandra-operator/pkg/apis"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"
	framework "github.com/operator-framework/operator-sdk/pkg/test"
	v1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"testing"
)

const (
	cdcName         = "test-dc-cassandra"
	statefulSetName = "cassandra-test-dc-cassandra"
)

func TestCassandra(t *testing.T) {

	if err := framework.AddToFrameworkScheme(apis.AddToScheme, defaultNewCassandraDataCenterList()); err != nil {
		t.Fatalf("Failed to add custom resource scheme to framework: %v", err)
	}

	t.Run("scaling up", CassandraScalingUp)
	t.Run("scaling down", CassandraScalingDown)
	t.Run("minimal spec test", MinimalCassandraSpec)
}

func deployCassandra(
	t *testing.T,
	nodes int32,
	racks []cassandraoperatorv1alpha1.Rack,
	dcGeneratorFunc func(name, namespace string) *cassandraoperatorv1alpha1.CassandraDataCenter,
	configMaps []*v1.ConfigMap,
) (*framework.TestCtx, *framework.Framework, *cassandraoperatorv1alpha1.CassandraDataCenter, string) {

	t.Log("Running test " + t.Name())

	ctx, f, cleanupOptions, namespace := initialise(t, configMaps)

	cassandraDC := dcGeneratorFunc(cdcName, namespace)

	createCassandraDataCenter(t, f, cleanupOptions, cassandraDC)
	checkStatefulSets(t, f, namespace, nodes, racks)
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

func MinimalCassandraSpec(t *testing.T) {
	t.Log("Running test " + t.Name())

	configMaps := []*v1.ConfigMap{
		&v1.ConfigMap{
			ObjectMeta: metav1.ObjectMeta{
				Name: "cassandra-operator-default-config",
			},
			Data: map[string]string{
				"nodes":          "1",
				"cassandraImage": "gcr.io/cassandra-operator/cassandra:3.11.4",
				"sidecarImage":   "gcr.io/cassandra-operator/cassandra-sidecar:latest",
				"memory":         "1Gi",
				"disk":           "1Gi",
			},
		},
	}

	ctx, _, _, _ := deployCassandra(t, 1, getRacks(), func(name, namespace string) *cassandraoperatorv1alpha1.CassandraDataCenter {
		return defaultMinimalCassandraDatacenter(name, namespace)
	}, configMaps)

	ctx.Cleanup()
}

func scaleCluster(t *testing.T, from, to int32) {

	// Let's make it easy
	racks := getRacks()

	fmt.Printf("Deploying cluster with %v nodes into %v racks\n", from, racks)
	ctx, f, cassandraDC, namespace := deployCassandra(t, from, racks, func(name, namespace string) *cassandraoperatorv1alpha1.CassandraDataCenter {
		return defaultNewCassandraDataCenter(name, namespace, from, racks)
	}, []*v1.ConfigMap{})

	// scale, from "from" to "to" nodes

	fmt.Printf("Done deployment, scaling from %v to %v\n", from, to)
	cassandraDC.Spec.Nodes = to
	updateCassandraDataCenter(t, f, cassandraDC)

	// wait until scaling is done and check all racks and nodes are in normal state
	checkStatefulSets(t, f, cassandraDC.Namespace, to, racks)
	checkAllNodesInNormalMode(t, f, namespace)

	// Cleanup after test
	ctx.Cleanup()
}

func checkStatefulSets(t *testing.T, f *framework.Framework, namespace string, nodes int32, racks []cassandraoperatorv1alpha1.Rack) {
	cdcSpec := cassandraoperatorv1alpha1.CassandraDataCenterSpec{Nodes: nodes, Racks: racks}
	rackDistribution := cluster.BuildRacksDistribution(cdcSpec)
	for _, rack := range rackDistribution {
		waitForStatefulset(t, f, namespace, statefulSetName+"-"+rack.Name, rack.Replicas)
	}
}

func getRacks() []cassandraoperatorv1alpha1.Rack {

	//zone := "failure-domain.beta.kubernetes.io/zone"
	//racks := []cassandraoperatorv1alpha1.Rack{
	//	{Name: "west1-a", Labels: map[string]string{zone: "europe-west1-a"}},
	//	{Name: "west1-b", Labels: map[string]string{zone: "europe-west1-b"}},
	//	{Name: "west1-c", Labels: map[string]string{zone: "europe-west1-c"}},
	//}

	//return racks

	return []cassandraoperatorv1alpha1.Rack{
		{Name: "rack1", Labels: map[string]string{}},
	}
}

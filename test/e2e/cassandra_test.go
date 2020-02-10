package e2e

import (
	"testing"

	"github.com/instaclustr/cassandra-operator/pkg/apis"
	framework "github.com/operator-framework/operator-sdk/pkg/test"
	"k8s.io/apimachinery/pkg/api/resource"
)

var dcMetaInfo = &DcMetaInfo{
	Name:       "cassandra-test-cluster-dc1",
	Datacenter: "dc1",
	Cluster:    "test-cluster",
}

func TestCassandra(t *testing.T) {
	if err := framework.AddToFrameworkScheme(apis.AddToScheme, defaultNewCassandraDataCenterList()); err != nil {
		t.Fatalf("Failed to add custom resource scheme to framework: %v", err)
	}

	disk, _ := resource.ParseQuantity("500Mi")
	memory, _ := resource.ParseQuantity("1Gi")

	dcMetaInfo.Disk = &disk
	dcMetaInfo.Memory = &memory

	if err := dcMetaInfo.validate(); err != nil {
		t.Fatal(err)
	}

	t.Run("scaling up", CassandraScalingUp)
	t.Run("scaling down", CassandraScalingDown)
}

func CassandraScalingUp(t *testing.T) {
	runTest(t, func(ctx *testingContext) {
		cdc := defaultCassandraDataCenter(2, dcMetaInfo)

		ctx.provisionCassandraDataCenter(cdc)

		ctx.scaleDataCenterUp(cdc)
	})
}

func CassandraScalingDown(t *testing.T) {
	runTest(t, func(ctx *testingContext) {
		cdc := defaultCassandraDataCenter(3, dcMetaInfo)

		ctx.provisionCassandraDataCenter(cdc)

		ctx.scaleDataCenterDown(cdc)
	})
}

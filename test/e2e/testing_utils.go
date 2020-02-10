package e2e

import (
	goctx "context"
	"testing"
	"time"

	framework "github.com/operator-framework/operator-sdk/pkg/test"
	"github.com/operator-framework/operator-sdk/pkg/test/e2eutil"
	v1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

const (
	DataCenterKey = "cassandra-operator.instaclustr.com/datacenter"
	ClusterKey    = "cassandra-operator.instaclustr.com/cluster"
)

const (
	retryInterval        = time.Second * 5
	timeout              = time.Second * 600
	cleanupRetryInterval = time.Second * 3
	cleanupTimeout       = time.Second * 600
)

type testingContext struct {
	ctx            *framework.TestCtx
	t              *testing.T
	f              *framework.Framework
	cleanupOptions *framework.CleanupOptions
	namespace      string
}

func initialise(t *testing.T) *testingContext {

	ctx := framework.NewTestCtx(t)

	cleanupOptions := framework.CleanupOptions{
		TestContext:   ctx,
		Timeout:       cleanupTimeout,
		RetryInterval: cleanupRetryInterval,
	}

	if err := ctx.InitializeClusterResources(&cleanupOptions); err != nil {
		t.Fatalf("Failed to initialize cluster resources: %v", err)
	}

	t.Log("Initialized cluster resources")

	namespace, err := ctx.GetNamespace()

	if err != nil {
		t.Fatalf("Could not obtain namespace: %v", err)
	}

	f := framework.Global

	if err := e2eutil.WaitForOperatorDeployment(t, f.KubeClient, namespace, "cassandra-operator", 1, retryInterval, timeout); err != nil {
		t.Fatalf("Timeout of Cassandra operator deployment has occurred: %v", err)
	}

	return &testingContext{t: t, ctx: ctx, f: f, cleanupOptions: &cleanupOptions, namespace: namespace}
}

func (ctx *testingContext) createConfigMaps(configMaps []*v1.ConfigMap) {
	for _, configMap := range configMaps {
		configMap.Namespace = ctx.namespace
		_ = ctx.f.Client.Create(goctx.TODO(), configMap, &framework.CleanupOptions{
			TestContext:   ctx.ctx,
			Timeout:       timeout,
			RetryInterval: cleanupRetryInterval,
		})
	}
}

func (ctx *testingContext) addDataCenterCleaner(dataCenterName string) {
	listOptions := metav1.ListOptions{
		LabelSelector: DataCenterKey + "=" + dataCenterName,
	}

	ctx.ctx.AddCleanupFn(func() error {
		return ctx.f.KubeClient.AppsV1().StatefulSets(ctx.namespace).DeleteCollection(nil, listOptions)
	})
}

func (ctx *testingContext) addClusterCleaner(clusterName string) {
	listOptions := metav1.ListOptions{
		LabelSelector: ClusterKey + "=" + clusterName,
	}
	ctx.ctx.AddCleanupFn(func() error {
		return ctx.f.KubeClient.AppsV1().StatefulSets(ctx.namespace).DeleteCollection(nil, listOptions)
	})
}

func (ctx *testingContext) cleanup() {
	ctx.ctx.Cleanup()
}

func runTest(t *testing.T, f func(ctx *testingContext)) {
	t.Log("Running test " + t.Name())

	ctx := initialise(t)

	f(ctx)

	ctx.cleanup()
}

package cassandrabackup

import (
	"context"
	"fmt"
	"github.com/google/uuid"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"github.com/instaclustr/cassandra-operator/pkg/controller/cassandradatacenter"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	"time"
)

const (
	controllerName = "CassandraBackupController"
)

//"github.com/instaclustr/cassandra-operator/pkg/sidecar"

// blank assignment to verify that ReconcileCassandraBackup implements reconcile.Reconciler
var _ reconcile.Reconciler = &ReconcileCassandraBackup{}

// ReconcileCassandraBackup reconciles a CassandraBackup object
type ReconcileCassandraBackup struct {
	// This client, initialized using mgr.Client() above, is a split client
	// that reads objects from the cache and writes to the apiserver
	client client.Client
	scheme *runtime.Scheme
	recorder record.EventRecorder
}

// Reconcile reads that state of the cluster for a CassandraBackup object and makes changes based on the state read
// and what is in the CassandraBackup.Spec
// TODO(user): Modify this Reconcile function to implement your Controller logic.  This example creates
// a Pod as an example
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (r *ReconcileCassandraBackup) Reconcile(request reconcile.Request) (reconcile.Result, error) {
	reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
	reqLogger.Info("Reconciling CassandraBackup")

	// backup and shit

	// Get request params
	b := &cassandraoperatorv1alpha1.CassandraBackup{}
	if err := r.client.Get(context.TODO(), request.NamespacedName, b); err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	// Get Pod Clients.
	// TODO: This is a copy of the code from statefulset.existingPods, consider turning into lib/helper code
	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	if err := r.client.Get(context.TODO(), request.NamespacedName, cdc); err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	pods, err := cassandradatacenter.ExistingPods(r.client, cdc)
	if err != nil {
		return reconcile.Result{}, fmt.Errorf("unable to list pods")
	}

	// Use the following for testing:
	//pods = []v1.Pod{{Status: v1.PodStatus{Phase: v1.PodRunning, PodIP: "127.0.0.1"}}}
	sidecarClients := sidecar.SidecarClients(pods, &sidecar.DefaultSidecarClientOptions)

	// Run backups
	for _, sidecarClient := range sidecarClients {

		// TODO: run this on goroutine so that all 3 are handled in parallel
		backupRequest := &sidecar.BackupOperation{
			DestinationUri: b.Spec.DestinationUri,
			SnapshotName:   b.Spec.SnapshotName,
			Keyspaces:      b.Spec.Keyspaces,
		}

		// Testing adding event to the object
		r.recorder.Event(b, v1.EventTypeNormal, "All good", "Starting backup")

		operationID, err := sidecarClient.Backup(backupRequest)
		if err != nil {
			fmt.Println(err)
			continue
			// log error
		} else {
			opState := operations.RUNNING

			fmt.Println(b.Status.Progress)


			go func() {
				for opState != operations.COMPLETED {
					fmt.Printf("Working on operation id: %v\n", operationID)
					backup, err := getBackup(sidecarClient, *operationID)
					if backup == nil || err != nil {
						// log error?
						fmt.Println(err)
						continue
					}
					b.Status.Progress = backup.Progress
					b.Status.State = string(backup.State)
					_ = r.client.Update(context.TODO(), b)
					opState = backup.State
					<-time.After(time.Second)
				}
			}()
		}

	}

	fmt.Printf("Spec: %v\n", b.Spec)

	return reconcile.Result{}, nil
}

func getBackup(client *sidecar.Client, id uuid.UUID) (backup *sidecar.BackupResponse, err error) {
	if backups, err := client.ListBackups(); err != nil {
		return nil, err
	} else {
		for _, backup := range backups {
			if backup.Id == id {
				return backup, nil
			}
		}
	}

    return
}

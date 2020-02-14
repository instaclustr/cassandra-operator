package cassandradatacenter

import (
	"context"
	"fmt"

	"github.com/go-logr/logr"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	corev1 "k8s.io/api/core/v1"

	"sigs.k8s.io/controller-runtime/pkg/client"
)

const pvcDeletionFinalizer = "finalizer.pvcs.cassandraoperator.instaclustr.com"

func (r *ReconcileCassandraDataCenter) deletePersistenceVolumeClaim(reqLogger logr.Logger, pvc corev1.PersistentVolumeClaim) error {
	if err := r.client.Delete(context.TODO(), &pvc); err != nil {
		reqLogger.Info(fmt.Sprintf("Unable to delete pvc %s.", pvc.Name))
		return err
	} else {
		reqLogger.Info(fmt.Sprintf("Successfully submitted deletion of pvc %s.", pvc.Name))
	}

	return nil
}

type pvcFilterFunc func(corev1.PersistentVolumeClaim) bool

func (r *ReconcileCassandraDataCenter) getPVCs(
	instance *cassandraoperatorv1alpha1.CassandraDataCenter,
	filterFn *pvcFilterFunc,
) ([]corev1.PersistentVolumeClaim, error) {
	pvcList := &corev1.PersistentVolumeClaimList{}

	listOpts := []client.ListOption{
		client.InNamespace(instance.Namespace),
		client.MatchingLabels{
			"cassandra-operator.instaclustr.com/datacenter": instance.DataCenter,
			"cassandra-operator.instaclustr.com/cluster":    instance.Cluster,
		},
	}

	if err := r.client.List(context.TODO(), pvcList, listOpts...); err != nil {
		return nil, err
	} else {

		if filterFn == nil {
			return pvcList.Items, nil
		}

		var filterPVCs []corev1.PersistentVolumeClaim

		for _, pvc := range pvcList.Items {
			if (*filterFn)(pvc) {
				filterPVCs = append(filterPVCs, pvc)
			}
		}

		return filterPVCs, nil
	}
}

func (r *ReconcileCassandraDataCenter) finalizePVCs(reqLogger logr.Logger, instance *cassandraoperatorv1alpha1.CassandraDataCenter) error {

	pvcList := corev1.PersistentVolumeClaimList{}

	listOpts := []client.ListOption{
		client.InNamespace(instance.Namespace),
		client.MatchingLabels{
			"cassandra-operator.instaclustr.com/datacenter": instance.DataCenter,
			"cassandra-operator.instaclustr.com/cluster":    instance.Cluster,
		},
	}

	if err := r.client.List(context.TODO(), &pvcList, listOpts...); err != nil {
		return err
	}

	if !instance.Spec.DeletePVCs {
		return nil
	}

	for _, pvc := range pvcList.Items {
		if err := r.deletePersistenceVolumeClaim(reqLogger, pvc); err != nil {
			return err
		}
	}

	return nil
}

func (r *ReconcileCassandraDataCenter) addFinalizer(reqLogger logr.Logger, instance *cassandraoperatorv1alpha1.CassandraDataCenter) error {
	if !contains(instance.GetFinalizers(), pvcDeletionFinalizer) && instance.Spec.DeletePVCs {
		reqLogger.Info("Adding Finalizer for the CassandraDataCenter")
		instance.SetFinalizers(append(instance.GetFinalizers(), pvcDeletionFinalizer))

		err := r.client.Update(context.TODO(), instance)
		if err != nil {
			reqLogger.Error(err, "Failed to update CassandraDataCenter with finalizer "+pvcDeletionFinalizer)
			return err
		}
	}
	return nil
}

func (r *ReconcileCassandraDataCenter) finalizeIfNecessary(reqLogger logr.Logger, instance *cassandraoperatorv1alpha1.CassandraDataCenter) (bool, error) {
	if instance.GetDeletionTimestamp() == nil {
		return false, nil
	}

	if contains(instance.GetFinalizers(), pvcDeletionFinalizer) {
		if err := r.finalizePVCs(reqLogger, instance); err != nil {
			return false, err
		} else {
			r.recorder.Event(
				instance,
				corev1.EventTypeNormal,
				"SuccessEvent",
				fmt.Sprintf("%s was finalized.", instance.Name))
		}

		instance.SetFinalizers(remove(instance.GetFinalizers(), pvcDeletionFinalizer))

		if err := r.client.Update(context.TODO(), instance); err != nil {
			return false, err
		}

		return true, nil
	}

	return false, nil
}

func (r *ReconcileCassandraDataCenter) finalizeDeletedPods(reqLogger logr.Logger, instance *cassandraoperatorv1alpha1.CassandraDataCenter) error {
	if deletedPods, err := AllDeletedPods(r.client, instance); err != nil {
		return err
	} else {
		if len(deletedPods) == 0 {
			return nil
		}

		for _, pod := range deletedPods {
			r.recorder.Event(
				instance,
				corev1.EventTypeNormal,
				"SuccessEvent",
				fmt.Sprintf("Decommissioning of %s was successful.", pod.Name))
		}

		if !instance.Spec.DeletePVCs {
			return nil
		}

		if existingPVCs, err := r.getPVCs(instance, nil); err != nil {
			return err
		} else {
			for _, pod := range deletedPods {
				for _, volume := range pod.Spec.Volumes {
					podsPVC := volume.VolumeSource.PersistentVolumeClaim
					if podsPVC != nil {
						for _, c := range existingPVCs {
							if c.Name == podsPVC.ClaimName {
								if err := r.deletePersistenceVolumeClaim(reqLogger, c); err != nil {

									r.recorder.Event(
										instance,
										corev1.EventTypeWarning,
										"FailureEvent",
										fmt.Sprintf("Deletion of PVC %s failed: %v", c.Name, err))

									return err
								}

								r.recorder.Event(
									instance,
									corev1.EventTypeNormal,
									"SuccessEvent",
									fmt.Sprintf("Deletion of PVC %s was successful.", c.Name))
								break
							}
						}
					}
				}
			}
		}
	}

	return nil
}

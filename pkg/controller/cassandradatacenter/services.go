package cassandradatacenter

import (
	"context"
	"fmt"

	"k8s.io/apimachinery/pkg/api/errors"

	"sigs.k8s.io/controller-runtime/pkg/client/config"

	"github.com/operator-framework/operator-sdk/pkg/metrics"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
)

func createOrUpdatePrometheusService(rctx *reconciliationRequestContext) (*corev1.Service, error) {
	prometheusService := &corev1.Service{ObjectMeta: DataCenterResourceMetadata(rctx.cdc, "prometheus")}
	prometheusService.Labels = PrometheusLabels(rctx.cdc)

	logger := rctx.logger.WithValues("Service.Name", prometheusService.Name)

	opresult, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, prometheusService, func(_ runtime.Object) error {
		prometheusService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports:     prometheusPort.asServicePorts(),
			Selector:  DataCenterLabels(rctx.cdc),
		}

		if err := controllerutil.SetControllerReference(rctx.cdc, prometheusService, rctx.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	// Only log if something has changed
	if opresult != controllerutil.OperationResultNone {
		logger.Info(fmt.Sprintf("Service %s %s.", prometheusService.Name, opresult))
	}

	return prometheusService, err
}

func createOrUpdateNodesService(rctx *reconciliationRequestContext) (*corev1.Service, error) {
	nodesService := &corev1.Service{ObjectMeta: DataCenterResourceMetadata(rctx.cdc, "nodes")}

	logger := rctx.logger.WithValues("Service.Name", nodesService.Name)

	opresult, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, nodesService, func(_ runtime.Object) error {
		nodesService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports:     ports{cqlPort, jmxPort}.asServicePorts(),
			Selector:  DataCenterLabels(rctx.cdc),
		}

		if err := controllerutil.SetControllerReference(rctx.cdc, nodesService, rctx.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	// Only log if something has changed
	if opresult != controllerutil.OperationResultNone {
		logger.Info(fmt.Sprintf("Service %s %s.", nodesService.Name, opresult))
	}

	return nodesService, err
}

func createOrUpdateSeedNodesService(rctx *reconciliationRequestContext) (*corev1.Service, error) {
	seedNodesService := &corev1.Service{ObjectMeta: DataCenterResourceMetadata(rctx.cdc, "seeds")}

	logger := rctx.logger.WithValues("Service.Name", seedNodesService.Name)

	opresult, err := controllerutil.CreateOrUpdate(context.TODO(), rctx.client, seedNodesService, func(_ runtime.Object) error {
		seedNodesService.Spec = corev1.ServiceSpec{
			ClusterIP:                "None",
			Ports:                    internodePort.asServicePorts(),
			Selector:                 DataCenterLabels(rctx.cdc),
			PublishNotReadyAddresses: true,
		}

		seedNodesService.Annotations = map[string]string{
			"service.alpha.kubernetes.io/tolerate-unready-endpoints": "true",
		}

		if err := controllerutil.SetControllerReference(rctx.cdc, seedNodesService, rctx.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	// Only log if something has changed
	if opresult != controllerutil.OperationResultNone {
		logger.Info(fmt.Sprintf("Service %s %s.", seedNodesService.Name, opresult))
	}

	return seedNodesService, err
}

func createOrUpdatePrometheusServiceMonitor(rctx *reconciliationRequestContext) error {
	cfg, err := config.GetConfig()
	if err != nil {
		return err
	}

	prometheusService, err := createOrUpdatePrometheusService(rctx)
	if err != nil {
		return err
	}

	if _, err := metrics.CreateServiceMonitors(cfg, rctx.cdc.Namespace, []*corev1.Service{prometheusService}); err != nil {
		// If this operator is deployed to a cluster without the prometheus-operator running, it will return
		// ErrServiceMonitorNotPresent, which can be used to safely skip ServiceMonitor creation.
		if err == metrics.ErrServiceMonitorNotPresent {
			log.Info("Install prometheus-operator in your cluster to create ServiceMonitor objects", "error", err.Error())
			return nil
		} else if errors.IsAlreadyExists(err) {
			// TODO: maybe recreate?
			log.Info(fmt.Sprintf("ServiceMonitor %v already exists, skipping creation", prometheusService.Name))
			return nil
		} else {
			log.Info("Could not create ServiceMonitor object", "error", err.Error())
			return err
		}
	} else {
		log.Info(fmt.Sprintf("Created ServiceMonitor object for %v", prometheusService.Name))
	}

	return nil
}

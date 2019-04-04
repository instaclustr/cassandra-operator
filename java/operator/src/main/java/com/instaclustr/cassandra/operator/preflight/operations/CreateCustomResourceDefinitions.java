package com.instaclustr.cassandra.operator.preflight.operations;

import com.instaclustr.cassandra.operator.preflight.Operation;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CreateCustomResourceDefinitions implements Operation {
    static final Logger logger = LoggerFactory.getLogger(CreateCustomResourceDefinitions.class);

    private final ApiextensionsV1beta1Api apiExtensionsApi;

    @Inject
    public CreateCustomResourceDefinitions(final ApiextensionsV1beta1Api apiExtensionsApi) {
        this.apiExtensionsApi = apiExtensionsApi;
    }

    @Override
    public void run() throws Exception {
        createCrdFromResource("/com/instaclustr/datacenter-crd.yaml");
        createCrdFromResource("/com/instaclustr/cluster-crd.yaml");
        createCrdFromResource("/com/instaclustr/backup-crd.yaml");
    }

    private void createCrdFromResource(final String resourceName) throws ApiException, IOException {
        try (final InputStream resourceStream = CreateCustomResourceDefinitions.class.getResourceAsStream(resourceName);
             final InputStreamReader resourceReader = new InputStreamReader(resourceStream);) {

            final V1beta1CustomResourceDefinition crdDefinition = Yaml.loadAs(resourceReader, V1beta1CustomResourceDefinition.class);

            final String crdName = crdDefinition.getMetadata().getName();

            logger.info("Creating Custom Resource Definition {}", crdName);

            try {
                apiExtensionsApi.createCustomResourceDefinition(crdDefinition, null, null, null);

            } catch (final ApiException e) {
                if (e.getCode() == 409) { // HTTP 409 CONFLICT

                    logger.info("Custom Resource Definition {} already exists.", crdName);

                    return;
                }

                throw e;
            }
        }
    }
}

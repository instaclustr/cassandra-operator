package com.instaclustr.cassandra.operator.preflight.operations;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.instaclustr.cassandra.operator.App;
import com.instaclustr.cassandra.operator.preflight.Operation;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CreateCustomResourceDefinitions implements Operation {

    private final ApiextensionsV1beta1Api apiExtensionsApi;

    public static <T> T jsonResourceAsObject(final String name, final Class<T> clazz) throws IOException {
        final Gson gson = new Gson();

        try (final InputStream resource = App.class.getResourceAsStream(name);
             final JsonReader reader = new JsonReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, clazz);
        }
    }

    @Inject
    public CreateCustomResourceDefinitions(final ApiextensionsV1beta1Api apiExtensionsApi) {
        this.apiExtensionsApi = apiExtensionsApi;
    }

    @Override
    public void run() throws Exception {
        apiExtensionsApi.createCustomResourceDefinition(null, null);
    }
}

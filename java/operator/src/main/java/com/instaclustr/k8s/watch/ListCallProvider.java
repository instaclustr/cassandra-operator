package com.instaclustr.k8s.watch;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiException;

public interface ListCallProvider {

    Call get(final String resourceVersion, final boolean watch) throws ApiException;

}

package com.instaclustr.cassandra.operator.controller;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;

public interface DataCenterControllerFactory {
    DataCenterReconciliationController reconciliationControllerForDataCenter(final DataCenter dataCenter);
    DataCenterDeletionController deletionControllerForDataCenter(final DataCenterKey dataCenter);
}

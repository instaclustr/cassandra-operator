package com.instaclustr.cassandra.sidecar.model;

public class BackupResponse {
    private String status;

    public BackupResponse() {};

    public BackupResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

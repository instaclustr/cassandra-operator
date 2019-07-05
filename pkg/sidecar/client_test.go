package sidecar

import (
	"bytes"
	"fmt"
	"github.com/go-resty/resty"
	"github.com/google/uuid"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"gotest.tools/assert"
	"io/ioutil"
	"net/http"
	"os"
	"testing"
)

func getHost() string {

	host := os.Getenv("TEST_SIDECAR_HOST")

	if len(host) == 0 {
		return "127.0.0.1"
	} else {
		return host
	}
}

func TestDemarshalling(t *testing.T) {

	// status

	client := NewSidecarClient(getHost(), &DefaultSidecarClientOptions)

	client.testMode = true

	client.testResponse = &resty.Response{
		RawResponse: &http.Response{
			Body:       ioutil.NopCloser(bytes.NewReader([]byte(`{ "operationMode": "NORMAL"}`))),
			Status:     "200 OK",
			StatusCode: 200,
		},
	}

	if status, err := client.Status(); err != nil || status == nil || status.NodeState != NORMAL {
		t.Fail()
	}

	// list operations

	client.testResponse = &resty.Response{
		RawResponse: &http.Response{
			Body: ioutil.NopCloser(bytes.NewReader([]byte(
				`[
					{
						"type": "decommission",
						"id": "d3262073-8101-450f-9a11-c851760abd57",
						"duration": "PT1M0.613S",
						"start": "2019-06-11T03:37:15.593Z",
						"stop": "2019-06-11T03:38:16.206Z",
						"state": "FINISHED"
    				},
					{
						"type": "backup",
						"id": "d3262073-8101-450f-9a11-c851760abd57",
						"duration": "PT1M0.613S",
						"start": "2019-06-11T03:37:15.593Z",
						"stop": "2019-06-11T03:38:16.206Z",
						"state": "RUNNING"
    				}
				]`))),
			Status:     "200 OK",
			StatusCode: 200,
		},
	}

	// get operations

	ops, err := client.GetOperations()
	if err != nil {
		t.Error(err)
	}

	backups, err := FilterOperations(*ops, backup)
	assert.Assert(t, len(backups) == 1)

	decommissions, err := FilterOperations(*ops, decommission)
	assert.Assert(t, len(decommissions) == 1)

	// get operation

	client.testResponse = &resty.Response{
		RawResponse: &http.Response{
			Body: ioutil.NopCloser(bytes.NewReader([]byte(
				`{
					"type": "backup",
					"id": "d3262073-8101-450f-9a11-c851760abd57",
					"duration": "PT1M0.613S",
					"start": "2019-06-11T03:37:15.593Z",
					"stop": "2019-06-11T03:38:16.206Z",
					"state": "RUNNING"
				}`))),
			Status:     "200 OK",
			StatusCode: 200,
		},
	}

	op, err := client.GetOperation(uuid.MustParse("d3262073-8101-450f-9a11-c851760abd57"))

	if err != nil {
		t.Error(err)
	}

	if (*op)["state"] != "RUNNING" {
		t.Errorf("testing backing should return RUNNING state")
	}
}

func TestSidecarClient_GetStatus(t *testing.T) {

	client := NewSidecarClient(getHost(), &DefaultSidecarClientOptions)

	status, e := client.Status()

	if e != nil {
		t.Errorf(e.Error())
	}

	if status == nil {
		t.Errorf("Status endpoint has not returned error but its status is not set.")
	}

	if status.NodeState != NORMAL {
		t.Fatalf("Expected NORMAL operation mode but received %v", status.NodeState)
	}
}

func TestClient_DecommissionNode(t *testing.T) {

	client := NewSidecarClient(getHost(), &DefaultSidecarClientOptions)

	// first decommissioning

	if operationId, err := client.Decommission(); err != nil {
		t.Errorf(err.Error())
	} else if operationId == nil || (*operationId) == uuid.Nil {
		t.Errorf("there is not any error from Decommission endpoint but operationId is nil")
	} else if getOpResponse, err := client.GetOperation(*operationId); err != nil {
		t.Errorf(err.Error())
	} else {
		assert.Assert(t, (*getOpResponse)["state"] == operations.RUNNING)
	}

	// second decommissioning on the same node

	response2, err2 := client.Decommission()

	if err2 == nil || response2 != nil {
		t.Errorf("Decommissioning of already decomissioned node should fail.")
	}
}

func TestClient_BackupNode(t *testing.T) {

	client := NewSidecarClient(getHost(), &DefaultSidecarClientOptions)

	backups, _ := client.ListBackups()
	fmt.Println(backups[0])

	request := &BackupOperation{
		DestinationUri: "/tmp/backup_test",
		Keyspaces:      []string{"test_keyspace"},
		SnapshotName:   "testSnapshot",
	}

	if operationID, err := client.Backup(request); err != nil {
		t.Errorf(err.Error())
	} else if getOpResponse, err := client.GetOperation(operationID); err != nil {
		t.Errorf(err.Error())
	} else {
		assert.Assert(t, (*getOpResponse)["state"] == "RUNNING")
	}
}

func TestClient_CleanupNode(t *testing.T) {

	client := NewSidecarClient(getHost(), &DefaultSidecarClientOptions)

	cleanups, _ := client.ListCleanups()
	fmt.Print(cleanups[0])

	request := &CleanupOperation{
		Keyspace: "test",
		Tables:   []string{"mytable", "mytable2"},
	}

	if response, err := client.Cleanup(request); err != nil {
		t.Errorf(err.Error())
	} else if response == nil {
		t.Errorf("there is not any error from Cleanup endpoint but response is nil")
	} else if getOpResponse, err := client.GetOperation(*response); err != nil {
		t.Errorf(err.Error())
	} else {
		assert.Assert(t, (*getOpResponse)["state"] == "COMPLETED")
	}
}

package sidecar

import (
	"bytes"
	"github.com/go-resty/resty"
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

	if status, err := client.Status(); err != nil || status == nil || status.OperationMode != OPERATION_MODE_NORMAL {
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
						"operationState": "FINISHED"
    				},
					{
						"type": "backup",
						"id": "d3262073-8101-450f-9a11-c851760abd57",
						"duration": "PT1M0.613S",
						"start": "2019-06-11T03:37:15.593Z",
						"stop": "2019-06-11T03:38:16.206Z",
						"operationState": "RUNNING"
    				}
				]`))),
			Status:     "200 OK",
			StatusCode: 200,
		},
	}

	// get operations

	ops, err := client.GetOperations();
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
					"operationState": "RUNNING"
				}`))),
			Status:     "200 OK",
			StatusCode: 200,
		},
	}

	op, err := client.GetOperation("d3262073-8101-450f-9a11-c851760abd57")

	if err != nil {
		t.Error(err)
	}

	if (*op)["operationState"] != "RUNNING" {
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

	if status.OperationMode != OPERATION_MODE_NORMAL {
		t.Fatalf("Expected NORMAL operation mode but received %v", status.OperationMode)
	}
}

func TestClient_DecommissionNode(t *testing.T) {

	client := NewSidecarClient(getHost(), &DefaultSidecarClientOptions)

	// first decommissioning

	if response, err := client.Decommission(); err != nil {
		t.Errorf(err.Error())
	} else if response == nil {
		t.Errorf("there is not any error from Decommission endpoint but response is nil")
	} else if getOpResponse, err := client.GetOperation(*response); err != nil {
		t.Errorf(err.Error())
	} else {
		assert.Assert(t, (*getOpResponse)["operationState"] == "RUNNING")
	}

	// second decommissioning on the same node

	response2, err2 := client.Decommission()

	if err2 == nil || response2 != nil {
		t.Errorf("Decommissioning of already decomissioned node should fail.")
	}
}

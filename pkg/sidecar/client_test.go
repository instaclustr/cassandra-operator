package sidecar

import (
	"os"
	"testing"
)

func getHost() string {

	host := os.Getenv("TEST_SIDECAR_HOST")

	if len(host) == 0 {
		return "192.168.56.104"
	} else {
		return host
	}
}

func TestSidecarClient_GetStatus(t *testing.T) {

	client := NewSidecarClient(getHost(), &ClientOptions{Secure: false, Port: 4567})

	response, e := client.GetStatus()

	if e != nil {
		t.Errorf(e.Error())
	} else if response == nil {
		t.Errorf("GetStatus has not returned error but its response is not set.")
	} else if response.OperationMode != "NORMAL" {
		t.Errorf("GetStatus was successful but returned %s instead of %s", response.OperationMode, "NORMAL")
	}
}

func TestClient_DecommissionNode(t *testing.T) {

	client := NewSidecarClient(getHost(), &ClientOptions{Secure: false, Port: 4567})

	// first decommissioning

	response, err := client.DecommissionNode()

	if err != nil {
		t.Errorf(err.Error())
	}

	if response == nil {
		t.Errorf("Decommissioning was successful but no DecommissionResponse was returned.")
	}

	// second decommissioning on the same node

	response2, err2 := client.DecommissionNode()

	if err2 == nil || response2 != nil {
		t.Errorf("Decommissioning of already decomissioned node should fail.")
	}
}

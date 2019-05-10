package sidecar

import (
	"os"
	"testing"
)

func getHost() string {

	host := os.Getenv("TEST_SIDECAR_HOST")

	if len(host) == 0 {
		return "192.168.56.104:4567"
	} else {
		return host
	}
}

func TestSidecarClient_GetStatus(t *testing.T) {

	client := NewSidecarClient(getHost(), &ClientOptions{Secure: false})

	response, e := client.GetStatus()

	if e != nil {
		println(e.Error())
		t.Fail()
	}

	if response.OperationMode != NORMAL {
		t.Fail()
	}
}

func TestClient_DecommissionNode(t *testing.T) {

	client := NewSidecarClient(getHost(), &ClientOptions{Secure: false})

	_, e := client.DecommissionNode()

	if e != nil {
		println(e.Error())
		t.Fail()
	}
}

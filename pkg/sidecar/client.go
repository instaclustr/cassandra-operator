package sidecar

import (
	"errors"
	"fmt"
	"github.com/go-resty/resty"
)

const (
	ACCEPT                           = "Accept"
	APPLICATION_JSON                 = "application/json"
	ENDPOINT_OPERATIONS_DECOMMISSION = "operations/decommission"
	ENDPOINT_STATUS                  = "status"
)

//////////////
//// BACKUPS
//////////////

type BackupArguments struct {
}

type BackupResponse struct {
}

type Backups interface {
	CreateBackup(arguments BackupArguments) (*BackupResponse, error)
}

func (client *Client) CreateBackup(arguments BackupArguments) (*BackupResponse, error) {
	return &BackupResponse{}, nil
}

/////////////////
//// OPERATIONS
/////////////////

type DecommissionResponse struct {
}

type Operations interface {
	DecommissionNode() (*DecommissionResponse, error)
}

func (client *Client) DecommissionNode() (*DecommissionResponse, error) {

	decommissionResponse := &DecommissionResponse{}

	response, err := client.restyClient.R().
		SetHeader(ACCEPT, APPLICATION_JSON).
		SetResult(&decommissionResponse).
		Post(ENDPOINT_OPERATIONS_DECOMMISSION)

	if err != nil {
		return nil, err
	}

	if response.IsSuccess() {
		return decommissionResponse, nil
	} else {
		return nil, errors.New(fmt.Sprintf("DecommissionNode returned HTTP response code %s", response.Status()))
	}
}

/////////////
//// STATUS
/////////////

type OperationMode string

const (
	STARTING       = OperationMode("STARTING")
	NORMAL         = OperationMode("NORMAL")
	JOINING        = OperationMode("JOINING")
	LEAVING        = OperationMode("LEAVING")
	DECOMMISSIONED = OperationMode("DECOMMISSIONED")
	MOVING         = OperationMode("MOVING")
	DRAINING       = OperationMode("DRAINING")
	DRAINED        = OperationMode("DRAINED")
)

type StatusResponse struct {
	OperationMode OperationMode
}

type Statuses interface {
	GetStatus() (*StatusResponse, error)
}

func (client *Client) GetStatus() (*StatusResponse, error) {

	statusResponse := &StatusResponse{}

	response, err := client.restyClient.R().
		SetHeader(ACCEPT, APPLICATION_JSON).
		SetResult(&statusResponse).
		Get(ENDPOINT_STATUS)

	if err != nil {
		return nil, err
	}

	if response.IsSuccess() {
		return statusResponse, nil
	} else {
		return nil, errors.New(fmt.Sprintf("GetStatus returned HTTP response code %s", response.Status()))
	}
}

////////////
//// CLIENT
////////////

func NewSidecarClient(host string, options *ClientOptions) *Client {

	if options == nil {
		options = &ClientOptions{
			Secure:   true,
			HttpMode: false,
		}
	}

	client := &Client{}
	client.Host = host
	client.Options = options

	restyClient := resty.DefaultClient

	var protocol = "https"

	if !client.Options.Secure {
		protocol = "http"
	}

	restyClient.SetHostURL(fmt.Sprintf("%s://%s", protocol, client.Host))

	if client.Options.HttpMode {
		restyClient.SetHTTPMode()
	}

	client.restyClient = restyClient

	return client
}

type ClientOptions struct {
	Secure   bool
	HttpMode bool
}

type Client struct {
	Host        string
	Options     *ClientOptions
	restyClient *resty.Client
}

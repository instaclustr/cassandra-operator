package sidecar

import (
	"fmt"
	"github.com/go-resty/resty"
)

const (
	ACCEPT                         = "Accept"
	ApplicationJson                = "application/json"
	EndpointOperationsDecommission = "operations/decommission"
	EndpointStatus                 = "status"
)

////////////
//// CLIENT
////////////

type Client struct {
	Host        string
	Options     *ClientOptions
	restyClient *resty.Client
}

type ClientOptions struct {
	Secure   bool
	HttpMode bool
}

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

type DecommissionError struct {
	status   int
	msg      string
	endpoint string
}

func (e *DecommissionError) Error() string {
	return convertErrorToString(e.endpoint, e.status, e.msg)
}

type DecommissionResponse struct {
}

type Operations interface {
	DecommissionNode() (*DecommissionResponse, error)
}

func (client *Client) DecommissionNode() (*DecommissionResponse, error) {

	decommissionResponse := &DecommissionResponse{}

	response, err := client.restyClient.R().
		SetHeader(ACCEPT, ApplicationJson).
		SetResult(&decommissionResponse).
		Post(EndpointOperationsDecommission)

	if err != nil {
		return nil, &DecommissionError{
			msg:      err.Error(),
			endpoint: EndpointOperationsDecommission,
		}
	}

	if !response.IsSuccess() {
		return nil, &DecommissionError{
			status:   response.StatusCode(),
			endpoint: EndpointOperationsDecommission,
		}
	}

	return decommissionResponse, nil
}

/////////////
//// STATUS
/////////////

type StatusError struct {
	status   int
	msg      string
	endpoint string
}

func (e *StatusError) Error() string {
	return convertErrorToString(e.endpoint, e.status, e.msg)
}

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
		SetHeader(ACCEPT, ApplicationJson).
		SetResult(&statusResponse).
		Get(EndpointStatus)

	if err != nil {
		return nil, &StatusError{
			msg:      err.Error(),
			endpoint: EndpointStatus,
		}
	}

	if !response.IsSuccess() {
		return nil, &StatusError{
			status:   response.StatusCode(),
			endpoint: EndpointStatus,
		}
	}

	return statusResponse, nil
}

//// helpers

func convertErrorToString(endpoint string, status int, msg string) string {
	if status != 0 {
		return fmt.Sprintf("Operation on endpoint %s was not successful, response code %d", endpoint, status)
	}

	return fmt.Sprintf("Operation on endpoint %s was errorneous: %s", endpoint, msg)
}

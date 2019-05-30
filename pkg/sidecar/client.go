package sidecar

import (
	"fmt"
	"github.com/go-resty/resty"
	corev1 "k8s.io/api/core/v1"
	"strconv"
	"time"
)

const (
	accept                         = "Accept"
	applicationJson                = "application/json"
	EndpointOperationsDecommission = "operations/decommission"
	EndpointStatus                 = "status"
)

var DefaultSidecarClientOptions = ClientOptions{Port: 4567, Secure: false,}

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
	Port     int
	Timeout  time.Duration
}

func NewSidecarClient(host string, options *ClientOptions) *Client {

	if options == nil {
		options = &ClientOptions{
			Secure:   true,
			HttpMode: false,
		}
	}

	if options.Timeout == 0 {
		options.Timeout = 1 * time.Minute
	}

	client := &Client{}
	client.Host = host
	client.Options = options

	restyClient := resty.New()

	var protocol = "https"

	if !client.Options.Secure {
		protocol = "http"
	}

	var port = ""

	if options.Port != 0 {
		port = ":" + strconv.FormatInt(int64(options.Port), 10)
	}

	restyClient.SetHostURL(fmt.Sprintf("%s://%s%s", protocol, client.Host, port))

	if client.Options.HttpMode {
		restyClient.SetHTTPMode()
	}

	restyClient.SetTimeout(client.Options.Timeout)

	client.restyClient = restyClient

	return client
}

func SidecarClients(pods []corev1.Pod, clientOptions ClientOptions) map[*corev1.Pod]*Client {

	podClients := make(map[*corev1.Pod]*Client)

	for _, pod := range pods {
		key := pod
		podClients[&key] = NewSidecarClient(pod.Status.PodIP, &clientOptions)
	}

	return podClients
}

func ClientFromPods(podsClients map[*corev1.Pod]*Client, pod corev1.Pod) *Client {

	for key, value := range podsClients {
		if key.Name == pod.Name {
			return value
		}
	}

	return nil
}

func ClientForPod(pods []corev1.Pod, podName string) *Client {

	for _, pod := range pods {
		if pod.Name == podName {
			return NewSidecarClient(pod.Status.PodIP, &DefaultSidecarClientOptions)
		}
	}

	return nil
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
	err      error
	endpoint string
}

func (e *DecommissionError) Error() string {
	return convertErrorToString(e.endpoint, e.status, e.err)
}

type DecommissionResponse struct {
}

type Operations interface {
	DecommissionNode() (*DecommissionResponse, error)
}

func (client *Client) DecommissionNode() (*DecommissionResponse, error) {

	decommissionResponse := &DecommissionResponse{}

	response, err := client.restyClient.R().
		SetHeader(accept, applicationJson).
		SetResult(decommissionResponse).
		Post(EndpointOperationsDecommission)

	if err != nil {
		return nil, &DecommissionError{
			err:      err,
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
	err      error
	endpoint string
}

func (e *StatusError) Error() string {
	return convertErrorToString(e.endpoint, e.status, e.err)
}

type OperationMode string

const (
	OPERATION_MODE_STARTING       = OperationMode("STARTING")
	OPERATION_MODE_NORMAL         = OperationMode("NORMAL")
	OPERATION_MODE_JOINING        = OperationMode("JOINING")
	OPERATION_MODE_LEAVING        = OperationMode("LEAVING")
	OPERATION_MODE_DECOMMISSIONED = OperationMode("DECOMMISSIONED")
	OPERATION_MODE_MOVING         = OperationMode("MOVING")
	OPERATION_MODE_DRAINING       = OperationMode("DRAINING")
	OPERATION_MODE_DRAINED        = OperationMode("DRAINED")
	OPERATION_MODE_ERROR          = OperationMode("ERROR")
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
		SetHeader(accept, applicationJson).
		SetResult(statusResponse).
		Get(EndpointStatus)

	if err != nil {
		return nil, &StatusError{
			err:      err,
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

func convertErrorToString(endpoint string, status int, err error) string {
	if status != 0 {
		return fmt.Sprintf("Operation on endpoint %s was not successful, response code %d", endpoint, status)
	}

	return fmt.Sprintf("Operation on endpoint %s was errorneous: %s", endpoint, err)
}

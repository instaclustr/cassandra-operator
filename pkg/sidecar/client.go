package sidecar

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/go-resty/resty"
	corev1 "k8s.io/api/core/v1"
	"strconv"
	"strings"
	"time"
)

//go:generate jsonenums -type=Kind

const (
	accept             = "Accept"
	applicationJson    = "application/json"
	EndpointOperations = "operations"
	EndpointStatus     = "status"
)

var DefaultSidecarClientOptions = ClientOptions{Port: 4567, Secure: false,}

type Client struct {
	Host    string
	Options *ClientOptions

	restyClient  *resty.Client
	testResponse *resty.Response
	testMode     bool
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

type HttpResponse struct {
	status       int
	err          error
	responseBody string
}

func (e *HttpResponse) Error() string {
	if e.status != 0 {
		return fmt.Sprintf("Operation was not successful, response code %d", e.status)
	}

	return fmt.Sprintf("Operation was errorneous: %s", e.err)
}

type Kind int

const (
	cleanup Kind = iota
	upgradesstables
	decommission
	backup
)

type httpVerb int

const (
	GET httpVerb = iota
	POST
)

type Operation struct {
	Type Kind   `json:"type"`
	Id   string `json:"id"`
}

/// RESPONSES

type Status struct {
	OperationMode OperationMode `json:"operationMode"`
}

type DecommissionOperationResponse struct {
	Operation
}

type CleanupOperationResponse struct {
	Operation
}

type BackupResponse struct {
	Operation
}

type UpgradeSSTablesResponse struct {
	Operation
}

/// OPERATIONS

type DecommissionOperation struct {
	Operation
}

type CleanupOperation struct {
	Operation
}

type BackupOperation struct {
	Operation
}

type UpgradeSSTablesOperation struct {
	Operation
}

type GetOperationsResponse []GetOperationResponse
type GetOperationResponse map[string]interface{}

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

func (client *Client) Status() (*Status, error) {

	if r, err := client.performRequest(EndpointStatus, GET, nil); requestInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if status, err := unmarshallBody(body, r, &Status{}); err == nil {
			return status.(*Status), nil
		} else {
			return nil, err
		}
	}
}

func (client *Client) Decommission() (*string, error) {

	if r, err := client.performRequest(EndpointOperations, POST, &DecommissionOperation{Operation{Type: decommission}}); requestInvalid(r, err) {
		return nil, err
	} else {
		location := parseLocation(r)
		return &location, nil
	}
}

func (client *Client) Cleanup() (*string, error) {

	if r, err := client.performRequest(EndpointOperations, POST, CleanupOperation{Operation{Type: cleanup}}); requestInvalid(r, err) {
		return nil, err
	} else {
		location := parseLocation(r)
		return &location, nil
	}
}

func (client *Client) Backup(request BackupOperation) (*string, error) {

	request.Type = backup

	if r, err := client.performRequest(EndpointOperations, POST, request); requestInvalid(r, err) {
		return nil, err
	} else {
		location := parseLocation(r)
		return &location, nil
	}
}

func (client *Client) GetOperation(id string) (*GetOperationResponse, error) {

	splits := strings.Split(id, "/")

	operation := strings.Split(id, "/")[len(splits)-1]

	if r, err := client.performRequest(EndpointOperations+"/"+operation, GET, nil); requestInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if status, err := unmarshallBody(body, r, &GetOperationResponse{}); err != nil {
			return status.(*GetOperationResponse), nil
		} else {
			return nil, err
		}
	}
}

func (client *Client) GetOperations() (*[]GetOperationResponse, error) {
	if r, err := client.performRequest(EndpointOperations, GET, nil); requestInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if response, err := unmarshallBody(body, r, &[]GetOperationResponse{}); err == nil {
			return response.(*[]GetOperationResponse), nil
		} else {
			return nil, err
		}
	}
}

func (client *Client) ListBackups() ([]BackupOperation, error) {

	ops, _ := client.GetOperations()

	operations, err := FilterOperations(*ops, backup)

	if err != nil {

	}

	var backups []BackupOperation

	for _, op := range operations {
		backups = append(backups, op.(BackupOperation))
	}

	return backups, nil
}

func (client *Client) UpgradeSSTables(request UpgradeSSTablesOperation) (*string, error) {

	request.Type = upgradesstables

	if r, err := client.performRequest(EndpointOperations, POST, request); requestInvalid(r, err) {
		return nil, err
	} else {
		location := parseLocation(r)
		return &location, nil
	}
}

func FilterOperations(ops GetOperationsResponse, _type Kind) ([]interface{}, error) {

	var result = make([]interface{}, 0)

	for _, item := range ops {

		if item["type"].(string) == _KindValueToName[_type] {

			var op interface{}

			switch _type {
			case cleanup:
				op = &CleanupOperation{}
			case upgradesstables:
				op = &UpgradeSSTablesOperation{}
			case decommission:
				op = &DecommissionOperation{}
			case backup:
				op = &BackupOperation{}
			default:
				continue
			}

			if body, err := json.Marshal(item); err != nil {
				return nil, err
			} else if err := json.Unmarshal(body, op); err != nil {
				return nil, err
			} else {
				result = append(result, op)
			}
		}
	}

	return result, nil
}

func requestInvalid(r interface{}, err error) bool {
	return r == nil || err != nil
}

func (client *Client) performRequest(endpoint string, verb httpVerb, requestBody interface{}) (response *resty.Response, err error) {

	if client.testMode {
		return client.testResponse, nil
	}

	request := client.restyClient.R().SetHeader(accept, applicationJson)

	if verb == POST {
		response, err = request.SetBody(requestBody).Post(endpoint)
	} else if verb == GET {
		response, err = request.Get(endpoint)
	}

	if err != nil {
		return nil, &HttpResponse{
			err: err,
		}
	}

	return
}

func parseLocation(response *resty.Response) string {
	return response.Header().Get("Location")
}

func readBody(response *resty.Response) (*[]byte, error) {

	var rawBody = response.RawBody()

	defer rawBody.Close()

	var body []byte

	if rawBody != nil {

		buf := new(bytes.Buffer)

		if _, err := buf.ReadFrom(rawBody); err != nil {
			return nil, err
		}

		body = buf.Bytes()
	}

	return &body, nil
}

func unmarshallBody(body *[]byte, response *resty.Response, responseEnvelope interface{}) (interface{}, error) {

	if err := json.Unmarshal(*body, responseEnvelope); err != nil {
		return nil, &HttpResponse{
			err:          err,
			responseBody: string(*body),
		}
	}

	if !response.IsSuccess() {
		return nil, &HttpResponse{
			status: response.StatusCode(),
		}
	}

	return responseEnvelope, nil
}

package sidecar

import (
	"encoding/json"
	"fmt"
	"github.com/go-resty/resty"
	"github.com/google/uuid"
	"github.com/instaclustr/cassandra-operator/pkg/common/nodestate"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	corev1 "k8s.io/api/core/v1"
	"net/http"
	"strconv"
	"strings"
	"time"
)

//go:generate jsonenums -type=Kind

const (
	accept             = "Accept"
	applicationJson    = "application/json; charset=utf-8"
	EndpointOperations = "operations"
	EndpointStatus     = "status"
)

var DefaultSidecarClientOptions = ClientOptions{Port: 4567, Secure: false}

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

func SidecarClients(pods []corev1.Pod, clientOptions *ClientOptions) map[*corev1.Pod]*Client {

	podClients := make(map[*corev1.Pod]*Client)

	for i, pod := range pods {
		podClients[&pods[i]] = NewSidecarClient(pod.Status.PodIP, clientOptions)
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

/// OPERATIONS

type Kind int

const (
	cleanup Kind = iota
	upgradesstables
	decommission
	backup
)

type Operation struct {
	Type Kind `json:"type"`
}

type DecommissionOperation struct {
	Operation
}

type CleanupOperation struct {
	Operation
	Keyspace string   `json:"keyspace"`
	Tables   []string `json:"tables"`
}

type BackupOperation struct {
	Operation
	DestinationUri string   `json:"destinationUri"`
	Keyspaces      []string `json:"keyspaces"`
	SnapshotName   string   `json:"snapshotName"`
}

type UpgradeSSTablesOperation struct {
	Operation
}

type Operations []OperationResponse
type OperationResponse map[string]interface{}
type BasicResponse struct {
	Id             uuid.UUID                 `json:"id"`
	CreationTime   time.Time                 `json:"creationTime"`
	State          operations.OperationState `json:"state"`
	Progress       float32                   `json:"progress"`
	StartTime      time.Time                 `json:"startTime"`
	CompletionTime time.Time                 `json:"completionTime"`
}

/// RESPONSES

type DecommissionOperationResponse struct {
	BasicResponse
}

type CleanupOperationResponse struct {
	BasicResponse
	CleanupOperation
}

type BackupResponse struct {
	BasicResponse
	BackupOperation
}

func (b *BackupResponse) String() string {
	op, _ := json.Marshal(b)
	return string(op)
}

func (c *CleanupOperationResponse) String() string {
	op, _ := json.Marshal(c)
	return string(op)
}

type UpgradeSSTablesResponse struct {
	Operation
}

func (client *Client) Status() (*nodestate.Status, error) {

	if r, err := client.performRequest(EndpointStatus, http.MethodGet, nil); responseInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if status, err := unmarshallBody(body, r, &nodestate.Status{}); err == nil {
			return status.(*nodestate.Status), nil
		} else {
			return nil, err
		}
	}
}

func (client *Client) Decommission() (*uuid.UUID, error) {

	// TODO: atm decommission operation doesn't need params, so will send an empty request.
	// This may change in the future.

	request := &DecommissionOperation{Operation{Type: decommission}}

	if r, err := client.performRequest(EndpointOperations, http.MethodPost, request); responseInvalid(r, err) {
		return nil, err
	} else {
		operationId, err := parseOperationId(r)
		if err != nil {
			return nil, err
		}
		return &operationId, nil
	}
}

func (client *Client) Cleanup(request *CleanupOperation) (*uuid.UUID, error) {

	request.Type = cleanup

	if r, err := client.performRequest(EndpointOperations, http.MethodPost, request); responseInvalid(r, err) {
		return nil, err
	} else {
		operationId, err := parseOperationId(r)
		if err != nil {
			return nil, err
		}
		return &operationId, nil
	}
}

func (client *Client) Backup(request *BackupOperation) (*uuid.UUID, error) {

	request.Type = backup

	if r, err := client.performRequest(EndpointOperations, http.MethodPost, request); responseInvalid(r, err) {
		return nil, err
	} else {
		operationId, err := parseOperationId(r)
		if err != nil {
			return nil, err
		}
		return &operationId, nil
	}
}

func (client *Client) GetOperation(id uuid.UUID) (*OperationResponse, error) {

	if id == uuid.Nil {
		return nil, fmt.Errorf("getOperation must get a valid id")
	}
	endpoint := EndpointOperations + "/" + id.String()

	if r, err := client.performRequest(endpoint, http.MethodGet, nil); responseInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if status, err := unmarshallBody(body, r, &OperationResponse{}); err != nil {
			return nil, err
		} else {
			return status.(*OperationResponse), nil
		}
	}
}

func (client *Client) GetOperations() (*[]OperationResponse, error) {
	if r, err := client.performRequest(EndpointOperations, http.MethodGet, nil); responseInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if response, err := unmarshallBody(body, r, &[]OperationResponse{}); err == nil {
			return response.(*[]OperationResponse), nil
		} else {
			return nil, err
		}
	}
}

func (client *Client) ListCleanups() ([]*CleanupOperationResponse, error) {

	ops, err := client.GetOperations()
	if ops == nil || err != nil {
		return []*CleanupOperationResponse{}, err
	}

	operations, err := FilterOperations(*ops, cleanup)
	if err != nil {
		//??
	}

	var cleanups []*CleanupOperationResponse
	for _, op := range operations {
		cleanups = append(cleanups, op.(*CleanupOperationResponse))
	}

	return cleanups, nil
}

func (client *Client) ListBackups() ([]*BackupResponse, error) {

	ops, err := client.GetOperations()
	if ops == nil || err != nil {
		return []*BackupResponse{}, nil
	}

	operations, err := FilterOperations(*ops, backup)
	if err != nil {
		// ?
	}

	var backups []*BackupResponse
	for _, op := range operations {
		backups = append(backups, op.(*BackupResponse))
	}

	return backups, nil
}

func (client *Client) UpgradeSSTables(request *UpgradeSSTablesOperation) (*uuid.UUID, error) {

	request.Type = upgradesstables

	if r, err := client.performRequest(EndpointOperations, http.MethodPost, request); responseInvalid(r, err) {
		return nil, err
	} else {
		operationId, err := parseOperationId(r)
		if err != nil {
			return nil, err
		}
		return &operationId, nil
	}
}

func FilterOperations(ops Operations, kind Kind) ([]interface{}, error) {

	var result = make([]interface{}, 0)

	for _, item := range ops {

		if item["type"].(string) == _KindValueToName[kind] {

			var op interface{}

			switch kind {
			case cleanup:
				op = &CleanupOperationResponse{}
			case upgradesstables:
				op = &UpgradeSSTablesResponse{}
			case decommission:
				op = &DecommissionOperationResponse{}
			case backup:
				op = &BackupResponse{}
			default:
				continue
			}

			if body, err := json.Marshal(item); err != nil {
				// Log error
				continue
			} else if err := json.Unmarshal(body, op); err != nil {
				// Log error
				continue
			} else {
				result = append(result, op)
			}
		}
	}

	return result, nil
}

func responseInvalid(r interface{}, err error) bool {
	return r == nil || err != nil
}

func (client *Client) performRequest(endpoint string, verb string, requestBody interface{}) (response *resty.Response, err error) {

	if client.testMode {
		return client.testResponse, nil
	}

	request := client.restyClient.R().SetHeader(accept, applicationJson)

	if verb == http.MethodPost {
		response, err = request.SetBody(requestBody).Post(endpoint)
	} else if verb == http.MethodGet {
		response, err = request.Get(endpoint)
	}

	if err != nil {
		return nil, &HttpResponse{
			err: err,
		}
	}

	return
}

func parseOperationId(response *resty.Response) (uuid.UUID, error) {
	ids := strings.Split(response.Header().Get("Location"), "/")
	location := ids[len(ids)-1]
	id, err := uuid.Parse(location)
	if err != nil {
		return uuid.Nil, err
	}

	return id, nil
}

func readBody(response *resty.Response) (*[]byte, error) {
	rawBody := response.Body()
	return &rawBody, nil
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

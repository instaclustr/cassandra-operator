package sidecar

import (
	"encoding/json"
	"fmt"
	"github.com/go-resty/resty"
	"github.com/google/uuid"
	"github.com/instaclustr/cassandra-operator/pkg/common/nodestate"
	corev1 "k8s.io/api/core/v1"
	"net/http"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"strconv"
	"strings"
	"time"
)

const (
	accept             = "Accept"
	applicationJson    = "application/json; charset=utf-8"
	EndpointOperations = "operations"
	EndpointStatus     = "status"
)

var log = logf.Log.WithName("SidecarClient")

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
	Port     int32
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

func (client *Client) GetOperations() (*Operations, error) {
	return client.GetFilteredOperations(nil)
}

func (client *Client) GetFilteredOperations(filter *OperationsFilter) (*Operations, error) {
	endpoint := EndpointOperations
	if filter != nil {
		endpoint = filter.buildFilteredEndpoint(endpoint)
	}
	if r, err := client.performRequest(endpoint, http.MethodGet, nil); responseInvalid(r, err) {
		return nil, err
	} else {
		body, err := readBody(r)

		if err != nil {
			return nil, err
		}

		if response, err := unmarshallBody(body, r, &Operations{}); err == nil {
			return response.(*Operations), nil
		} else {
			return nil, err
		}
	}
}

func FilterOperations(ops Operations, kind Kind) (result []interface{}, err error) {

	result = make([]interface{}, 0)
	var op interface{}

	for _, item := range ops {
		if op, err = ParseOperation(item, kind); err != nil {
			log.Error(err, "Error parsing operation", &map[string]interface{}{"Operation": op})
			continue
		}
		result = append(result, op)
	}

	return result, nil
}

func ParseOperation(operation OperationResponse, kind Kind) (interface{}, error) {
	var op interface{}
	// TODO: hopefully useless when backups are properly done
	if operation["progress"] == "NaN" {
		operation["progress"] = 0.0
	}
	if operation["type"].(string) == _KindValueToName[kind] {

		switch kind {
		case cleanup:
			op = &CleanupOperationResponse{}
		case upgradesstables:
			op = &UpgradeSSTablesResponse{}
		case decommission:
			op = &DecommissionOperationResponse{}
		case backup:
			op = &BackupResponse{}
		case rebuild:
			op = &RebuildResponse{}
		case scrub:
			op = &ScrubResponse{}
		case noop:
			return nil, fmt.Errorf("no op")
		}

		if body, err := json.Marshal(operation); err != nil {
			return nil, err
		} else if err := json.Unmarshal(body, op); err != nil {
			return nil, err
		}

	}
	return op, nil
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

func (client *Client) StartOperation(request OperationRequest) (uuid.UUID, error) {
	request.Init()
	if r, err := client.performRequest(EndpointOperations, http.MethodPost, request); responseInvalid(r, err) {
		return uuid.Nil, err
	} else {
		operationId, err := parseOperationId(r)
		if err != nil {
			return uuid.Nil, err
		}
		return operationId, nil
	}
}

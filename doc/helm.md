### Helm Instructions

The project includes in-tree helm templates to make installation simpler and repeatable. 
To install via helm follow the steps below:

 1) Make sure helm templates include all required files:
     ```bash
     $ make helm
     ```

 1) First check and change the values.yaml file to make sure it meets your requirements:
    e.g. in `helm/cassandra-operator/values.yaml`

 1) Install the operator
    ```
    $ helm install helm/cassandra-operator --namespace cassandra-operator 
    ```

 1) Create a Cassandra cluster (remember to check the `helm/cassandra/values.yaml` for correct values)
    ```
    $ helm install helm/cassandra -n test-cluster
    ```

The Helm templates are relatively independent and can also be used to generate the deployments yaml files:
```
$ helm template helm/cassandra-operator
```

### Custom Namespace support via helm templates
Custom namespace support is somewhat limited at the moment primarily due to laziness. You can have the operator watch a different namespace (other than "default") by changing the namepsace it watches on startup. This is not an optimal way to do so (we should watch all namespaces... maybe), but it's the implementation as it stands.

To changes the namespace the operator watches (it can be deployed in a different namespace if you want), you will need to modify the deployment the operator gets deployed by (either the helm package or the example yaml) to include the following in the containers spec
in the `helm/cassandra-operator/templates/deployment.yaml` file:

```yaml
  env:
    - name: WATCH_NAMESPACE
      valueFrom:
        fieldRef:
          fieldPath: metadata.namespace
    - name: POD_NAME
      valueFrom:
        fieldRef:
          fieldPath: metadata.name
    - name: OPERATOR_NAME
      value: "cassandra-operator"
```

The modified helm template (`helm/cassandra-operator/templates/deployment.yaml`) would look like:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: {{ template "cassandra-operator.name" . }}
    chart: {{ .Chart.Name }}-{{ .Chart.Version }}
    heritage: {{ .Release.Service }}
    operator: cassandra
    release: {{ .Release.Name }}
  name: {{ template "cassandra-operator.fullname" . }}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: {{ template "cassandra-operator.name" . }}
      operator: cassandra
      release: {{ .Release.Name }}
  template:
    metadata:
      labels:
        app: {{ template "cassandra-operator.name" . }}
        operator: cassandra
        release: {{ .Release.Name }}
    spec:
      {{- with .Values.imagePullSecret }}
      imagePullSecrets:
        - name: {{ . }}
      {{- end }}
      containers:
        - name: {{ template "cassandra-operator.name" . }}
          env:
            - name: WATCH_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: OPERATOR_NAME
              value: "cassandra-operator"
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: "{{ .Values.image.pullPolicy }}"
          ports:
            - containerPort: 8080
              name: http
          resources:
{{ toYaml .Values.resources | indent 12 }}
    {{- if .Values.nodeSelector }}
      nodeSelector:
{{ toYaml .Values.nodeSelector | indent 8 }}
    {{- end }}
    {{- if .Values.rbacEnable }}
      serviceAccountName: {{ template "cassandra-operator.fullname" . }}
    {{- end }}
    {{- if .Values.tolerations }}
      tolerations:
{{ toYaml .Values.tolerations | indent 8 }}
      securityContext:
{{ toYaml .Values.securityContext | indent 8 }}
{{- end }}
```

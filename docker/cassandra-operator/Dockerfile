FROM golang AS builder

COPY . /go/src/github.com/instaclustr/cassandra-operator
WORKDIR /go/src/github.com/instaclustr/cassandra-operator

# Install dep
RUN go get -d -u github.com/golang/dep \
    && cd $(go env GOPATH)/src/github.com/golang/dep \
    && DEP_LATEST=$(git describe --abbrev=0 --tags) \
    && git checkout $DEP_LATEST \
    && go install -ldflags="-X main.version=$DEP_LATEST" ./cmd/dep

# Install dependencies
RUN dep ensure -v -vendor-only

# Build binary
RUN cd cmd/manager \
    && CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags '-w' -o /tmp/cassandra-operator

FROM scratch

COPY --from=builder /tmp/cassandra-operator .
CMD ["./cassandra-operator"]

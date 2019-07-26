FROM golang AS builder

ARG version

# Create a user so that operator can run as non-root
RUN useradd -u 999 cassandra-operator

COPY . /code
WORKDIR /code

# Build binary
RUN cd cmd/manager \
    && CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
        -ldflags "-w -X main.version=${version}" \
        -o /tmp/cassandra-operator

# Build final image
FROM scratch

COPY --from=builder /tmp/cassandra-operator .
COPY --from=builder /etc/passwd /etc/passwd
USER 999
CMD ["./cassandra-operator"]

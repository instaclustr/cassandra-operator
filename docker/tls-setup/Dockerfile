FROM alpine

WORKDIR /code

RUN apk update && apk add curl && apk add openssl && apk add openjdk8-jre-base && apk add bash && apk add bind-tools
RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt`/bin/linux/amd64/kubectl;
RUN chmod +x kubectl; mv kubectl /usr/local/bin/kubectl

ADD gencerts.sh /code/

# Create 'cassandra' user and group
RUN sed -i "s/999/99/" /etc/group
RUN addgroup -g 999 -S cassandra && adduser -u 999 -S cassandra -G cassandra
USER 999

ENTRYPOINT ["/code/gencerts.sh"]


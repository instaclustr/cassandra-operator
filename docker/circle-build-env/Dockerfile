FROM debian:stretch

COPY dagi /usr/local/bin/

RUN apt-get update

RUN dagi locales gnupg2 dirmngr lsb-release curl git \
    openjdk-8-jdk-headless maven apt-transport-https make procps sudo \
    apt-transport-https ca-certificates gnupg-agent software-properties-common

RUN export CLOUD_SDK_REPO="cloud-sdk-$(lsb_release -c -s)" && \
    echo "deb http://packages.cloud.google.com/apt $CLOUD_SDK_REPO main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -

RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
RUN add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian $(lsb_release -cs) stable"

RUN dagi google-cloud-sdk docker-ce docker-ce-cli containerd.io kubectl

RUN curl https://get.helm.sh/helm-v3.1.1-linux-amd64.tar.gz -o /tmp/helm-v3.1.1-linux-amd64.tar.gz \
    && (cd /tmp && tar -zxvf /tmp/helm-v3.1.1-linux-amd64.tar.gz && mv /tmp/linux-amd64/helm /usr/local/bin/helm) \
    && chmod +x /usr/local/bin/helm \
    && rm -rf /tmp/helm* /tmp/linux-amd64

RUN curl -s https://packagecloud.io/install/repositories/datawireio/telepresence/script.deb.sh | bash

RUN dagi telepresence
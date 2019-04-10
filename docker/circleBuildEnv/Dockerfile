FROM debian:stretch

COPY dagi /usr/local/bin/

RUN dagi locales gnupg2 dirmngr lsb-release curl git \
    openjdk-8-jdk-headless maven apt-transport-https make

RUN export CLOUD_SDK_REPO="cloud-sdk-$(lsb_release -c -s)" && \
    echo "deb http://packages.cloud.google.com/apt $CLOUD_SDK_REPO main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -

RUN echo 'deb https://apt.dockerproject.org/repo debian-stretch main' >> /etc/apt/sources.list && \
    apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D

RUN dagi google-cloud-sdk docker-engine

RUN dagi kubectl

RUN curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get | bash

RUN curl -s https://packagecloud.io/install/repositories/datawireio/telepresence/script.deb.sh | bash

RUN dagi telepresence sudo

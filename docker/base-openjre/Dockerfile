ARG OPENJRE_BASE_IMAGE=debian:stretch

FROM ${OPENJRE_BASE_IMAGE}

ARG openjre_version=8u332-ga-1~deb9u1 
ARG openjre_package=openjdk-8-jre-headless

COPY dagi /usr/local/bin/

RUN dagi locales gnupg2 dirmngr curl uuid-runtime wget \
    ${openjre_package}=${openjre_version} libjna-java libjna-jni procps

FROM debian:stretch

ARG openjre_version=8u212-b01-1~deb9u1

COPY dagi /usr/local/bin/

RUN dagi locales gnupg2 dirmngr curl \
    openjdk-8-jre-headless=${openjre_version} libjna-java libjna-jni procps

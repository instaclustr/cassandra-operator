FROM debian:stretch

COPY dagi /usr/local/bin/

RUN dagi locales gnupg2 dirmngr curl \
    openjdk-8-jre-headless libjna-java libjna-jni procps
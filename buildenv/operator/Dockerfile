FROM debian:stretch

COPY buildenv/operator/dagi /usr/local/bin/

RUN dagi locales gnupg2 dirmngr lsb-release curl \
    openjdk-8-jre-headless

RUN mkdir -p /opt/cassandra-operator

COPY operator/target/operator-1.0-SNAPSHOT.jar /opt/cassandra-operator/operator-1.0.jar

CMD java $JVMOPTS -jar /opt/cassandra-operator/operator-1.0.jar



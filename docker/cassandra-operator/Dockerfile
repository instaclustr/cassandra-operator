FROM base-openjre

RUN dagi maven git

RUN mkdir -p /opt/cassandra-operator

COPY operator-1.0-SNAPSHOT-final.jar /opt/cassandra-operator/operator-1.0.jar

CMD java $JVMOPTS -jar /opt/cassandra-operator/operator-1.0.jar

## Logging

The logging of Cassandra process is configured according to `/etc/cassandra/logback.xml`.

By default, the logging is only writing to stdout so you might inspect logs by `kubectl logs` command:

```
$ kubectl logs -f cassandra-test-cluster-dc1-west1-a-0 cassandra
``` 

There are not any `system.log` nor `debug.log` files written because one size does not fit all here and 
it is important to realize that logs would be written to attached PV which might have various 
performance characteristics. One can accidentally set logging to e.g `TRACE` for debugging and then he might forget 
to set it back. Obviously, there will be _a lot_ of logs and performance might even suffer. Additionally, logs are as 
well occupying disk space which might be looked at as unnecessary nor desirable as we might fill up all disk space 
which was primarily dedicated to Cassandra data files.

If one really wants to start to log into files, we have to change default `/etc/cassandra/logback.xml` file. This 
task might be achieved by the config map at the end of this document. There is no need to create new config map, 
you might as well reuse your existing one and add that data key there with respective content.

Then, you have to say that you want to mount it as a volume. As documented in custom 
configuration documentation, the path of that key is mounted under `/etc/cassandra/` and all files in `cassandra-env.sh.d` 
directory are sourced, one by one, sorted on their prefix, before Cassandra starts. 

```
  userConfigMapVolumeSource:
    name: test-dc-cassandra-user-config
    type: array
    items:
      - key: cassandra_logback
        path: cassandra-env.sh.d/004-custom-logback.sh
```

Check that `cassandra_logback` is a bash script which writes to `/etc/cassandra/logback.xml` so we effectively 
replace the default content. Keep in mind that we are saving all logs under `/var/lib/cassandra/logs`.

Also check this `<configuration scan="true" scanPeriod="60 seconds">`, it means that once Cassandra 
is started, you might change the content of that file and Cassandra will scan it at most in 60 seconds 
and its logging subsystem is reconfigured automatically. There is `vim` editor in 6.x versions of Cassandra 
bundled in the image so you might edit it, otherwise you might copy it over by `kubectl cp`.

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: test-dc-cassandra-user-config
data:
  cassandra_logback: |
    mkdir -p /var/lib/cassandra/logs
    cat <<EOF > /etc/cassandra/logback.xml
    <configuration scan="true" scanPeriod="60 seconds">
      <jmxConfigurator />

      <!-- No shutdown hook; we run it ourselves in StorageService after shutdown -->

      <!-- SYSTEMLOG rolling file appender to system.log (INFO level) -->

      <appender name="SYSTEMLOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
          <level>INFO</level>
        </filter>
        <file>/var/lib/cassandra/logs/system.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
          <!-- rollover daily -->
          <fileNamePattern>/var/lib/cassandra/logs/system.log.%d{yyyy-MM-dd}.%i.zip</fileNamePattern>
          <!-- each file should be at most 50MB, keep 7 days worth of history, but at most 5GB -->
          <maxFileSize>10MB</maxFileSize>
          <maxHistory>5</maxHistory>
          <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
          <pattern>%-5level [%thread] %date{ISO8601} %F:%L - %msg%n</pattern>
        </encoder>
      </appender>

      <!-- DEBUGLOG rolling file appender to debug.log (all levels) -->

      <appender name="DEBUGLOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/lib/cassandra/logs/debug.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
          <!-- rollover daily -->
          <fileNamePattern>/var/lib/cassandra/logs/debug.log.%d{yyyy-MM-dd}.%i.zip</fileNamePattern>
          <!-- each file should be at most 50MB, keep 7 days worth of history, but at most 5GB -->
          <maxFileSize>10MB</maxFileSize>
          <maxHistory>5</maxHistory>
          <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
          <pattern>%-5level [%thread] %date{ISO8601} %F:%L - %msg%n</pattern>
        </encoder>
      </appender>

      <!-- ASYNCLOG assynchronous appender to debug.log (all levels) -->

      <appender name="ASYNCDEBUGLOG" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <includeCallerData>true</includeCallerData>
        <appender-ref ref="DEBUGLOG" />
      </appender>

      <!-- STDOUT console appender to stdout (INFO level) -->

      <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
          <level>INFO</level>
        </filter>
        <encoder>
          <pattern>%-5level [%thread] %date{ISO8601} %F:%L - %msg%n</pattern>
        </encoder>
      </appender>

      <!-- Uncomment bellow and corresponding appender-ref to activate logback metrics
      <appender name="LogbackMetrics" class="com.codahale.metrics.logback.InstrumentedAppender" />
       -->

      <root level="INFO">
        <appender-ref ref="SYSTEMLOG" />
        <appender-ref ref="STDOUT" />
        <appender-ref ref="ASYNCDEBUGLOG" /> <!-- Comment this line to disable debug.log -->
        <!--
        <appender-ref ref="LogbackMetrics" />
        -->
      </root>

      <logger name="org.apache.cassandra" level="DEBUG"/>
    </configuration>
    EOF
``` 
package com.instaclustr.cassandra.sidecar;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.io.IOException;

import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;

public class JMXHelper {

    public MBeanServerConnection getMBeanServerConnection(String jmxServiceURL) throws IOException {
        return getMBeanServerConnection(new JMXServiceURLTypeConverter().convert(jmxServiceURL));
    }

    public MBeanServerConnection getMBeanServerConnection(JMXServiceURL jmxServiceURL) throws IOException {
        final JMXConnector connector = JMXConnectorFactory.connect(jmxServiceURL);

        return connector.getMBeanServerConnection();
    }
}

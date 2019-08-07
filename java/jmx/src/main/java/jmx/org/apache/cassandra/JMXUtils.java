package jmx.org.apache.cassandra;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class JMXUtils {

    public static MBeanServerConnection getMBeanServerConnection(final JMXConnector jmxConnector) throws Exception {
        return jmxConnector.getMBeanServerConnection();
    }

    public static JMXConnector getJmxConnector(final JMXConnectionInfo jmxConnectionInfo) throws Exception {
        if (jmxConnectionInfo == null || jmxConnectionInfo.jmxServiceURL == null) {
            throw new IllegalArgumentException("passed JMXConnectionInfo is either null or its jmxServiceURL is null.");
        }

        if (jmxConnectionInfo.jmxPassword != null && jmxConnectionInfo.jmxUser != null) {

            final Map<String, Object> envMap = new HashMap<String, Object>() {{
                put(JMXConnector.CREDENTIALS, new String[]{
                        jmxConnectionInfo.jmxUser,
                        jmxConnectionInfo.jmxPassword});
            }};

            if (jmxConnectionInfo.trustStore != null && jmxConnectionInfo.trustStorePassword != null) {

                if (!Paths.get(jmxConnectionInfo.trustStore).toFile().exists()) {
                    throw new IllegalStateException(String.format("Specified truststore file for Cassandra %s does not exist!", jmxConnectionInfo.trustStore));
                }

                System.setProperty("javax.net.ssl.trustStore", jmxConnectionInfo.trustStore);
                System.setProperty("javax.net.ssl.trustStorePassword", jmxConnectionInfo.trustStorePassword);
                System.setProperty("ssl.enable", "true");
                System.setProperty("com.sun.management.jmxremote.registry.ssl", "true");

                envMap.put("com.sun.jndi.rmi.factory.socket", new SslRMIClientSocketFactory());
            }

            return JMXConnectorFactory.connect(jmxConnectionInfo.jmxServiceURL, envMap);
        }

        return JMXConnectorFactory.connect(jmxConnectionInfo.jmxServiceURL);
    }
}

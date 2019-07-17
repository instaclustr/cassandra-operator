package jmx.org.apache.cassandra;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import java.util.HashMap;

public class JMXUtils {

    public static MBeanServerConnection getMBeanServerConnection(final JMXConnector jmxConnector) throws Exception {
        return jmxConnector.getMBeanServerConnection();
    }

    public static JMXConnector getJmxConnector(final JMXConnectionInfo jmxConnectionInfo) throws Exception {
        if (jmxConnectionInfo == null || jmxConnectionInfo.jmxServiceURL == null) {
            throw new IllegalArgumentException("passed JMXConnectionInfo is either null or its jmxServiceURL is null.");
        }

        if (jmxConnectionInfo.jmxPassword != null && jmxConnectionInfo.jmxUser != null) {
            return JMXConnectorFactory.connect(jmxConnectionInfo.jmxServiceURL,
                                               new HashMap<String, String[]>() {{
                                                   put(JMXConnector.CREDENTIALS, new String[]{
                                                           jmxConnectionInfo.jmxUser,
                                                           jmxConnectionInfo.jmxPassword});
                                               }});
        }

        return JMXConnectorFactory.connect(jmxConnectionInfo.jmxServiceURL);
    }
}

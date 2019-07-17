package jmx.org.apache.cassandra;

import javax.management.remote.JMXServiceURL;

public class JMXConnectionInfo {
    public final String jmxPassword;
    public final String jmxUser;
    public final JMXServiceURL jmxServiceURL;

    public JMXConnectionInfo(final String jmxPassword,
                             final String jmxUser,
                             final JMXServiceURL jmxServiceURL) {
        this.jmxPassword = jmxPassword;
        this.jmxUser = jmxUser;
        this.jmxServiceURL = jmxServiceURL;
    }
}

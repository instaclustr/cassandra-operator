package jmx.org.apache.cassandra;

import javax.management.remote.JMXServiceURL;
import java.nio.file.Path;

public class JMXConnectionInfo {
    public final String jmxPassword;
    public final String jmxUser;
    public final JMXServiceURL jmxServiceURL;
    public final String trustStore;
    public final String trustStorePassword;

    public JMXConnectionInfo(final String jmxPassword,
                             final String jmxUser,
                             final JMXServiceURL jmxServiceURL,
                             final String trustStore,
                             final String trustStorePassword) {
        this.jmxPassword = jmxPassword;
        this.jmxUser = jmxUser;
        this.jmxServiceURL = jmxServiceURL;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
    }
}

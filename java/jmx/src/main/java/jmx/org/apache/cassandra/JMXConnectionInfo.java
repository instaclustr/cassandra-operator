package jmx.org.apache.cassandra;

import javax.management.remote.JMXServiceURL;

import com.google.common.base.MoreObjects;

/**
 * Holder of JMX related information for setting up JMX connection to Cassandra node.
 */
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

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("jmxServiceURL", jmxServiceURL)
                          .add("trustStore", trustStore)
                          .add("jmxUser", jmxUser)
                          .add("trustStorePassword", "redacted")
                          .add("jmxPassword", "redacted")
                          .toString();
    }
}

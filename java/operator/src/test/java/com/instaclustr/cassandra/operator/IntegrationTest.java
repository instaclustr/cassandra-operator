package com.instaclustr.cassandra.operator;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.FallthroughRetryPolicy;
import com.datastax.driver.core.policies.RetryPolicy;
import com.instaclustr.k8s.K8sModule;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.util.ClientBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.*;

@Guice(modules = K8sModule.class)
public class IntegrationTest {
    private final String clusterName = System.getProperty("cassandraCluster", "cassandra-default");
    private Session session;

    CoreV1Api client;


    @Test(groups = {"integration"})
    public void testWritesNode() throws ApiException, InterruptedException, IOException {
        client = new CoreV1Api(ClientBuilder.defaultClient());
        Cluster cluster = Cluster.builder()
                .withRetryPolicy(FallthroughRetryPolicy.INSTANCE)
                .addContactPoints(InetAddress.getAllByName(clusterName + "-nodes")).build();
        session = cluster.connect();
        session.execute("CREATE KEYSPACE IF NOT EXISTS test WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 3}");
        session.execute("CREATE TABLE IF NOT EXISTS test.foo (" +
                "userid uuid, " +
                "posted_month int, " +
                "value int," +
                "PRIMARY KEY (userid, posted_month))");


        ExecutorService executor = Executors.newFixedThreadPool(1);
        ArrayList<DriverException> results = new ArrayList<>();

        executor.submit(() -> {
            Integer i = 0;

            while(!Thread.currentThread().isInterrupted()) {
                try {
                    session.execute("INSERT INTO test.foo (userid, posted_month, value) VALUES (uuid(), ?, ?)", i % 12, i );
                } catch (DriverException e) {
                    results.add(e);
                }
            }
        });


        Thread.sleep(TimeUnit.MINUTES.toMillis(3));

        executor.shutdownNow();

        Assert.assertEquals(results.size(), 0);

        System.exit(0);


    }


}

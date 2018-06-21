package com.instaclustr.backup;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.instaclustr.backup.util.*;
import org.kohsuke.args4j.Option;

import javax.annotation.Nullable;
import javax.management.remote.JMXServiceURL;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class CommonBackupArguments extends BaseArguments {
    public CommonBackupArguments() {
        super();
    }

    public void setSpeed(@Nullable Speed speed) {
        this.speed = speed;
    }

    public void setDuration(@Nullable Time duration) {
        this.duration = duration;
    }

    public void setBandwidth(@Nullable DataRate bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void setJmxServiceURL(JMXServiceURL jmxServiceURL) {
        this.jmxServiceURL = jmxServiceURL;
    }

    public void setJmxServiceURLFromIp(String ip, int port) {

        //format service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi
        try {
            this.jmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + ip + ":" + port + "/jmxrmi");
        } catch (MalformedURLException e) {
            e.printStackTrace(); //TODO handle better
        }
    }

    public void setBackupBucket(String backupBucket) {
        this.backupBucket = backupBucket;
    }

    public void setBackupId(String backupId) {
        this.backupId = backupId;
    }

    public void setOfflineSnapshot(Boolean offlineSnapshot) {
        this.offlineSnapshot = offlineSnapshot;
    }

    public enum Speed {
        SLOW(new DataRate(1L, DataRate.DataRateUnit.MBPS), 1),
        FAST(new DataRate(10L, DataRate.DataRateUnit.MBPS), 1),
        LUDICROUS(new DataRate(10L, DataRate.DataRateUnit.MBPS), 10),
        PLAID(null, 100);


        Speed(final DataRate bandwidth, final int concurrentUploads) {
            this.bandwidth = bandwidth;
            this.concurrentUploads = concurrentUploads;
        }

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }

        final DataRate bandwidth;
        final int concurrentUploads;
    }

    CommonBackupArguments(final String appName, final PrintStream stream) {
        super(appName, stream);
    }

    @Override
    public void parseArguments(String[] args) {
        super.parseArguments(args);

        if (this.bandwidth == null && this.speed == null) {
            this.speed = CommonBackupArguments.Speed.FAST;
        }

        if (this.offlineSnapshot == null) {
            this.offlineSnapshot = false;
        }

        // use the specified speed
        if (this.speed != null) {
            this.bandwidth = this.speed.bandwidth;
            this.concurrentConnections = this.speed.concurrentUploads;
        }

        if(this.sharedContainerPath == null) {
            this.sharedContainerPath = Paths.get("/");
        }

        if(this.cassandraDirectory == null) {
            this.cassandraDirectory = cassandraDirectory.resolve(Paths.get("var/lib/cassandra"));
        }
    }

    abstract void commonBackupPrintHelp();

    @Override
    void printHelp() {
        commonBackupPrintHelp();
        stream.println("If neither --speed or --bandwidth is specified, then a default speed of 'fast' is used.\n" +
                "\n" +
                "If --duration is specified then the upload throughput is calculated as (total commitlog size ÷ duration), with a minimum speed of 500KB/s.\n" +
                "Specifying --bandwidth in addition to --duration will cap the upload bandwidth (i.e. min(bandwidth, calculated bandwidth)).\n" +
                "\n" +
                "Times and data rates are specified by a numerical value and unit suffix (optionally shortened and space separated).\n" +
                "e.g. '1h', '1 day', '2m', '3000 kbps' \n");

        stream.format("Valid time units are: %s%n", Arrays.stream(TimeUnit.values()).map(Enum::name).map(String::toLowerCase).collect(Collectors.toList()));
        stream.format("Valid data rate units are: %s%n%n", Arrays.stream(DataRate.DataRateUnit.values()).map(Enum::name).map(String::toLowerCase).collect(Collectors.toList()));

        stream.println("The following pre-defined speeds may be used to specify combined bandwidth and concurrent connection limits:");
        Arrays.stream(Speed.values()).map(v -> String.format("%-20s%s, %s concurrent connection%s", v.name().toLowerCase(), (v.bandwidth == null ? "unlimited" : v.bandwidth), v.concurrentUploads, v.concurrentUploads == 1 ? "" : "s")).forEach(stream::println);
    }

    @Option(name = "-s", aliases = {"--speed"}, usage = "Speed to upload the com.instaclustr.backup.", forbids = {"--bandwidth", "--concurrent-connections"})
    @Nullable
    public Speed speed;

    @Option(name = "-d", aliases = {"--duration"}, usage = "Calculate upload throughput based on total file size ÷ duration.", handler = MeasureOptionHandler.class, metaVar = "time")
    @Nullable
    public Time duration;

    @Option(name = "-b", aliases = {"--bandwidth"}, usage = "Maximum upload throughput.", metaVar = "data-rate", handler = MeasureOptionHandler.class)
    @Nullable
    public DataRate bandwidth;

    @Option(name = "-j", aliases = {"--jmx"}, usage = "JMX service url for Cassandra", metaVar = "jmx-url", handler = JMXUrlOptionHandler.class, forbids = "--offline")
    @JsonSerialize(using = JMXServiceURLSerializer.class)
    @JsonDeserialize(using = JMXServiceURLDeserializer.class)
    public JMXServiceURL jmxServiceURL;

    @Option(name = "--bucket", aliases = {"--backup-bucket"}, usage = "Container or bucket to store backups in", metaVar = "bucket_name")
    public String backupBucket;

    @Option(name = "--id", aliases = {"--backup-id"}, usage = "Cassandra backup id", metaVar = "cassandra-2")
    public String backupId;

    @Option(name = "--offline", usage = "Cassandra is not running (won't use JMX to snapshot, no token lists uploaded)", metaVar = "false", forbids = "-j")
    public Boolean offlineSnapshot;



}

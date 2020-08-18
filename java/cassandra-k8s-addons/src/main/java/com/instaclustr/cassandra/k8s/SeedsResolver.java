package com.instaclustr.cassandra.k8s;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The logic in this seed resolver seems to  be rather counter-intuitive, using of dig command ... eh?
 *
 * The reason for this is that we have a seed service (as Kubernetes service) in operator and it work in such
 * way that it exposes even unready endpoints which is what we want, sure, but the resolution of addresses it
 * has is in the following format, imagine one node is running and second one is joining, if we read all addresses
 * from "" it returns these two (for that second node joining)
 *
 * cassandra-test-cluster-dc1-west1-b-0.cassandra-test-cluster-dc1-nodes.default.svc.cluster.local
 * 10-244-2-117.cassandra-test-cluster-dc1-nodes.default.svc.cluster.local
 *
 * Seeds are told to be only nodes which are ending on "-0" (first node in a rack) but we can not
 * parse this suffix from the second address. No matter what, it will always return ip address at the beginning
 * in case of the other pod and we can not determine if the pod is indeed a seed or not (its hostname ending on "-0")
 *
 * For that reason, we are using dig command here and we are asking for SRV records of service name, which returns this:
 *
 * cassandra-test-cluster-dc1-west1-b-0.cassandra-test-cluster-dc1-nodes.default.svc.cluster.local
 * cassandra-test-cluster-dc1-west1-a-0.cassandra-test-cluster-dc1-nodes.default.svc.cluster.local
 *
 * From that we can filter out only seeds and that will be returned.
 *
 * @param <T>
 */
public class SeedsResolver<T> {

    private static final Logger logger = LoggerFactory.getLogger(SeedsResolver.class);

    private static final Pattern digResponseLinePattern = Pattern.compile("(.*) (.*) (.*) (.*)");

    private final String serviceName;

    private final AddressTranslator<T> addressTranslator;

    public SeedsResolver(String serviceName, AddressTranslator<T> addressTranslator) {
        this.serviceName = serviceName;
        this.addressTranslator = addressTranslator;
    }

    public List<T> resolve() throws Exception {
        List<InetAddress> seeds = resolveSeeds(serviceName);

        if (seeds.isEmpty()) {
            throw new IllegalStateException("Seed list is empty!");
        }

        return addressTranslator.translate(seeds);
    }

    private List<InetAddress> resolveSeeds(String service) throws Exception {
        String namespace = readNamespace();

        String clusterDomain = getClusterDomain();

        String digQuery = constructDomainName(service, namespace, clusterDomain);

        List<String> digResult = executeShellCommand("dig", "-t", "SRV", digQuery, "+short");

        List<String> endpoints = parseEndpoints(digResult);

        List<String> seeds = filterSeeds(endpoints);

        return mapEndpointAsInetAddresses(seeds);
    }

    private List<String> filterSeeds(final List<String> endpoints) {
        return endpoints.stream().filter(endpoint -> endpoint.split("\\.")[0].endsWith("-0")).collect(toList());
    }

    private List<InetAddress> mapEndpointAsInetAddresses(final List<String> endpoints) {

        List<InetAddress> inetAddresses = new ArrayList<>();

        for (String endpoint : endpoints) {
            try {
                InetAddress inetAddress = InetAddress.getByName(endpoint);

                logger.info(String.format("Resolved seed: %s", inetAddress.getCanonicalHostName()));

                inetAddresses.add(inetAddress);
            } catch (Exception ex) {
                logger.warn(format("Unable to resolve endpoint %s by name", endpoint), ex);
            }
        }

        return inetAddresses;
    }

    private List<String> parseEndpoints(List<String> digResult) {
        List<String> endpoints = new ArrayList<>();

        for (String line : digResult) {
            Matcher matcher = digResponseLinePattern.matcher(line);

            if (matcher.matches()) {
                String endpoint = matcher.group(4);

                endpoint = endpoint.substring(0, endpoint.length() - 1);

                endpoints.add(endpoint);
            }
        }

        return endpoints;
    }

    /**
     * We are going to parse the last element in "search" row in /etc/resolv.conf
     *
     * nameserver 10.96.0.10
     * search default.svc.cluster.local svc.cluster.local cluster.local
     * options ndots:5
     */
    private String getClusterDomain() {

        final String defaultClusterDomain = "cluster.local";

        try {
            return Files.readAllLines(Paths.get("/etc/resolv.conf")).stream()
                .filter(line -> line.startsWith("search"))
                .map(searchLine -> {
                    final String[] split = searchLine.split(" ");
                    return split[split.length - 1];
                })
                .findFirst()
                .orElse(defaultClusterDomain);
        } catch (final Exception ex) {
            logger.error("Unable to read /etc/resolv.conf, returning " + defaultClusterDomain);
            return defaultClusterDomain;
        }
    }

    private String constructDomainName(String serviceName, String namespace, String clusterDomain) {
        return format("%s.%s.svc.%s", serviceName, namespace, clusterDomain);
    }

    private String readNamespace() throws Exception {
        return new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")));
    }

    private List<String> executeShellCommand(String... command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        Process process = processBuilder.start();

        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        List<String> output = new ArrayList<>();

        while ((line = br.readLine()) != null) {
            logger.debug("dig output: {}", line);
            output.add(line);
        }

        return output;
    }
}

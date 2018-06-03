package com.instaclustr.cassandra.k8s;

import org.apache.cassandra.locator.IEndpointSnitch;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;

public class Snitch implements IEndpointSnitch {
    @Override
    public String getRack(final InetAddress inetAddress) {
        return null;
    }

    @Override
    public String getDatacenter(final InetAddress inetAddress) {
        return null;
    }

    @Override
    public List<InetAddress> getSortedListByProximity(final InetAddress inetAddress, final Collection<InetAddress> collection) {
        return null;
    }

    @Override
    public void sortByProximity(final InetAddress inetAddress, final List<InetAddress> list) {

    }

    @Override
    public int compareEndpoints(final InetAddress inetAddress, final InetAddress inetAddress1, final InetAddress inetAddress2) {
        return 0;
    }

    @Override
    public void gossiperStarting() {

    }

    @Override
    public boolean isWorthMergingForRangeQuery(final List<InetAddress> list, final List<InetAddress> list1, final List<InetAddress> list2) {
        return false;
    }
}

package com.instaclustr.picocli.typeconverter;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.testng.annotations.Test;

public class InetSocketAddressTypeConverterTest {
    private static final int DEFAULT_PORT = 1234;

    private static final InetSocketAddressTypeConverter typeConverter = new InetSocketAddressTypeConverter() {
        @Override
        protected int defaultPort() {
            return DEFAULT_PORT;
        }
    };


    @Test(description = "InetSocketAddressTypeConverter should return the loopback address if defaultAddress is not overridden")
    public void testDefaultLoopbackAddress() throws Exception {
        assertEquals(typeConverter.convert(""), new InetSocketAddress(InetAddress.getLoopbackAddress(), DEFAULT_PORT)); // default address & port
        assertEquals(typeConverter.convert(":4321"), new InetSocketAddress(InetAddress.getLoopbackAddress(), 4321)); // default address & user specified port
    }

    @Test(description = "InetSocketAddressTypeConverter should return a custom address if defaultAddress is overridden")
    public void testDefaultCustomAddress() throws Exception {
        final InetAddress defaultHost = InetAddress.getByName("192.0.2.235");

        final InetSocketAddressTypeConverter defaultCustomAddressTypeConverter = new InetSocketAddressTypeConverter() {
            @Override
            protected InetAddress defaultAddress() {
                return defaultHost;
            }

            @Override
            protected int defaultPort() {
                return DEFAULT_PORT;
            }
        };

        assertEquals(defaultCustomAddressTypeConverter.convert(""), new InetSocketAddress(defaultHost, DEFAULT_PORT)); // default address & port
        assertEquals(defaultCustomAddressTypeConverter.convert(":4321"), new InetSocketAddress(defaultHost, 4321)); // default address & user specified port
        assertEquals(defaultCustomAddressTypeConverter.convert(" : 4321 "), new InetSocketAddress(defaultHost, 4321)); // extraneous whitespace
    }

    @Test(description = "Test various IPv4 addresses (in dotted notation)")
    public void testIPv4Dotted() throws Exception {
        assertEquals(typeConverter.convert("192.0.2.235"), new InetSocketAddress("192.0.2.235", DEFAULT_PORT)); // user specified address & default port
        assertEquals(typeConverter.convert("192.0.2.235:4321"), new InetSocketAddress("192.0.2.235", 4321)); // user specified address & port
        assertEquals(typeConverter.convert(" 192.0.2.235 : 4321 "), new InetSocketAddress("192.0.2.235", 4321)); // extraneous whitespace
    }

    @Test(description = "Test various IPv4 addresses (in decimal notation)")
    public void testIPv4Decimal() {
        assertEquals(typeConverter.convert("3221226219"), new InetSocketAddress("192.0.2.235", DEFAULT_PORT)); // user specified address & default port
        assertEquals(typeConverter.convert("3221226219:4321"), new InetSocketAddress("192.0.2.235", 4321)); // user specified address & port
        assertEquals(typeConverter.convert(" 3221226219 : 4321 "), new InetSocketAddress("192.0.2.235", 4321)); // extraneous whitespace
    }

    @Test(description = "Test various IPv6 addresses")
    public void testIPv6() {
        assertEquals(typeConverter.convert("[2001:db8:85a3:8d3:1319:8a2e:370:7348]"), new InetSocketAddress("[2001:db8:85a3:8d3:1319:8a2e:370:7348]", DEFAULT_PORT)); // user specified address & default port
        assertEquals(typeConverter.convert("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:4321"), new InetSocketAddress("[2001:db8:85a3:8d3:1319:8a2e:370:7348]", 4321)); // user specified address & port
        assertEquals(typeConverter.convert(" [2001:db8:85a3:8d3:1319:8a2e:370:7348] : 4321 "), new InetSocketAddress("[2001:db8:85a3:8d3:1319:8a2e:370:7348]", 4321)); // extraneous whitespace
    }

    @Test
    public void testHostnames() {
        assertEquals(typeConverter.convert("example.com"), new InetSocketAddress("example.com", DEFAULT_PORT));
        assertEquals(typeConverter.convert("example.com:4321"), new InetSocketAddress("example.com", 4321));
        assertEquals(typeConverter.convert(" example.com : 4321 "), new InetSocketAddress("example.com", 4321));
    }

    @Test
    public void testBrokenAddresses() {
        assertThrows(() -> typeConverter.convert(":")); // blank port number
        assertThrows(() -> typeConverter.convert(":abc")); // non-numeric port number
        assertThrows(() -> typeConverter.convert(":9999999")); // invalid port number

        assertThrows(() -> typeConverter.convert("999.999.999.999")); // invalid IPv4 address (attempts to do hostname resolution)

        assertThrows(() -> typeConverter.convert("2001:db8:85a3:8d3:1319:8a2e:370:7348")); // IPv6 address without square brackets

        assertThrows(() -> typeConverter.convert("[2001:hello:world]")); // invalid IPv6 address (attempts to do hostname resolution)
    }

}
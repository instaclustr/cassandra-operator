package com.instaclustr.picocli;

import static java.util.concurrent.TimeUnit.HOURS;

import java.net.InetSocketAddress;

import com.instaclustr.measure.Time;
import com.instaclustr.picocli.typeconverter.ServerInetSocketAddressTypeConverter;
import com.instaclustr.picocli.typeconverter.TimeMeasureTypeConverter;
import picocli.CommandLine.Option;

public class SidecarSpec {

    private static final int DEFAULT_SIDECAR_HTTP_PORT = 4567;

    public static final class HttpServerInetSocketAddressTypeConverter extends ServerInetSocketAddressTypeConverter {
        @Override
        protected int defaultPort() {
            return DEFAULT_SIDECAR_HTTP_PORT;
        }
    }

    @Option(names = {"-l", "--listen"},
            paramLabel = "[ADDRESS][:PORT]",
            defaultValue = ":" + DEFAULT_SIDECAR_HTTP_PORT,
            converter = HttpServerInetSocketAddressTypeConverter.class,
            description = "Listen address (and optional port) for the API endpoint HTTP server. " +
                    "ADDRESS must be a resolvable hostname, IPv4 dotted or decimal address, or IPv6 address (enclosed in square brackets). " +
                    "When ADDRESS is omitted, the server will bind on all interfaces. " +
                    "PORT, when specified, must be a valid port number. The default port " + DEFAULT_SIDECAR_HTTP_PORT + " will be substituted if omitted. " +
                    "If ADDRESS is omitted but PORT is specified, PORT must be prefixed with a colon (':'), or PORT will be interpreted as a decimal IPv4 address. " +
                    "Defaults to '${DEFAULT-VALUE}'")
    public InetSocketAddress httpServerAddress;

    @Option(
            names = {"-e", "--operations-expiration"},
            description = "Period after which finished operations are deleted.",
            converter = TimeMeasureTypeConverter.class
    )
    public Time operationsExpirationPeriod = new Time(1L, HOURS);
}

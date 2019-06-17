package com.instaclustr.sidecar.cassandra;

import com.instaclustr.guice.AbstractSidecarModule;
import com.instaclustr.sidecar.cassandra.picocli.CassandraSidecarCLIOptions;

public class SidecarModule extends AbstractSidecarModule {
    private final CassandraSidecarCLIOptions cliOptions;

    public SidecarModule(final CassandraSidecarCLIOptions cliOptions) {
        super(cliOptions);
        this.cliOptions = cliOptions;
    }

    @Override
    protected void configure() {

        super.configure();

        bind(CassandraSidecarCLIOptions.class).toInstance(cliOptions);
    }
}

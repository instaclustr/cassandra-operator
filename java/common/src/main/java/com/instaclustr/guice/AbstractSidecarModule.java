package com.instaclustr.guice;

import com.google.inject.AbstractModule;
import com.instaclustr.picocli.SidecarCLIOptions;

public abstract class AbstractSidecarModule extends AbstractModule {
    private final SidecarCLIOptions sidecarCLIOptions;

    public AbstractSidecarModule(SidecarCLIOptions sidecarCLIOptions) {
        this.sidecarCLIOptions = sidecarCLIOptions;
    }

    @Override
    protected void configure() {
        bind(SidecarCLIOptions.class).toInstance(sidecarCLIOptions);
    }
}

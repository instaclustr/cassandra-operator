package com.instaclustr.version;

import com.google.inject.AbstractModule;

public class VersionModule extends AbstractModule {
    private final Version version;

    public VersionModule(final String[] version) {
        this(Version.parse(version));
    }

    public VersionModule(final Version version) {
        this.version = version;
    }

    @Override
    protected void configure() {
        bind(Version.class).toInstance(this.version);
    }
}

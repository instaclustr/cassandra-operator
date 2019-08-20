package com.instaclustr.threading;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class ExecutorsModule extends AbstractModule {
    @Provides
    @Singleton
    Executors.ExecutorServiceSupplier getFileUploaderExecutorSupplier() {
        return new Executors.FixedTasksExecutor();
    }
}

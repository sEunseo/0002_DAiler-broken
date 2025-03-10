package com.fissy.dialer.common.concurrent;

import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Generated;

import dagger.internal.Factory;
import dagger.internal.Preconditions;

@Generated(
        value = "dagger.internal.codegen.ComponentProcessor",
        comments = "https://google.github.io/dagger"
)
public enum DialerExecutorModule_ProvideUiSerialExecutorServiceFactory
        implements Factory<ScheduledExecutorService> {
    INSTANCE;

    public static Factory<ScheduledExecutorService> create() {
        return INSTANCE;
    }

    @Override
    public ScheduledExecutorService get() {
        return Preconditions.checkNotNull(
                DialerExecutorModule.provideUiSerialExecutorService(),
                "Cannot return null from a non-@Nullable @Provides method");
    }
}

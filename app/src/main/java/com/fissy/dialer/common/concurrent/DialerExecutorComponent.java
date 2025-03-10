/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.fissy.dialer.common.concurrent;

import android.content.Context;

import androidx.fragment.app.FragmentManager;

import com.fissy.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.fissy.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.fissy.dialer.common.concurrent.Annotations.NonUiParallel;
import com.fissy.dialer.common.concurrent.Annotations.Ui;
import com.fissy.dialer.inject.HasRootComponent;
import com.fissy.dialer.inject.IncludeInDialerRoot;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.ExecutorService;

import dagger.Subcomponent;

/**
 * Dagger component which provides a {@link DialerExecutorFactory}.
 */
@Subcomponent
public abstract class DialerExecutorComponent {

    public static DialerExecutorComponent get(Context context) {
        return ((DialerExecutorComponent.HasComponent)
                ((HasRootComponent) context.getApplicationContext()).component())
                .dialerExecutorComponent();
    }

    public abstract DialerExecutorFactory dialerExecutorFactory();

    @NonUiParallel
    public abstract ExecutorService lowPriorityThreadPool();

    @Ui
    public abstract ListeningExecutorService uiExecutor();

    @BackgroundExecutor
    public abstract ListeningExecutorService backgroundExecutor();

    @LightweightExecutor
    public abstract ListeningExecutorService lightweightExecutor();

    public <OutputT> UiListener<OutputT> createUiListener(
            FragmentManager fragmentManager, String taskId) {
        return UiListener.create(fragmentManager, taskId);
    }

    /**
     * Used to refer to the root application component.
     */
    @IncludeInDialerRoot
    public interface HasComponent {
        DialerExecutorComponent dialerExecutorComponent();
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.fissy.dialer.commandline;

import android.content.Context;

import com.fissy.dialer.function.Supplier;
import com.fissy.dialer.inject.HasRootComponent;
import com.fissy.dialer.inject.IncludeInDialerRoot;
import com.google.common.collect.ImmutableMap;

import dagger.Subcomponent;

/**
 * Component to get all available commands.
 */
@Subcomponent
public abstract class CommandLineComponent {

    public static CommandLineComponent get(Context context) {
        return ((HasComponent) ((HasRootComponent) context.getApplicationContext()).component())
                .commandLineComponent();
    }

    public abstract Supplier<ImmutableMap<String, Command>> commandSupplier();

    /**
     * Used to refer to the root application component.
     */
    @IncludeInDialerRoot
    public interface HasComponent {
        CommandLineComponent commandLineComponent();
    }
}

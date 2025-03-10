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

package com.fissy.dialer.commandline.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import com.fissy.dialer.activecalls.ActiveCallsComponent;
import com.fissy.dialer.commandline.Arguments;
import com.fissy.dialer.commandline.Command;
import com.fissy.dialer.inject.ApplicationContext;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;

/**
 * Manipulates {@link com.fissy.dialer.activecalls.ActiveCalls}
 */
public class ActiveCallsCommand implements Command {

    private final Context appContext;

    @Inject
    ActiveCallsCommand(@ApplicationContext Context appContext) {
        this.appContext = appContext;
    }

    @NonNull
    @Override
    public String getShortDescription() {
        return "manipulate active calls";
    }

    @NonNull
    @Override
    public String getUsage() {
        return "activecalls list";
    }

    @Override
    public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
        if (args.getPositionals().isEmpty()) {
            return Futures.immediateFuture(getUsage());
        }

        String command = args.getPositionals().get(0);

        if ("list".equals(command)) {
            return Futures.immediateFuture(
                    ActiveCallsComponent.get(appContext).activeCalls().getActiveCalls().toString());
        }
        throw new IllegalCommandLineArgumentException("unknown command " + command);
    }
}

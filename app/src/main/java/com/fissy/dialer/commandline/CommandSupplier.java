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

import com.fissy.dialer.function.Supplier;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

/**
 * Supplies commands
 */
@AutoValue
public abstract class CommandSupplier implements Supplier<ImmutableMap<String, Command>> {

    public static Builder builder() {
        return new AutoValue_CommandSupplier.Builder();
    }

    public abstract ImmutableMap<String, Command> commands();

    @Override
    public ImmutableMap<String, Command> get() {
        return commands();
    }

    /**
     * builder for the supplier
     */
    @AutoValue.Builder
    public abstract static class Builder {

        abstract ImmutableMap.Builder<String, Command> commandsBuilder();

        public Builder addCommand(String key, Command command) {
            commandsBuilder().put(key, command);
            return this;
        }

        public abstract CommandSupplier build();
    }
}

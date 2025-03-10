/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */

package com.fissy.dialer.shortcuts;

import android.content.Context;

import androidx.annotation.NonNull;

import com.fissy.dialer.configprovider.ConfigProviderComponent;

/**
 * Checks if dynamic shortcuts should be enabled.
 */
public class Shortcuts {

    /**
     * Key for boolean config value which determines whether or not to enable dynamic shortcuts.
     */
    private static final String DYNAMIC_SHORTCUTS_ENABLED = "dynamic_shortcuts_enabled";

    private Shortcuts() {
    }

    static boolean areDynamicShortcutsEnabled(@NonNull Context context) {
        return ConfigProviderComponent.get(context)
                .getConfigProvider()
                .getBoolean(DYNAMIC_SHORTCUTS_ENABLED, true);
    }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.fissy.dialer.i18n;

import android.content.Context;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Utilities for locale.
 */
public final class LocaleUtils {

    /**
     * Returns the default locale of the device.
     */
    public static Locale getLocale(Context context) {
        LocaleList localList = context.getResources().getConfiguration().getLocales();
        if (!localList.isEmpty()) {
            return localList.get(0);
        }
        return Locale.getDefault();
    }
}

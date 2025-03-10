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

package com.android.incallui.maps;

import android.content.Context;

import com.fissy.dialer.inject.HasRootComponent;

import dagger.Subcomponent;

/**
 * Subcomponent that can be used to access the maps implementation.
 */
@Subcomponent
public abstract class MapsComponent {

    public static MapsComponent get(Context context) {
        return ((HasComponent) ((HasRootComponent) context.getApplicationContext()).component())
                .mapsComponent();
    }

    public abstract Maps getMaps();

    /**
     * Used to refer to the root application component.
     */
    public interface HasComponent {
        MapsComponent mapsComponent();
    }
}

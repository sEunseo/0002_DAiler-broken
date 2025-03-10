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

package com.fissy.dialer.buildtype;

import com.fissy.dialer.proguard.UsedByReflection;

/**
 * Gets the build type. The functionality depends on a an implementation being present in the app
 * that has the same package and the class name ending in "Impl". For example,
 * com.fissy.dialer.buildtype.BuildTypeAccessorImpl. This class is found by the module using
 * reflection.
 */
@UsedByReflection(value = "BuildType.java")
public
        /* package */ interface BuildTypeAccessor {
    @BuildType.Type
    int getBuildType();
}

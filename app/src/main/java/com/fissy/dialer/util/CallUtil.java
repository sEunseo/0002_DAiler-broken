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
 * limitations under the License
 */

package com.fissy.dialer.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.phonenumberutil.PhoneNumberHelper;

import java.util.List;

/**
 * Utilities related to calls that can be used by non system apps.
 */
public class CallUtil {

    /**
     * Indicates that the video calling is not available.
     */
    public static final int VIDEO_CALLING_DISABLED = 0;

    /**
     * Indicates that video calling is enabled, regardless of presence status.
     */
    public static final int VIDEO_CALLING_ENABLED = 1;

    /**
     * Indicates that video calling is enabled, but the availability of video call affordances is
     * determined by the presence status associated with contacts.
     */
    public static final int VIDEO_CALLING_PRESENCE = 2;

    private static boolean hasInitializedIsVideoEnabledState;
    private static boolean cachedIsVideoEnabledState;

    /**
     * Return Uri with an appropriate scheme, accepting both SIP and usual phone call numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberHelper.isUriNumber(number)) {
            return Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    /**
     * Determines if video calling is available, and if so whether presence checking is available as
     * well.
     *
     * <p>Returns a bitmask with {@link #VIDEO_CALLING_ENABLED} to indicate that video calling is
     * available, and {@link #VIDEO_CALLING_PRESENCE} if presence indication is also available.
     *
     * @param context The context
     * @return A bit-mask describing the current video capabilities.
     */
    @SuppressLint("MissingPermission")
    public static int getVideoCallingAvailability(Context context) {
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)) {
            return VIDEO_CALLING_DISABLED;
        }
        TelecomManager telecommMgr = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return VIDEO_CALLING_DISABLED;
        }

        List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
            if (account != null) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
                    int videoCapabilities = VIDEO_CALLING_ENABLED;
                    if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE)) {
                        videoCapabilities |= VIDEO_CALLING_PRESENCE;
                    }
                    return videoCapabilities;
                }
            }
        }
        return VIDEO_CALLING_DISABLED;
    }

    /**
     * Determines if one of the call capable phone accounts defined supports video calling.
     *
     * @param context The context.
     * @return {@code true} if one of the call capable phone accounts supports video calling, {@code
     * false} otherwise.
     */
    public static boolean isVideoEnabled(Context context) {
        boolean isVideoEnabled = (getVideoCallingAvailability(context) & VIDEO_CALLING_ENABLED) != 0;

        // Log everytime the video enabled state changes.
        if (!hasInitializedIsVideoEnabledState) {
            LogUtil.i("CallUtil.isVideoEnabled", "isVideoEnabled: " + isVideoEnabled);
            hasInitializedIsVideoEnabledState = true;
            cachedIsVideoEnabledState = isVideoEnabled;
        } else if (cachedIsVideoEnabledState != isVideoEnabled) {
            LogUtil.i(
                    "CallUtil.isVideoEnabled",
                    "isVideoEnabled changed from %b to %b",
                    cachedIsVideoEnabledState,
                    isVideoEnabled);
            cachedIsVideoEnabledState = isVideoEnabled;
        }

        return isVideoEnabled;
    }

    /**
     * Determines if one of the call capable phone accounts defined supports calling with a subject
     * specified.
     *
     * @param context The context.
     * @return {@code true} if one of the call capable phone accounts supports calling with a subject
     * specified, {@code false} otherwise.
     */
    @SuppressLint("MissingPermission")
    public static boolean isCallWithSubjectSupported(Context context) {
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)) {
            return false;
        }
        TelecomManager telecommMgr = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }

        List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
            if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT)) {
                return true;
            }
        }
        return false;
    }
}

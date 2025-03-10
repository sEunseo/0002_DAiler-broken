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

package com.fissy.dialer.promotion.impl;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;

import com.fissy.dialer.R;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.configprovider.ConfigProvider;
import com.fissy.dialer.inject.ApplicationContext;
import com.fissy.dialer.promotion.Promotion;
import com.fissy.dialer.spannable.ContentWithLearnMoreSpanner;
import com.fissy.dialer.storage.StorageComponent;
import com.fissy.dialer.storage.Unencrypted;

import javax.inject.Inject;

/**
 * RTT promotion.
 */
public final class RttPromotion implements Promotion {
    private static final String SHARED_PREFERENCE_KEY_ENABLED = "rtt_promotion_enabled";
    private static final String SHARED_PREFERENCE_KEY_DISMISSED = "rtt_promotion_dismissed";
    private final Context appContext;
    private final SharedPreferences sharedPreferences;
    private final ConfigProvider configProvider;

    @Inject
    RttPromotion(
            @ApplicationContext Context context,
            @Unencrypted SharedPreferences sharedPreferences,
            ConfigProvider configProvider) {
        appContext = context;
        this.sharedPreferences = sharedPreferences;
        this.configProvider = configProvider;
    }

    public static void setEnabled(Context context) {
        LogUtil.enterBlock("RttPromotion.setEnabled");
        StorageComponent.get(context)
                .unencryptedSharedPrefs()
                .edit()
                .putBoolean(SHARED_PREFERENCE_KEY_ENABLED, true)
                .apply();
    }

    @Override
    public int getType() {
        return PromotionType.BOTTOM_SHEET;
    }

    @Override
    public boolean isEligibleToBeShown() {
        return sharedPreferences.getBoolean(SHARED_PREFERENCE_KEY_ENABLED, false)
                && !sharedPreferences.getBoolean(SHARED_PREFERENCE_KEY_DISMISSED, false);
    }

    @Override
    public CharSequence getTitle() {
        return appContext.getString(R.string.rtt_promotion_title);
    }

    @Override
    public CharSequence getDetails() {
        return new ContentWithLearnMoreSpanner(appContext)
                .create(
                        appContext.getString(R.string.rtt_promotion_details),
                        configProvider.getString(
                                "rtt_promo_learn_more_link_full_url",
                                "http://support.google.com/pixelphone/?p=dialer_rtt"));
    }

    @Override
    @DrawableRes
    public int getIconRes() {
        return R.drawable.quantum_ic_rtt_vd_theme_24;
    }

    @Override
    public void dismiss() {
        sharedPreferences.edit().putBoolean(SHARED_PREFERENCE_KEY_DISMISSED, true).apply();
    }
}

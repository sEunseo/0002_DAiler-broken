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

package com.fissy.dialer.app.calllog;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.QuickContactBadge;

import com.fissy.dialer.app.calllog.CallLogAdapter.OnActionModeStateChangedListener;
import com.fissy.dialer.logging.DialerImpression;
import com.fissy.dialer.logging.Logger;

/**
 * Allows us to click the contact badge for non multi select mode.
 */
public class DialerQuickContactBadge extends QuickContactBadge {

    private View.OnClickListener extraOnClickListener;
    private OnActionModeStateChangedListener onActionModeStateChangeListener;

    public DialerQuickContactBadge(Context context) {
        super(context);
    }

    public DialerQuickContactBadge(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DialerQuickContactBadge(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onClick(View v) {
        if (extraOnClickListener != null
                && onActionModeStateChangeListener.isActionModeStateEnabled()) {
            Logger.get(v.getContext())
                    .logImpression(DialerImpression.Type.MULTISELECT_SINGLE_PRESS_TAP_VIA_CONTACT_BADGE);
            extraOnClickListener.onClick(v);
        } else {
            super.onClick(v);
        }
    }

    public void setMulitSelectListeners(
            View.OnClickListener extraOnClickListener,
            OnActionModeStateChangedListener actionModeStateChangeListener) {
        this.extraOnClickListener = extraOnClickListener;
        onActionModeStateChangeListener = actionModeStateChangeListener;
    }
}

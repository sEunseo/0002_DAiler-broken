/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.phone.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.phone.common.util.ViewUtil;
import com.fissy.dialer.R;

/**
 * EditText which resizes dynamically with respect to text length.
 */
public class ResizingTextEditText extends androidx.appcompat.widget.AppCompatEditText {
    private final int mOriginalTextSize;
    private final int mMinTextSize;
    private boolean mIsResizeEnabled = true;

    public ResizingTextEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOriginalTextSize = (int) getTextSize();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ResizingText);
        mMinTextSize = (int) a.getDimension(R.styleable.ResizingText_resizing_text_min_size,
                mOriginalTextSize);
        a.recycle();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (mIsResizeEnabled) {
            ViewUtil.resizeText(this, mOriginalTextSize, mMinTextSize);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mIsResizeEnabled) {
            ViewUtil.resizeText(this, mOriginalTextSize, mMinTextSize);
        }
    }

    public void setResizeEnabled(boolean isEnabled) {
        mIsResizeEnabled = isEnabled;
    }
}

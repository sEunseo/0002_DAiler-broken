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

package com.fissy.dialer.callcomposer.camera.camerafocus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

import java.util.List;

/**
 * Pie menu item.
 */
public class PieItem {

    // Gray out the view when disabled
    private static final float ENABLED_ALPHA = 1;
    private static final float DISABLED_ALPHA = (float) 0.3;
    private final int level;
    private final float center;
    private Drawable drawable;
    private float start;
    private float sweep;
    private float animate;
    private int inner;
    private int outer;
    private boolean selected;
    private boolean enabled;
    private List<PieItem> items;
    private Path path;
    private OnClickListener onClickListener;
    private float alpha;

    public PieItem(Drawable drawable, int level) {
        this.drawable = drawable;
        this.level = level;
        setAlpha(1f);
        enabled = true;
        setAnimationAngle(getAnimationAngle());
        start = -1;
        center = -1;
    }

    public boolean hasItems() {
        return items != null;
    }

    public List<PieItem> getItems() {
        return items;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path p) {
        path = p;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        drawable.setAlpha((int) (255 * alpha));
    }

    private float getAnimationAngle() {
        return animate;
    }

    public void setAnimationAngle(float a) {
        animate = a;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (this.enabled) {
            setAlpha(ENABLED_ALPHA);
        } else {
            setAlpha(DISABLED_ALPHA);
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean s) {
        selected = s;
    }

    public int getLevel() {
        return level;
    }

    public void setGeometry(float st, float sw, int inside, int outside) {
        start = st;
        sweep = sw;
        inner = inside;
        outer = outside;
    }

    public float getCenter() {
        return center;
    }

    public float getStart() {
        return start;
    }

    public float getStartAngle() {
        return start + animate;
    }

    public float getSweep() {
        return sweep;
    }

    public int getInnerRadius() {
        return inner;
    }

    public int getOuterRadius() {
        return outer;
    }

    public void setOnClickListener(OnClickListener listener) {
        onClickListener = listener;
    }

    public void performClick() {
        if (onClickListener != null) {
            onClickListener.onClick(this);
        }
    }

    public int getIntrinsicWidth() {
        return drawable.getIntrinsicWidth();
    }

    public int getIntrinsicHeight() {
        return drawable.getIntrinsicHeight();
    }

    public void setBounds(int left, int top, int right, int bottom) {
        drawable.setBounds(left, top, right, bottom);
    }

    public void draw(Canvas canvas) {
        drawable.draw(canvas);
    }

    public void setImageResource(Context context, int resId) {
        Drawable d = context.getResources().getDrawable(resId).mutate();
        d.setBounds(drawable.getBounds());
        drawable = d;
        setAlpha(alpha);
    }

    /**
     * Listener to detect pie item clicks.
     */
    public interface OnClickListener {
        void onClick(PieItem item);
    }
}

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

package com.android.incallui.audioroute;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff.Mode;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.os.BuildCompat;

import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.TelecomAdapter;
import com.fissy.dialer.R;
import com.fissy.dialer.common.FragmentUtils;
import com.fissy.dialer.common.LogUtil;
import com.fissy.dialer.logging.DialerImpression;
import com.fissy.dialer.logging.Logger;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Shows picker for audio routes
 */
public class AudioRouteSelectorDialogFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AudioRouteSelectorDialogFragment";
    private static final String ARG_AUDIO_STATE = "audio_state";

    public static AudioRouteSelectorDialogFragment newInstance(CallAudioState audioState) {
        AudioRouteSelectorDialogFragment fragment = new AudioRouteSelectorDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_AUDIO_STATE, audioState);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        FragmentUtils.checkParent(this, AudioRouteSelectorPresenter.class);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        LogUtil.i("AudioRouteSelectorDialogFragment.onCreateDialog", null);
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        if (Settings.canDrawOverlays(getContext())) {
            dialog
                    .getWindow()
                    .setType(
                            BuildCompat.isAtLeastO()
                                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        return dialog;
    }

    @Nullable
    @Override
    @SuppressLint("NewApi")
    public View onCreateView(
            LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
        View view = layoutInflater.inflate(R.layout.audioroute_selector, null, false);
        CallAudioState audioState = requireArguments().getParcelable(ARG_AUDIO_STATE);

        if (BuildCompat.isAtLeastP()) {
            // Create items for all connected Bluetooth devices
            Collection<BluetoothDevice> bluetoothDeviceSet = audioState.getSupportedBluetoothDevices();
            for (BluetoothDevice device : bluetoothDeviceSet) {
                boolean selected =
                        (audioState.getRoute() == CallAudioState.ROUTE_BLUETOOTH)
                                && (bluetoothDeviceSet.size() == 1
                                || device.equals(audioState.getActiveBluetoothDevice()));
                TextView textView = createBluetoothItem(device, selected);
                ((LinearLayout) view).addView(textView, 0);
            }
        } else {
            // Only create Bluetooth audio route
            TextView textView =
                    (TextView) getLayoutInflater().inflate(R.layout.audioroute_item, null, false);
            textView.setText(getString(R.string.audioroute_bluetooth));
            initItem(
                    textView,
                    CallAudioState.ROUTE_BLUETOOTH,
                    audioState,
                    DialerImpression.Type.IN_CALL_SWITCH_AUDIO_ROUTE_BLUETOOTH);
            ((LinearLayout) view).addView(textView, 0);
        }

        initItem(
                (TextView) view.findViewById(R.id.audioroute_speaker),
                CallAudioState.ROUTE_SPEAKER,
                audioState,
                DialerImpression.Type.IN_CALL_SWITCH_AUDIO_ROUTE_SPEAKER);
        initItem(
                (TextView) view.findViewById(R.id.audioroute_headset),
                CallAudioState.ROUTE_WIRED_HEADSET,
                audioState,
                DialerImpression.Type.IN_CALL_SWITCH_AUDIO_ROUTE_WIRED_HEADSET);
        initItem(
                (TextView) view.findViewById(R.id.audioroute_earpiece),
                CallAudioState.ROUTE_EARPIECE,
                audioState,
                DialerImpression.Type.IN_CALL_SWITCH_AUDIO_ROUTE_EARPIECE);

        // TODO(a bug): set peak height correctly to fully expand it in landscape mode.
        return view;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);
        FragmentUtils.getParentUnsafe(
                        AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
                .onAudioRouteSelectorDismiss();
    }

    private void initItem(
            TextView item,
            final int itemRoute,
            CallAudioState audioState,
            DialerImpression.Type impressionType) {
        if ((audioState.getSupportedRouteMask() & itemRoute) == 0) {
            item.setVisibility(View.GONE);
        } else if (audioState.getRoute() == itemRoute) {
            item.setSelected(true);
            item.setTextColor(android.R.attr.colorPrimary);
            item.setCompoundDrawableTintList(ColorStateList.valueOf(android.R.attr.colorPrimary));
            item.setCompoundDrawableTintMode(Mode.SRC_ATOP);
        }
        item.setOnClickListener(
                (v) -> {
                    logCallAudioRouteImpression(impressionType);
                    FragmentUtils.getParentUnsafe(
                                    AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
                            .onAudioRouteSelected(itemRoute);
                    dismiss();
                });
    }

    private TextView createBluetoothItem(BluetoothDevice bluetoothDevice, boolean selected) {
        TextView textView =
                (TextView) getLayoutInflater().inflate(R.layout.audioroute_item, null, false);
        textView.setText(getAliasName(bluetoothDevice));
        if (selected) {
            textView.setSelected(true);
            textView.setTextColor(android.R.attr.colorPrimary);
            textView.setCompoundDrawableTintList(ColorStateList.valueOf(android.R.attr.colorPrimary));
            textView.setCompoundDrawableTintMode(Mode.SRC_ATOP);
        }
        textView.setOnClickListener(
                (v) -> {
                    logCallAudioRouteImpression(DialerImpression.Type.IN_CALL_SWITCH_AUDIO_ROUTE_BLUETOOTH);
                    // Set Bluetooth audio route
                    FragmentUtils.getParentUnsafe(
                                    AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
                            .onAudioRouteSelected(CallAudioState.ROUTE_BLUETOOTH);
                    // Set active Bluetooth device
                    TelecomAdapter.getInstance().requestBluetoothAudio(bluetoothDevice);
                    dismiss();
                });

        return textView;
    }

    @SuppressLint("PrivateApi")
    private String getAliasName(BluetoothDevice bluetoothDevice) {
        try {
            Method getActiveDeviceMethod = bluetoothDevice.getClass().getDeclaredMethod("getAliasName");
            getActiveDeviceMethod.setAccessible(true);
            return (String) getActiveDeviceMethod.invoke(bluetoothDevice);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return null;
            }
            return bluetoothDevice.getName();
        }
    }

    private void logCallAudioRouteImpression(DialerImpression.Type impressionType) {
        DialerCall dialerCall = CallList.getInstance().getOutgoingCall();
        if (dialerCall == null) {
            dialerCall = CallList.getInstance().getActiveOrBackgroundCall();
        }

        if (dialerCall != null) {
            Logger.get(getContext())
                    .logCallImpression(
                            impressionType, dialerCall.getUniqueCallId(), dialerCall.getTimeAddedMs());
        } else {
            Logger.get(getContext()).logImpression(impressionType);
        }
    }

    /**
     * Called when an audio route is picked
     */
    public interface AudioRouteSelectorPresenter {
        void onAudioRouteSelected(int audioRoute);

        void onAudioRouteSelectorDismiss();
    }
}

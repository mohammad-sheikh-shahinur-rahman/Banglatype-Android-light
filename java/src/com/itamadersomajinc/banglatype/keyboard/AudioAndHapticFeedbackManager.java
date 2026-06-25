/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.itamadersomajinc.banglatype.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.itamadersomajinc.banglatype.keyboard.common.Constants;
import com.itamadersomajinc.banglatype.keyboard.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private Context mContext;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;

    // Synthesized custom click tracks
    private AudioTrack mMechanicalTrack;
    private AudioTrack mIphoneTrack;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        initSynthesizedTracks();
    }

    public void performHapticAndAudioFeedback(final int code,
            final View viewToPerformHapticFeedbackOn) {
        performHapticFeedback(viewToPerformHapticFeedbackOn);
        performAudioFeedback(code);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public void vibrate(final long milliseconds) {
        if (mVibrator == null) {
            return;
        }
        mVibrator.vibrate(milliseconds);
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code) {
        if (mAudioManager == null || !mSoundOn || mContext == null) {
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int profile = 0;
        try {
            profile = Integer.parseInt(prefs.getString("pref_sound_profile", "0"));
        } catch (Exception e) {
            profile = 0;
        }

        if (profile == 1) { // Mechanical
            playSynthesizedSound(mMechanicalTrack);
        } else if (profile == 2) { // iPhone
            playSynthesizedSound(mIphoneTrack);
        } else {
            playSystemSound(code);
        }
    }

    private void playSystemSound(final int code) {
        final int sound;
        switch (code) {
        case Constants.CODE_DELETE:
            sound = AudioManager.FX_KEYPRESS_DELETE;
            break;
        case Constants.CODE_ENTER:
            sound = AudioManager.FX_KEYPRESS_RETURN;
            break;
        case Constants.CODE_SPACE:
            sound = AudioManager.FX_KEYPRESS_SPACEBAR;
            break;
        default:
            sound = AudioManager.FX_KEYPRESS_STANDARD;
            break;
        }
        mAudioManager.playSoundEffect(sound, mSettingsValues.mKeypressSoundVolume);
    }

    private void playSynthesizedSound(final AudioTrack track) {
        if (track == null) return;
        try {
            track.stop();
            track.reloadStaticData();
            track.play();
        } catch (Exception e) {
            android.util.Log.e("AudioAndHaptic", "Failed to play custom sound track", e);
        }
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn) {
        if (mSettingsValues == null || !mSettingsValues.mVibrateOn || mVibrator == null || viewToPerformHapticFeedbackOn == null) {
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(viewToPerformHapticFeedbackOn.getContext());
        int profile = 0;
        try {
            profile = Integer.parseInt(prefs.getString("pref_haptic_profile", "0"));
        } catch (Exception e) {
            profile = 0;
        }

        switch (profile) {
            case 1: // Light/iPhone Tick
                vibrate(8);
                break;
            case 2: // Medium/Android Tap
                vibrate(18);
                break;
            case 3: // Strong/Crisp Click
                vibrate(30);
                break;
            case 4: // Mechanical Double-Tap
                try {
                    mVibrator.vibrate(new long[]{0, 6, 8, 6}, -1);
                } catch (Exception e) {
                    vibrate(15);
                }
                break;
            case 0:
            default:
                vibrateDefault(viewToPerformHapticFeedbackOn);
                break;
        }
    }

    private void vibrateDefault(final View viewToPerformHapticFeedbackOn) {
        if (mSettingsValues.mKeypressVibrationDuration >= 0) {
            vibrate(mSettingsValues.mKeypressVibrationDuration);
            return;
        }
        if (viewToPerformHapticFeedbackOn != null) {
            viewToPerformHapticFeedbackOn.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn();
    }

    private void initSynthesizedTracks() {
        try {
            int sampleRate = 44100;

            // Generate Mechanical Click
            byte[] mechPcm = generateMechanicalClick();
            mMechanicalTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    mechPcm.length,
                    AudioTrack.MODE_STATIC);
            mMechanicalTrack.write(mechPcm, 0, mechPcm.length);

            // Generate iPhone Click
            byte[] iphonePcm = generateIphoneClick();
            mIphoneTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    iphonePcm.length,
                    AudioTrack.MODE_STATIC);
            mIphoneTrack.write(iphonePcm, 0, iphonePcm.length);
        } catch (Exception e) {
            android.util.Log.e("AudioAndHaptic", "Failed to initialize custom sound profiles", e);
        }
    }

    private static byte[] generateMechanicalClick() {
        int sampleRate = 44100;
        double duration = 0.035; // 35 ms
        int numSamples = (int) (duration * sampleRate);
        byte[] pcm = new byte[2 * numSamples];
        java.util.Random random = new java.util.Random();

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;

            // Snap component: 4000 Hz, decays fast (tau = 0.002s)
            double snapVal = Math.sin(2 * Math.PI * 4000 * t) * Math.exp(-t / 0.002);

            // Spring/slider component: 1500 Hz, decays slower (tau = 0.012s)
            double springVal = Math.sin(2 * Math.PI * 1500 * t) * Math.exp(-t / 0.012);

            // Noise burst: decays fast (tau = 0.005s)
            double noiseVal = (random.nextDouble() * 2.0 - 1.0) * Math.exp(-t / 0.005);

            // Mix
            double mix = 0.4 * snapVal + 0.3 * springVal + 0.3 * noiseVal;

            // Convert to 16-bit PCM
            short val = (short) (mix * 32767);
            pcm[2 * i] = (byte) (val & 0xff);
            pcm[2 * i + 1] = (byte) ((val >> 8) & 0xff);
        }
        return pcm;
    }

    private static byte[] generateIphoneClick() {
        int sampleRate = 44100;
        double duration = 0.025; // 25 ms
        int numSamples = (int) (duration * sampleRate);
        byte[] pcm = new byte[2 * numSamples];

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;

            // Low pitch block sound: 320 Hz, decays fast (tau = 0.006s)
            double popVal = Math.sin(2 * Math.PI * 320 * t) * Math.exp(-t / 0.006);

            // Convert to 16-bit PCM
            short val = (short) (popVal * 32767);
            pcm[2 * i] = (byte) (val & 0xff);
            pcm[2 * i + 1] = (byte) ((val >> 8) & 0xff);
        }
        return pcm;
    }
}

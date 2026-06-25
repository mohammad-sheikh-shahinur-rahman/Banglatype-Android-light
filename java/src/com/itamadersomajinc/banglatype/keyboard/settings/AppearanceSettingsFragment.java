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

package com.itamadersomajinc.banglatype.keyboard.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.itamadersomajinc.banglatype.keyboard.R;
import com.itamadersomajinc.banglatype.keyboard.common.Constants;
import com.itamadersomajinc.banglatype.keyboard.define.ProductionFlags;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * "Appearance" settings sub screen.
 */
public final class AppearanceSettingsFragment extends SubScreenFragment {
    private static final int RC_PICK_IMAGE = 4920;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_appearance);
        if (!ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED ||
                Constants.isPhone(Settings.readScreenMetrics(getResources()))) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD);
        }

        final SeekBarDialogPreference keyboardHeightPref = (SeekBarDialogPreference) findPreference("pref_keyboard_height");
        if (keyboardHeightPref != null) {
            keyboardHeightPref.setInterface(new SeekBarDialogPreference.ValueProxy() {
                @Override
                public int readValue(String key) {
                    return PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt(key, 100);
                }

                @Override
                public int readDefaultValue(String key) {
                    return 100;
                }

                @Override
                public void writeValue(int value, String key) {
                    PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putInt(key, value).apply();
                }

                @Override
                public void writeDefaultValue(String key) {
                    PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().remove(key).apply();
                }

                @Override
                public String getValueText(int value) {
                    return value + "%";
                }

                @Override
                public void feedbackValue(int value) {}
            });
        }
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference) {
        final String key = preference.getKey();
        if ("pref_custom_background_color_picker".equals(key)) {
            showColorPickerDialog("pref_custom_background_color", "Choose Background Color", Color.BLACK);
            return true;
        } else if ("pref_custom_key_color_picker".equals(key)) {
            showColorPickerDialog("pref_custom_key_color", "Choose Key Color", Color.DKGRAY);
            return true;
        } else if ("pref_custom_key_text_color_picker".equals(key)) {
            showColorPickerDialog("pref_custom_key_text_color", "Choose Text & Icon Color", Color.WHITE);
            return true;
        } else if ("pref_custom_bg_image_picker".equals(key)) {
            pickBackgroundImage();
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void pickBackgroundImage() {
        try {
            final Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, RC_PICK_IMAGE);
        } catch (Exception e) {
            try {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, RC_PICK_IMAGE);
            } catch (Exception ex) {
                Toast.makeText(getActivity(), "No gallery app found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PICK_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            final android.net.Uri selectedImage = data.getData();
            if (selectedImage != null) {
                try {
                    final InputStream is = getActivity().getContentResolver().openInputStream(selectedImage);
                    final File cacheFile = new File(getActivity().getFilesDir(), "custom_keyboard_bg.jpg");
                    final FileOutputStream os = new FileOutputStream(cacheFile);
                    final byte[] buffer = new byte[4096];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    is.close();
                    os.flush();
                    os.close();

                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    prefs.edit().putString("pref_custom_keyboard_bg_uri", android.net.Uri.fromFile(cacheFile).toString()).apply();
                    Toast.makeText(getActivity(), "Background image set successfully!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    android.util.Log.e("ThemeSettings", "Failed to save custom background image", e);
                    Toast.makeText(getActivity(), "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showColorPickerDialog(final String prefKey, final String title, final int defaultColor) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final int currentColor = prefs.getInt(prefKey, defaultColor);

        final LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        final EditText hexInput = new EditText(getActivity());
        hexInput.setHint("Enter Hex Color (e.g. #121212)");
        hexInput.setText(String.format("#%06X", (0xFFFFFF & currentColor)));
        layout.addView(hexInput);

        final GridLayout grid = new GridLayout(getActivity());
        grid.setColumnCount(4);
        grid.setPadding(0, 16, 0, 0);

        final int[] colors = {
            0xFF121212, 0xFF0D1B2A, 0xFF143625, 0xFF3A0A0A,
            0xFF2D124D, 0xFF072A2C, 0xFF212529, 0xFF1E293B,
            0xFFFF5722, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0,
            0xFFE91E63, 0xFFFFFFFF, 0xFF888888, 0xFF000000
        };

        for (final int color : colors) {
            final View colorView = new View(getActivity());
            final int size = (int) (40 * getResources().getDisplayMetrics().density);
            final GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(12, 12, 12, 12);
            colorView.setLayoutParams(params);

            final GradientDrawable gd = new GradientDrawable();
            gd.setColor(color);
            gd.setCornerRadius(size / 2f);
            gd.setStroke(2, 0xFFDDDDDD);
            colorView.setBackground(gd);

            colorView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hexInput.setText(String.format("#%06X", (0xFFFFFF & color)));
                }
            });
            grid.addView(colorView);
        }

        layout.addView(grid);

        new android.app.AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Select", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String hex = hexInput.getText().toString().trim();
                        try {
                            if (!hex.startsWith("#")) {
                                hex = "#" + hex;
                            }
                            int color = Color.parseColor(hex);
                            prefs.edit().putInt(prefKey, color).apply();
                            Toast.makeText(getActivity(), "Color applied!", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(getActivity(), "Invalid Hex Color", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        CustomInputStyleSettingsFragment.updateCustomInputStylesSummary(
                findPreference(Settings.PREF_CUSTOM_INPUT_STYLES));
        ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME));
    }
}

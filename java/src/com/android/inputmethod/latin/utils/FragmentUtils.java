/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import com.itamadersomajinc.banglatype.dictionarypack.DictionarySettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.about.AboutPreferences;
import com.itamadersomajinc.banglatype.keyboard.settings.AccountsSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.AdvancedSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.AppearanceSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.CorrectionSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.CustomInputStyleSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.DebugSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.GestureSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.LayoutPickerFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.PreferencesSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.SettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.settings.ThemeSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.spellcheck.SpellCheckerSettingsFragment;
import com.itamadersomajinc.banglatype.keyboard.userdictionary.UserDictionaryAddWordFragment;
import com.itamadersomajinc.banglatype.keyboard.userdictionary.UserDictionaryList;
import com.itamadersomajinc.banglatype.keyboard.userdictionary.UserDictionaryLocalePicker;
import com.itamadersomajinc.banglatype.keyboard.userdictionary.UserDictionarySettings;

import java.util.HashSet;

public class FragmentUtils {
    private static final HashSet<String> sLatinImeFragments = new HashSet<>();
    static {
        sLatinImeFragments.add(DictionarySettingsFragment.class.getName());
        sLatinImeFragments.add(AboutPreferences.class.getName());
        sLatinImeFragments.add(PreferencesSettingsFragment.class.getName());
        sLatinImeFragments.add(AccountsSettingsFragment.class.getName());
        sLatinImeFragments.add(AppearanceSettingsFragment.class.getName());
        sLatinImeFragments.add(ThemeSettingsFragment.class.getName());
        sLatinImeFragments.add(CustomInputStyleSettingsFragment.class.getName());
        sLatinImeFragments.add(GestureSettingsFragment.class.getName());
        sLatinImeFragments.add(LayoutPickerFragment.class.getName());
        sLatinImeFragments.add(CorrectionSettingsFragment.class.getName());
        sLatinImeFragments.add(AdvancedSettingsFragment.class.getName());
        sLatinImeFragments.add(DebugSettingsFragment.class.getName());
        sLatinImeFragments.add(SettingsFragment.class.getName());
        sLatinImeFragments.add(SpellCheckerSettingsFragment.class.getName());
        sLatinImeFragments.add(UserDictionaryAddWordFragment.class.getName());
        sLatinImeFragments.add(UserDictionaryList.class.getName());
        sLatinImeFragments.add(UserDictionaryLocalePicker.class.getName());
        sLatinImeFragments.add(UserDictionarySettings.class.getName());
    }

    public static boolean isValidFragment(String fragmentName) {
        return sLatinImeFragments.contains(fragmentName);
    }
}

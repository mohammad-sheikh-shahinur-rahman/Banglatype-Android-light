package com.itamadersomajinc.banglatype.keyboard.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.itamadersomajinc.banglatype.keyboard.R;

public final class LayoutPickerFragment extends PreferenceFragment {

    private SharedPreferences mPrefs;
    
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.layout_picker, container, false);
        
        final Activity activity = getActivity();
        if (activity == null) {
            return root;
        }
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        
        setupLayoutToggle(root, R.id.card_avro, R.id.switch_avro, "pref_layout_active_avro");
        setupLayoutToggle(root, R.id.card_jatiyo, R.id.switch_jatiyo, "pref_layout_active_jatiyo");
        setupLayoutToggle(root, R.id.card_probhat, R.id.switch_probhat, "pref_layout_active_probhat");
        setupLayoutToggle(root, R.id.card_chakma, R.id.switch_chakma, "pref_layout_active_chakma");
        setupLayoutToggle(root, R.id.card_arabic, R.id.switch_arabic, "pref_layout_active_arabic");
        setupLayoutToggle(root, R.id.card_qwerty, R.id.switch_qwerty, "pref_layout_active_qwerty");
        
        return root;
    }
    
    private void setupLayoutToggle(final View root, final int cardId, final int switchId, final String prefKey) {
        final View card = root.findViewById(cardId);
        final SwitchMaterial toggle = (SwitchMaterial) root.findViewById(switchId);
        
        if (card == null || toggle == null) {
            return;
        }
        
        // Load initial state (all are true by default)
        boolean isEnabled = mPrefs.getBoolean(prefKey, true);
        toggle.setChecked(isEnabled);
        
        // Handle click
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean newState = !toggle.isChecked();
                toggle.setChecked(newState);
                mPrefs.edit().putBoolean(prefKey, newState).apply();
            }
        });
    }
}

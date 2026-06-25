# Plan: Add Theme Builder, Voice Typing, and Spacebar-Swipe Language Switch

## Key finding from exploration
**Spacebar-swipe to switch language is ALREADY fully implemented** in `PointerTracker.java`
(captures `mSpaceSwipeStartX` on down, sets `mIsSwipingOnSpace` past one key-width of travel,
fires `onCustomRequest(CUSTOM_CODE_SWITCH_TO_NEXT_SUBTYPE)` → `LatinIME.switchToNextSubtype()`).
So that feature needs only **polish**, not building. The real work is Voice Typing and the Theme Builder.

---

## Feature 1: Spacebar-swipe language switch (POLISH only)
Current code only ever switches to the *next* subtype regardless of swipe direction; the sign of
`distanceX` is computed but unused.

- `common/.../Constants.java`: add `CUSTOM_CODE_SWITCH_TO_PREV_SUBTYPE = 3`.
- `LatinIME.java`: add `switchToPreviousSubtype()` (mirror of `switchToNextSubtype`, using
  `mSubtypeState.switchSubtype` / `switchToNextInputMethod(token, ...)`); handle the new code in
  `onCustomRequest`.
- `PointerTracker.java onUpEventInternal`: fire prev vs next based on `sign(x - mSpaceSwipeStartX)`.
- (Optional) make the threshold a fraction of key width for easier triggering.

## Feature 2: Voice Typing (mic key → system speech recognizer)
Today only a hidden suggestion-strip "voice key" exists and it just delegates to a (usually absent)
shortcut IME via `CODE_SHORTCUT` — effectively dead. We add a real recognizer.

- `RECORD_AUDIO` permission is already declared in the manifest. `PermissionsManager` helper exists.
- New class `com.codepotro.osboard.keyboard.VoiceInputManager`:
  - Wraps `android.speech.SpeechRecognizer` with a `RecognitionListener`.
  - On `onResults`, commit text via `LatinIME.onTextInput(recognizedText)` (existing path that
    routes through `mInputLogic.onTextInput`).
  - Requests `RECORD_AUDIO` via `PermissionsManager` if not granted before starting.
- `LatinIME.onEvent` (line ~1453): change the `CODE_SHORTCUT` branch so that when no shortcut IME is
  ready (`!mRichImm.isShortcutImeReady()`) and `SpeechRecognizer.isRecognitionAvailable(this)`, it
  launches `VoiceInputManager` instead of the no-op `switchToShortcutIme`. Keeps backward behavior
  when a real voice IME is present.
- Wire the existing suggestion-strip voice key (already gated by `pref_voice_input_key`, default on)
  to this path — no new UI key strictly required, but optionally add a mic key to the bottom row
  later. Initial scope: make the existing voice button actually do voice-to-text.
- Add a brief "Listening…" visual is out of scope for v1 (can show a Toast).

## Feature 3: Theme Builder (custom key color / text color / background color)
Themes are static compiled `R.style` resources wrapped via `ContextThemeWrapper`; arbitrary runtime
colors cannot be a new style. Approach: read user color prefs in `KeyboardView` and override the
baked-in drawables/paint, and rebuild the view when those prefs change.

New SharedPreferences (follow the `pref_show_number_row` pattern in `Settings.java`):
- `pref_custom_theme_enabled` (bool, default false)
- `pref_custom_key_color` (int color)
- `pref_custom_key_text_color` (int color)
- `pref_custom_background_color` (int color)

Code changes:
- `Settings.java` / `SettingsValues.java`: add the constants + read them into `SettingsValues`.
- `KeyboardView.java`:
  - In the constructor, after reading `mKeyBackground`/`mFunctionalKeyBackground`/`mSpacebarBackground`,
    if custom theme enabled, `mutate()` + `setColorFilter(keyColor, SRC_ATOP)` (API-guarded `setTint`).
  - In `setKeyboard`, after `mKeyDrawParams.updateParams(...)`, override `mKeyDrawParams.mTextColor`
    (and hint colors) with the custom text color. `KeyDrawParams` fields are public mutable ints.
  - Apply `setBackgroundColor(customBg)` on the view when enabled.
- `KeyboardSwitcher.updateKeyboardThemeAndContextThemeWrapper`: also return `true` (force view
  rebuild) when a cached signature of the custom-color prefs changed, so changes take effect.
- New `ThemeBuilderSettingsFragment` + `res/xml/prefs_screen_theme_builder.xml`:
  - A master enable checkbox + three color-picker preferences.
  - Color picker: a small custom `DialogPreference` (no external lib) writing an int via `getInt`.
- Add an entry in `prefs_screen_appearance.xml` pointing to the new fragment.
- Background **image** (gallery pick) deferred to a follow-up — color first to keep scope sane.

## Build / verification
- Project builds with Gradle (`./gradlew :assembleDebug` from `java/` or root). I'll compile to catch
  errors. I cannot run the IME interactively here, so verification is: successful build + code review
  of the wired paths. I'll report build output honestly.

## Order of work
1. Feature 1 polish (smallest, lowest risk).
2. Feature 3 Theme Builder settings + rendering.
3. Feature 2 Voice typing.
4. Build, fix compile errors, report.

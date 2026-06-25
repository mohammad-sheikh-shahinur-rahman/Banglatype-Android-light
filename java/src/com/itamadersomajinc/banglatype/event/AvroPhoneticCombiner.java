/*
 * Copyright (C) 2024 BanglaType
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

package com.itamadersomajinc.banglatype.event;

import com.itamadersomajinc.banglatype.keyboard.common.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A {@link Combiner} that implements Avro Phonetic transliteration for Bangla.
 *
 * <p>The user types Bangla phonetically using the Latin (QWERTY) keyboard, e.g. typing
 * {@code "amar nam"} produces {@code "আমার নাম"}. Latin letters are consumed and accumulated in a
 * roman buffer; the whole buffer is re-transliterated to Bangla on every keystroke and exposed as
 * the composing-state feedback. The Bangla composing word is committed by the framework when a
 * separator (space, punctuation, etc.) is typed.</p>
 *
 * <p>The transliteration is a longest-match scan over a phonetic rule table. Vowels render as
 * independent letters at the start of a syllable and as dependent vowel signs (kar) after a
 * consonant. Two consecutive consonants are automatically joined with a hasanta (virama) to form
 * conjuncts.</p>
 */
public class AvroPhoneticCombiner implements Combiner {

    private static final char HASANTA = '্'; // BENGALI SIGN VIRAMA
    private static final char JA = 'য';      // BENGALI LETTER YA (jo-fola base)
    private static final char YYA = 'য়';     // BENGALI LETTER YYA (য়)
    private static final int MAX_KEY_LEN = 3;

    // Consonants: roman -> Bangla consonant.
    private static final Map<String, String> CONS = new HashMap<>();
    // Vowels: roman -> { independent form, dependent sign (kar) }.
    private static final Map<String, String[]> VOWEL = new HashMap<>();
    // Free-standing signs that attach to the previous output (do not start a new syllable).
    private static final Map<String, String> SIGN = new HashMap<>();

    static {
        // ---- Consonants (multi-char variants must also exist as longest matches) ----
        CONS.put("k", "ক");   CONS.put("kh", "খ");  CONS.put("q", "ক");
        CONS.put("g", "গ");   CONS.put("gh", "ঘ");  CONS.put("Ng", "ঙ");
        CONS.put("c", "চ");   CONS.put("ch", "ছ");
        CONS.put("j", "জ");   CONS.put("jh", "ঝ");  CONS.put("J", "জ");
        CONS.put("NG", "ঞ");
        CONS.put("T", "ট");   CONS.put("Th", "ঠ");
        CONS.put("D", "ড");   CONS.put("Dh", "ঢ");  CONS.put("N", "ণ");
        CONS.put("t", "ত");   CONS.put("th", "থ");
        CONS.put("d", "দ");   CONS.put("dh", "ধ");  CONS.put("n", "ন");
        CONS.put("p", "প");   CONS.put("ph", "ফ");  CONS.put("f", "ফ");
        CONS.put("b", "ব");   CONS.put("bh", "ভ");  CONS.put("v", "ভ");
        CONS.put("m", "ম");
        CONS.put("z", "জ");   CONS.put("Z", "জ");
        CONS.put("r", "র");   CONS.put("R", "ড়");   CONS.put("Rh", "ঢ়");
        CONS.put("l", "ল");
        CONS.put("sh", "শ");  CONS.put("S", "শ");   CONS.put("Sh", "ষ");
        CONS.put("s", "স");   CONS.put("h", "হ");
        CONS.put("Y", YYA + "");
        CONS.put("kkh", "ক্ষ"); // ক্ষ
        CONS.put("gg", "জ্ঞ");  // জ্ঞ

        // ---- Vowels: { independent, kar } ----
        VOWEL.put("o", new String[] {"অ", ""});           // অ / inherent (no sign)
        VOWEL.put("a", new String[] {"আ", "া"});     // আ / া
        VOWEL.put("i", new String[] {"ই", "ি"});     // ই / ি
        VOWEL.put("I", new String[] {"ঈ", "ী"});     // ঈ / ী
        VOWEL.put("u", new String[] {"উ", "ু"});     // উ / ু
        VOWEL.put("U", new String[] {"ঊ", "ূ"});     // ঊ / ূ
        VOWEL.put("rri", new String[] {"ঋ", "ৃ"});   // ঋ / ৃ
        VOWEL.put("e", new String[] {"এ", "ে"});     // এ / ে
        VOWEL.put("E", new String[] {"এ", "ে"});     // এ / ে
        VOWEL.put("OI", new String[] {"ঐ", "ৈ"});    // ঐ / ৈ
        VOWEL.put("oi", new String[] {"ঐ", "ৈ"});    // ঐ / ৈ
        VOWEL.put("O", new String[] {"ও", "ো"});     // ও / ো
        VOWEL.put("OU", new String[] {"ঔ", "ৌ"});    // ঔ / ৌ
        VOWEL.put("ou", new String[] {"ঔ", "ৌ"});    // ঔ / ৌ

        // ---- Signs ----
        SIGN.put("ng", "ং");  // ং anusvara
        SIGN.put("ngo", "ং"); // (defensive) -- handled by longest match before "ng" if present
        SIGN.put(":", "ঃ");   // ঃ visarga
        SIGN.put("^", "ঁ");   // ঁ chandrabindu
        SIGN.put(".", "।");   // । danda
    }

    private final StringBuilder mRoman = new StringBuilder();

    @Override
    @Nonnull
    public Event processEvent(final ArrayList<Event> previousEvents, final Event event) {
        if (event.isFunctionalKeyEvent()) {
            if (Constants.CODE_DELETE == event.mKeyCode && mRoman.length() > 0) {
                // Back up over the roman buffer instead of the editor's text.
                mRoman.setLength(mRoman.length() - 1);
                return Event.createConsumedEvent(event);
            }
            return event;
        }
        final int codePoint = event.mCodePoint;
        if (isAvroInput(codePoint)) {
            mRoman.appendCodePoint(codePoint);
            return Event.createConsumedEvent(event);
        }
        // A separator (space, digit, punctuation...). Do not consume it: the framework commits the
        // current composing Bangla word and then applies this event. reset() clears the buffer.
        return event;
    }

    private static boolean isAvroInput(final int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z');
    }

    /** Transliterate a roman string to Bangla using longest-match phonetic rules. */
    static String transliterate(final String s) {
        final StringBuilder out = new StringBuilder();
        boolean lastConsonant = false;
        final int n = s.length();
        int i = 0;
        while (i < n) {
            String key = null;
            int matchLen = 0;
            for (int len = Math.min(MAX_KEY_LEN, n - i); len >= 1; --len) {
                final String sub = s.substring(i, i + len);
                if (CONS.containsKey(sub) || VOWEL.containsKey(sub) || SIGN.containsKey(sub)
                        || "y".equals(sub)) {
                    key = sub;
                    matchLen = len;
                    break;
                }
            }
            if (null == key) {
                out.append(s.charAt(i));
                lastConsonant = false;
                i += 1;
                continue;
            }
            i += matchLen;

            if ("y".equals(key)) {
                if (lastConsonant) {
                    out.append(HASANTA).append(JA); // jo-fola ্য
                } else {
                    out.append(YYA); // য়
                }
                lastConsonant = false;
            } else if (VOWEL.containsKey(key)) {
                final String[] forms = VOWEL.get(key);
                out.append(lastConsonant ? forms[1] : forms[0]);
                lastConsonant = false;
            } else if (CONS.containsKey(key)) {
                if (lastConsonant) {
                    out.append(HASANTA);
                }
                out.append(CONS.get(key));
                lastConsonant = true;
            } else { // SIGN
                out.append(SIGN.get(key));
                lastConsonant = false;
            }
        }
        return out.toString();
    }

    @Override
    public CharSequence getCombiningStateFeedback() {
        return transliterate(mRoman.toString());
    }

    @Override
    public void reset() {
        mRoman.setLength(0);
    }

    public String getRomanString() {
        return mRoman.toString();
    }
}

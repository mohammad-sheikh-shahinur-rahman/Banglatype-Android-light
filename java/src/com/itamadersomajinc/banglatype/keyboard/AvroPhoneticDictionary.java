package com.itamadersomajinc.banglatype.keyboard;

import com.android.inputmethod.latin.Dictionary;
import com.itamadersomajinc.banglatype.keyboard.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AvroPhoneticDictionary {
    private static final Map<String, String[]> MAPPINGS = new HashMap<>();
    static {
        MAPPINGS.put("amar", new String[]{"আমার"});
        MAPPINGS.put("ami", new String[]{"আমি"});
        MAPPINGS.put("amra", new String[]{"আমরা"});
        MAPPINGS.put("tumi", new String[]{"তুমি"});
        MAPPINGS.put("tomar", new String[]{"তোমার"});
        MAPPINGS.put("tomader", new String[]{"তোমাদের"});
        MAPPINGS.put("bhalo", new String[]{"ভালো", "ভাল"});
        MAPPINGS.put("ache", new String[]{"আছে"});
        MAPPINGS.put("achen", new String[]{"আছেন"});
        MAPPINGS.put("kemon", new String[]{"কেমন"});
        MAPPINGS.put("dhonnobad", new String[]{"ধন্যবাদ"});
        MAPPINGS.put("bangla", new String[]{"বাংলা"});
        MAPPINGS.put("bangladesh", new String[]{"বাংলাদেশ"});
        MAPPINGS.put("onek", new String[]{"অনেক"});
        MAPPINGS.put("khub", new String[]{"খুব"});
        MAPPINGS.put("ebong", new String[]{"এবং"});
        MAPPINGS.put("kintu", new String[]{"কিন্তু"});
        MAPPINGS.put("shomoy", new String[]{"সময়"});
        MAPPINGS.put("ekhon", new String[]{"এখন"});
        MAPPINGS.put("shundor", new String[]{"সুন্দর"});
        MAPPINGS.put("shathe", new String[]{"সাথে"});
        MAPPINGS.put("kaj", new String[]{"কাজ"});
        MAPPINGS.put("hobe", new String[]{"হবে"});
        MAPPINGS.put("nam", new String[]{"নাম"});
        MAPPINGS.put("phone", new String[]{"ফোন"});
        MAPPINGS.put("thik", new String[]{"ঠিক"});
        MAPPINGS.put("ki", new String[]{"কি", "কী"});
        MAPPINGS.put("kothay", new String[]{"কোথায়"});
        MAPPINGS.put("bondu", new String[]{"বন্ধু"});
        MAPPINGS.put("bari", new String[]{"বাড়ি"});
        MAPPINGS.put("manush", new String[]{"মানুষ"});
        MAPPINGS.put("desh", new String[]{"দেশ"});
        MAPPINGS.put("r", new String[]{"আর"});
        MAPPINGS.put("tai", new String[]{"তাই"});
        MAPPINGS.put("dure", new String[]{"দূরে"});
        MAPPINGS.put("kache", new String[]{"কাছে"});
        MAPPINGS.put("upore", new String[]{"উপরে"});
        MAPPINGS.put("niche", new String[]{"নিচে"});
        MAPPINGS.put("basha", new String[]{"ভাষা", "বাসা"});
        MAPPINGS.put("khabar", new String[]{"খাবার"});
        MAPPINGS.put("khacchi", new String[]{"খাচ্ছি"});
        MAPPINGS.put("likhchi", new String[]{"লিখছি"});
    }

    public static List<SuggestedWordInfo> getSuggestions(final String roman, final Dictionary sourceDict) {
        final List<SuggestedWordInfo> list = new ArrayList<>();
        if (roman == null || roman.isEmpty()) {
            return list;
        }

        final String normRoman = roman.toLowerCase();

        // 1. Exact or prefix matches
        final List<String> matchedKeys = new ArrayList<>();
        for (final String key : MAPPINGS.keySet()) {
            if (key.startsWith(normRoman)) {
                matchedKeys.add(key);
            }
        }

        // Sort matching keys by length so closer matches come first
        Collections.sort(matchedKeys, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return Integer.compare(s1.length(), s2.length());
            }
        });

        final Set<String> addedBangla = new HashSet<>();
        for (final String key : matchedKeys) {
            final String[] words = MAPPINGS.get(key);
            for (final String word : words) {
                if (!addedBangla.contains(word)) {
                    int score = (key.equals(normRoman)) ? 250 : 200 - (key.length() - normRoman.length()) * 10;
                    if (score < 50) score = 50;
                    list.add(new SuggestedWordInfo(word, null, score, SuggestedWordInfo.KIND_CORRECTION, sourceDict, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE));
                    addedBangla.add(word);
                }
            }
        }

        // 2. Spell/autocorrect matches for typos (e.g. edit distance = 1)
        if (list.isEmpty() && normRoman.length() > 2) {
            for (final String key : MAPPINGS.keySet()) {
                if (getEditDistance(normRoman, key) <= 1) {
                    final String[] words = MAPPINGS.get(key);
                    for (final String word : words) {
                        if (!addedBangla.contains(word)) {
                            list.add(new SuggestedWordInfo(word, null, 150, SuggestedWordInfo.KIND_CORRECTION, sourceDict, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE));
                            addedBangla.add(word);
                        }
                    }
                }
            }
        }

        return list;
    }

    private static int getEditDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}

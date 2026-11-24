package com.videoconverter.service;

import java.util.ArrayList;
import java.util.List;

/** Small helper: splits long text into chunks at sentence ends. */
final class TextUtil {
    private TextUtil() {}

    static List<String> split(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?।])\\s+")) {
            // a single huge sentence: cut it hard
            while (sentence.length() > maxChars) {
                if (current.length() > 0) { chunks.add(current.toString()); current.setLength(0); }
                chunks.add(sentence.substring(0, maxChars));
                sentence = sentence.substring(maxChars);
            }
            if (current.length() + sentence.length() + 1 > maxChars) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(sentence);
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }
}

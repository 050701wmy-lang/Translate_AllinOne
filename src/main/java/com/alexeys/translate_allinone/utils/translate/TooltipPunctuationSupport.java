package com.alexeys.translate_allinone.utils.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Remove unsolicited prose bullets without rewriting protected data or source punctuation. */
final class TooltipPunctuationSupport {
    private static final Pattern MARKER = Pattern.compile("</?s\\d+>|§[0-9a-fk-or]|\\{[A-Za-z][A-Za-z0-9_.:-]*}", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_DASH = Pattern.compile("[-\\p{Pd}−•]");

    private TooltipPunctuationSupport() {}

    static String clean(String source, String translated) {
        if (source == null || translated == null || translated.indexOf('-') < 0) return translated;
        // A genuine source dash does not authorize unlimited extra bullets in the reply.
        long sourceDashes = SOURCE_DASH.matcher(project(source).text()).results().count();
        long translatedDashes = translated.chars().filter(c -> c == '-').count();
        if (sourceDashes >= translatedDashes) return translated;
        long retainedSourceDashes = sourceDashes;
        Projection visible = project(translated);
        boolean[] remove = new boolean[translated.length()];
        var dashes = Pattern.compile("-(?:[ \\t]*-)*").matcher(visible.text());
        while (dashes.find()) {
            int i = dashes.start();
            // Preserve explicit divider runs; separated bullet-like dashes are a different case.
            if (dashes.group().matches("-{2,}")) continue;
            int before = i - 1, after = dashes.end();
            while (before >= 0 && Character.isWhitespace(visible.text().charAt(before))) before--;
            while (after < visible.text().length() && Character.isWhitespace(visible.text().charAt(after))) after++;
            char left = before < 0 ? '\0' : visible.text().charAt(before);
            char right = after == visible.text().length() ? '\0' : visible.text().charAt(after);
            // A minus before an explicitly positive value is an added bullet, not
            // the sign of that value. Numeric placeholders project to a digit too.
            if (right == '+' && after + 1 < visible.text().length()
                    && Character.isDigit(visible.text().charAt(after + 1))
                    && !project(source).text().contains("-+")) {
                for (int offset = i; offset < dashes.end(); offset++) remove[visible.offsets().get(offset)] = true;
                continue;
            }
            if (Character.isDigit(right)) continue;
            if (Character.isDigit(left) && before == i - 1) continue;
            if (dashes.group().equals("-") && asciiLetter(left) && asciiLetter(right)
                    && before == i - 1 && after == dashes.end()) continue;
            if (han(left) || han(right) || (dashes.end() > i + 1 && Character.isLetter(right))
                    || ((before < 0 || before < i - 1) && Character.isLetter(right))) {
                if (retainedSourceDashes > 0) {
                    retainedSourceDashes--;
                    // A cluster can contain both the original separator and an
                    // added dash at the next color boundary: " - <sN>-Name".
                    for (int offset = i + 1; offset < dashes.end(); offset++) {
                        if (visible.text().charAt(offset) == '-') remove[visible.offsets().get(offset)] = true;
                    }
                    continue;
                }
                for (int offset = i; offset < dashes.end(); offset++) remove[visible.offsets().get(offset)] = true;
            }
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < translated.length(); i++) if (!remove[i]) result.append(translated.charAt(i));
        return result.toString();
    }

    private static boolean asciiLetter(char c) { return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'; }
    private static boolean han(char c) { return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN; }

    private static Projection project(String text) {
        StringBuilder visible = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        var markers = MARKER.matcher(text);
        int start = 0;
        while (markers.find()) {
            append(text, start, markers.start(), visible, offsets);
            // Numbers are opaque but must still protect their unary minus signs.
            if (markers.group().matches("\\{(?:d|value)\\d+}")) {
                visible.append('0');
                offsets.add(markers.start());
            }
            start = markers.end();
        }
        append(text, start, text.length(), visible, offsets);
        return new Projection(visible.toString(), offsets);
    }

    private static void append(String source, int start, int end, StringBuilder text, List<Integer> offsets) {
        for (int i = start; i < end; i++) { text.append(source.charAt(i)); offsets.add(i); }
    }

    private record Projection(String text, List<Integer> offsets) {}
}

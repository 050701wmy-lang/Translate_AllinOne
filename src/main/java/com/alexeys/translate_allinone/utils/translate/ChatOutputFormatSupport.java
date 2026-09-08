package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.text.StylePreserver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** The source owns layout and styles; the model supplies only the text inside each run. */
final class ChatOutputFormatSupport {
    private static final Pattern TAG = Pattern.compile("<s(\\d+)>(.*?)</s\\1>", Pattern.DOTALL);
    private static final Pattern MARKER = Pattern.compile("</?s\\d+>");
    private static final Pattern TOKEN = Pattern.compile("\\{[gcdn]\\d+}");
    private static final Pattern PREFIX = Pattern.compile("^(?:(?:\\{[gcdn]\\d+})|[^\\p{L}\\p{N}])*");
    private static final Pattern SUFFIX = Pattern.compile("\\s*$");

    private ChatOutputFormatSupport() { }

    static StylePreserver.ExtractionResult uniqueRuns(String marked, Map<Integer, Style> originalStyles) {
        Map<Integer, Style> styles = new LinkedHashMap<>(originalStyles);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int nextId = styles.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        var runs = TAG.matcher(marked);
        StringBuilder result = new StringBuilder();
        while (runs.find()) {
            int originalId = Integer.parseInt(runs.group(1));
            int id = originalId;
            if (!seen.add(originalId)) {
                id = nextId++;
                styles.put(id, originalStyles.getOrDefault(originalId, Style.EMPTY));
            }
            runs.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    "<s" + id + ">" + runs.group(2) + "</s" + id + ">"));
        }
        return new StylePreserver.ExtractionResult(runs.appendTail(result).toString(), styles);
    }

    static String runRequest(String source) {
        var object = new com.google.gson.JsonObject();
        var tags = TAG.matcher(source);
        while (tags.find()) {
            if (hasWords(tags.group(2))) object.addProperty(tags.group(1), tags.group(2));
        }
        return object.toString();
    }

    static String runReply(String source, String reply) {
        String json = reply.strip();
        if (json.startsWith("```json") && json.endsWith("```")) json = json.substring(7, json.length() - 3).strip();
        try {
            var expected = com.google.gson.JsonParser.parseString(runRequest(source)).getAsJsonObject();
            var actual = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!actual.keySet().equals(expected.keySet())) throw new InvalidFormat("Changed chat run IDs");
            StringBuilder tagged = new StringBuilder();
            for (String id : expected.keySet()) {
                var value = actual.get(id);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new InvalidFormat("Non-text chat run");
                }
                tagged.append("<s").append(id).append('>').append(value.getAsString())
                        .append("</s").append(id).append('>');
            }
            return restore(source, tagged.toString());
        } catch (RuntimeException ex) {
            throw new InvalidFormat("Invalid chat run response: " + ex.getMessage());
        }
    }

    static StylePreserver.ExtractionResult extract(Component source) {
        StringBuilder marked = new StringBuilder();
        Map<Integer, Style> styles = new LinkedHashMap<>();
        source.visit((style, text) -> {
            String[] lines = text.split("\n", -1);
            for (int line = 0; line < lines.length; line++) {
                if (line > 0) marked.append('\n');
                if (!lines[line].isEmpty()) {
                    int id = styles.size();
                    styles.put(id, style);
                    marked.append("<s").append(id).append('>').append(lines[line]).append("</s").append(id).append('>');
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new StylePreserver.ExtractionResult(marked.toString(), styles);
    }

    static String restore(String source, String reply) {
        Map<String, String> originals = new LinkedHashMap<>();
        var originalTags = TAG.matcher(source);
        while (originalTags.find()) originals.put(originalTags.group(1), originalTags.group(2));
        long textRuns = originals.values().stream().filter(ChatOutputFormatSupport::hasWords).count();
        if (textRuns == 0) return source;
        String normalized = (reply == null ? "" : reply).replaceAll("\\{(/?s\\d+)}", "<$1>");
        Map<String, String> replacements = new LinkedHashMap<>();
        var responseTags = TAG.matcher(normalized);
        while (responseTags.find()) {
            String id = responseTags.group(1);
            if (!originals.containsKey(id) || replacements.put(id, responseTags.group(2)) != null) {
                throw new InvalidFormat("Unknown or duplicate chat style run");
            }
        }
        if (replacements.isEmpty() && textRuns == 1) {
            // A single text run is unambiguous even if the provider omits or changes its delimiters.
            var markers = MARKER.matcher(normalized);
            while (markers.find()) {
                if (!originals.containsKey(markers.group().replaceAll("\\D", ""))) {
                    throw new InvalidFormat("Unknown chat style marker");
                }
            }
            String text = MARKER.matcher(normalized).replaceAll("");
            if (text.contains("<s") || text.contains("</s")) throw new InvalidFormat("Incomplete chat style marker");
            for (var entry : originals.entrySet()) {
                if (hasWords(entry.getValue())) replacements.put(entry.getKey(), text);
            }
        } else if (hasWords(TAG.matcher(normalized).replaceAll(""))) {
            throw new InvalidFormat("Text outside chat style runs");
        }
        StringBuilder restored = new StringBuilder();
        originalTags.reset();
        int end = 0;
        while (originalTags.find()) {
            restored.append(source, end, originalTags.start());
            String original = originalTags.group(2);
            String content = original;
            if (hasWords(original)) {
                String translated = replacements.get(originalTags.group(1));
                if (translated == null || !hasWords(translated) || MARKER.matcher(translated).find()) {
                    throw new InvalidFormat("Missing chat style run");
                }
                var leading = PREFIX.matcher(original);
                leading.find();
                var trailing = SUFFIX.matcher(original);
                trailing.find();
                content = leading.group() + PREFIX.matcher(translated).replaceFirst("").stripTrailing() + trailing.group();
                if (Pattern.compile("\\{/?[sgcn]\\d*").matcher(TOKEN.matcher(content).replaceAll("")).find()) {
                    throw new InvalidFormat("Malformed chat placeholder");
                }
                if (!tokens(original).equals(tokens(content))) throw new InvalidFormat("Changed chat placeholders");
                if (original.chars().filter(c -> c == '\n').count() != content.chars().filter(c -> c == '\n').count()) {
                    throw new InvalidFormat("Changed explicit chat line breaks");
                }
            }
            restored.append("<s").append(originalTags.group(1)).append('>').append(content)
                    .append("</s").append(originalTags.group(1)).append('>');
            end = originalTags.end();
        }
        return restored.append(source.substring(end)).toString();
    }

    private static boolean hasWords(String value) {
        return TOKEN.matcher(value).replaceAll("").codePoints().anyMatch(Character::isLetter);
    }

    private static Map<String, Integer> tokens(String value) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        var matcher = TOKEN.matcher(value);
        while (matcher.find()) counts.merge(matcher.group(), 1, Integer::sum);
        return counts;
    }

    static final class InvalidFormat extends IllegalArgumentException {
        InvalidFormat(String message) { super(message); }
    }
}

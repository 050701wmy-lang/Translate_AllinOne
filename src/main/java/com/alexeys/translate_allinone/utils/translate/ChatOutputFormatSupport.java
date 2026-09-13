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
        // Servers often send legacy codes inside literal Components. Decode them before
        // numeric extraction or sentence translation; they are styles, never prose tokens.
        source = normalize(source);
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

    static Component normalize(Component source) {
        var result = Component.empty();
        source.visit((style, text) -> {
            Component decoded = text.indexOf('§') < 0 ? Component.literal(text)
                    : StylePreserver.fromLegacyText(text);
            decoded.visit((local, value) -> {
                if (!value.isEmpty()) {
                    Style effective = local.applyTo(style);
                    var siblings = result.getSiblings();
                    if (!siblings.isEmpty() && siblings.getLast().getStyle().equals(effective)) {
                        Component previous = siblings.removeLast();
                        result.append(Component.literal(previous.getString() + value).setStyle(effective));
                    } else result.append(Component.literal(value).setStyle(effective));
                }
                return Optional.empty();
            }, Style.EMPTY);
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    /** A sentence may reorder its colored phrases and move numbers with their meaning. */
    static String restoreSentence(String source, String reply) {
        if (reply == null) throw new InvalidFormat("Missing sentence");
        try {
            com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationValidator
                    .validateSentenceStyles(source, reply);
        } catch (com.alexeys.translate_allinone.utils.componentjson.ComponentJsonException error) {
            throw new InvalidFormat(error.getMessage());
        }
        Map<String, String> originalRuns = new LinkedHashMap<>();
        var runs = TAG.matcher(source);
        while (runs.find()) originalRuns.put(runs.group(1), runs.group(2));
        var seen = new java.util.HashSet<String>();
        runs = TAG.matcher(reply);
        int end = 0;
        while (runs.find()) {
            if (!TOKEN.matcher(reply.substring(end, runs.start())).replaceAll("").isBlank()
                    || !originalRuns.containsKey(runs.group(1))
                    || MARKER.matcher(runs.group(2)).find()) {
                throw new InvalidFormat("Invalid sentence style runs");
            }
            seen.add(runs.group(1));
            end = runs.end();
        }
        if (!TOKEN.matcher(reply.substring(end)).replaceAll("").isBlank() || !seen.equals(originalRuns.keySet())
                || !tokens(source).equals(tokens(reply)) || reply.indexOf('§') >= 0
                || source.chars().filter(c -> c == '\n').count() != reply.chars().filter(c -> c == '\n').count()) {
            throw new InvalidFormat("Changed sentence styles, values or explicit lines");
        }
        // Do not prepend source punctuation/tokens or put runs back in English order.
        return reply;
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
                // A comma belongs to the sentence; unlike indentation/bullets it must
                // not be copied in front of translated punctuation a second time.
                content = leading.group().matches("(?s).*[,.!?;:，。！？；：].*")
                        ? translated.stripTrailing() + trailing.group()
                        : leading.group() + PREFIX.matcher(translated).replaceFirst("").stripTrailing() + trailing.group();
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

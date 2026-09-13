package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Shared text identity; specialized callers still own formatting, eligibility and layout. */
public final class SharedHudTranslationSupport {
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");
    private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_TAG = Pattern.compile("</?s\\d+>");
    private static final Set<String> ENGLISH_PROSE = Set.of("this", "item", "can", "be", "reforged",
            "grants", "increases", "your", "chance", "of", "dropping", "multiple", "crops", "when",
            "from", "within", "after", "before", "for", "with", "the", "to", "and", "is", "are",
            "cooldown", "price", "sell", "fortune", "follow", "flows", "understand", "themselves",
            "stats", "notes", "earned", "during", "spent");

    static boolean hasIncompleteTooltipProse(String source, String translated, String language) {
        String target = language == null ? "" : language.toLowerCase(Locale.ROOT);
        if (!(target.contains("chinese") || target.startsWith("zh") || target.contains("中文"))) return false;
        Set<String> sourceWords = proseWords(source);
        sourceWords.retainAll(proseWords(translated));
        return !sourceWords.isEmpty();
    }

    private static Set<String> proseWords(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) return result;
        String visible = FORMAT.matcher(text).replaceAll("").replaceAll("</?s\\d+>|\\{[^}]+}", " ");
        var words = WORD.matcher(visible);
        while (words.find()) {
            String word = words.group().toLowerCase(Locale.ROOT);
            if (ENGLISH_PROSE.contains(word)) result.add(word);
        }
        return result;
    }

    static boolean hasTranslatableWords(String text) {
        String readable = FORMAT.matcher(text).replaceAll("")
                .replaceAll("</?s\\d+>", "")
                .replaceAll("\\{d\\d+}(?:st|nd|rd|th|[kKmMbBtT%]|ms|[smhd])?", "")
                .replaceAll("\\{[A-Za-z][A-Za-z0-9_.:-]*}", "");
        return WORD.matcher(readable).find();
    }

    private static String plainTranslation(String text) {
        return text == null ? null : FORMAT.matcher(text).replaceAll("");
    }

    private SharedHudTranslationSupport() {}

    public static boolean supports(ComponentTranslationDocument document) {
        if (document == null) return false;
        return switch (document.route()) {
            case BOSS_BAR, SCOREBOARD, ENTITY_NAME, TEXT_DISPLAY,
                    TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> true;
            case CHAT_OUTPUT -> "true".equals(document.semanticSettings().get("shared_npc"));
            default -> false;
        };
    }

    private static String scope() {
        Minecraft client = Minecraft.getInstance();
        String host = client == null || client.getCurrentServer() == null ? "local"
                : client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        return host;
    }

    static String dictionary(String source, String language) {
        String target = language == null ? "" : language.toLowerCase(Locale.ROOT);
        if (!(target.contains("chinese") || target.startsWith("zh") || target.contains("中文"))) return null;
        var result = WynnSharedDictionaryService.getInstance().lookupItemLine(source);
        return result.hit() ? result.translation() : null;
    }

    static String repairTooltipResidue(String source, String translated, String language) {
        return repairTooltipResidue(source, translated, text -> dictionary(text, language));
    }

    static String repairTooltipResidue(String source, String translated, Function<String, String> lookup) {
        if (source == null || translated == null || translated.isBlank()) return translated;
        String visibleSource = STYLE_TAG.matcher(FORMAT.matcher(source).replaceAll("")).replaceAll("");
        // Only translate glossary words also present in the source. Unknown names/abbreviations stay intact.
        Plan repair = plan(translated, term -> Pattern.compile(
                (term.equalsIgnoreCase("Coins") ? "(?<![A-Za-z0-9_])(?:[0-9][0-9,.]*\\s*)?" : "(?<![A-Za-z0-9_])") + Pattern.quote(term)
                + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE).matcher(visibleSource).find() ? lookup.apply(term) : null);
        return repair.restore(repair.exact() == null ? repair.text() : repair.exact());
    }

    static List<String> pieces(String source) {
        var styles = STYLE_TAG.matcher(source);
        if (styles.find()) {
            List<String> result = new ArrayList<>();
            int offset = 0;
            do {
                result.addAll(pieces(source.substring(offset, styles.start())));
                result.add(styles.group());
                offset = styles.end();
            } while (styles.find());
            result.addAll(pieces(source.substring(offset)));
            return result;
        }
        var formatting = FORMAT.matcher(source);
        if (formatting.find()) {
            List<String> result = new ArrayList<>();
            int offset = 0;
            do {
                result.addAll(pieces(source.substring(offset, formatting.start())));
                result.add(formatting.group());
                offset = formatting.end();
            } while (formatting.find());
            result.addAll(pieces(source.substring(offset)));
            return result;
        }
        if (source.contains("\n")) {
            List<String> result = new ArrayList<>();
            for (String line : source.split("(?<=\\n)|(?=\\n)", -1)) {
                if (line.equals("\n")) result.add(line);
                else result.addAll(pieces(line));
            }
            return result;
        }
        // A heading belongs to the label domain, not to the task sentence identity.
        var heading = Pattern.compile("^(\\s*Objective)(:\\s*)(.*)$", Pattern.DOTALL).matcher(source);
        return heading.matches() ? List.of(heading.group(1), heading.group(2), heading.group(3))
                : List.of(source);
    }

    static Plan plan(String source, Function<String, String> lookup) {
        String text = source.strip();
        int start = source.indexOf(text);
        String prefix = source.substring(0, start), suffix = source.substring(start + text.length());
        String exact = plainTranslation(lookup.apply(text));
        if (exact != null && !exact.isBlank() && !exact.equalsIgnoreCase(text)) return new Plan(prefix, suffix, text, Map.of(), exact);
        var words = WORD.matcher(text);
        List<int[]> spans = new ArrayList<>();
        while (words.find()) {
            // Never treat placeholder identifiers as dictionary terms.
            if (text.lastIndexOf('{', words.start()) > text.lastIndexOf('}', words.start())) continue;
            if (text.lastIndexOf('<', words.start()) > text.lastIndexOf('>', words.start())) continue;
            spans.add(new int[]{words.start(), words.end()});
        }
        Map<String, String> terms = new LinkedHashMap<>();
        StringBuilder masked = new StringBuilder();
        int copied = 0;
        for (int i = 0; i < spans.size(); i++) {
            for (int j = Math.min(spans.size() - 1, i + 5); j >= i; j--) {
                String candidate = text.substring(spans.get(i)[0], spans.get(j)[1]);
                if (!candidate.matches("[A-Za-z'’ -]+")) continue;
                String translated = plainTranslation(lookup.apply(candidate));
                if (translated == null || translated.isBlank() || translated.equalsIgnoreCase(candidate)) continue;
                String token = "{taio.term." + terms.size() + "}";
                if (text.contains(token)) continue;
                masked.append(text, copied, spans.get(i)[0]).append(token);
                copied = spans.get(j)[1];
                terms.put(token, translated);
                i = j;
                break;
            }
        }
        masked.append(text.substring(copied));
        return new Plan(prefix, suffix, masked.toString(), Map.copyOf(terms), null);
    }

    static ComponentTranslationDocument document(Plan plan, String scope, Set<String> tokens) {
        Set<String> protectedTokens = new HashSet<>(tokens);
        protectedTokens.removeIf(token -> !plan.text().contains(token));
        plan.terms().keySet().stream().filter(key -> key.startsWith("{taio.term.")).forEach(protectedTokens::add);
        var policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.SCOREBOARD)
                .withContext("shared-hud-text; reference glossary: " + new TreeMap<>(plan.terms()))
                .withPrivateTokens(protectedTokens)
                .withSemanticSetting("shared_hud", "v6-original-text")
                .withSemanticSetting("server", scope)
                .withSemanticSetting("terms", new TreeMap<>(plan.terms()).toString());
        return ComponentTranslationRuntime.prepare(Component.literal(plan.text()), policy);
    }

    static ComponentTranslationDocument document(ComponentTranslationDocument original,
            ComponentTextUnit unit, Plan plan, String scope) {
        if (!isCoherentProse(original)) {
            var shared = document(plan, scope, unit.protectedTokens().keySet());
            if ("hypixel".equals(original.semanticSettings().get("provider_route"))) {
                var settings = new TreeMap<>(shared.semanticSettings());
                settings.put("provider_route", "hypixel");
                return new ComponentTranslationDocument(shared.protocol(), shared.policyVersion(), shared.route(),
                        shared.sourceJson(), shared.units(), settings);
            }
            return shared;
        }
        if ("v1".equals(original.semanticSettings().get("styled_sentence"))
                && (original.route() == ComponentTranslationRoute.BOSS_BAR
                || original.route() == ComponentTranslationRoute.SCOREBOARD)) {
            // Only identical styled sentences share a response. Rendering metadata and the
            // surface route are local concerns; hard tokens and style topology remain in the key.
            var policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.SCOREBOARD)
                    .withContext("shared-hud-sentence")
                    .withPrivateTokens(unit.protectedTokens().keySet())
                    .withSemanticSetting("styled_sentence", "v1")
                    .withSemanticSetting("shared_surface", "v1");
            String providerRoute = original.semanticSettings().get("provider_route");
            if (providerRoute != null) policy = policy.withSemanticSetting("provider_route", providerRoute);
            original = ComponentTranslationRuntime.prepare(Component.literal(plan.text()), policy);
            unit = original.units().getFirst();
        }
        // Keep the paragraph protocol and its anchor topology; it has a dedicated response validator.
        var tokens = new TreeMap<>(unit.protectedTokens());
        var settings = new TreeMap<>(original.semanticSettings());
        settings.put("shared_hud", "v6-original-sentence");
        settings.put("server", scope);
        settings.put("terms", new TreeMap<>(plan.terms()).toString());
        return new ComponentTranslationDocument(original.protocol(), original.policyVersion(), original.route(),
                original.sourceJson(), List.of(new ComponentTextUnit(unit.id(), unit.jsonPointer(),
                plan.text(), tokens, unit.context() + "; translate the entire sentence across all color runs as one coherent message; "
                        + "preserve paired style tags, numbers and glyph placeholders; reorder colored phrases as needed for natural target-language grammar; "
                        + "a style span may split into at most four balanced spans using the same id to surround another colored phrase; "
                        + "use every source style id, invent none; preserve each number and glyph placeholder exactly, never duplicate them; do not add bullets or dashes; "
                        + "reference glossary (source = translation): " + new TreeMap<>(plan.terms())
                        + "; keep NPC/player names without glossary entries exactly as written, never transliterate only part of a name"
                        + "; translate ALL remaining English prose, preserving the sentence meaning")), settings);
    }

    public static <T> ComponentTranslationRuntime.Resolution<T> resolve(
            ComponentTranslationDocument original, String language,
            Function<ComponentTranslationResponse, T> renderer, boolean queue) {
        Map<String, String> values = new LinkedHashMap<>();
        ComponentTranslationRuntimeCore.Resolution<String> waiting = null;
        String scope = scope();
        for (var unit : original.units()) {
            StringBuilder output = new StringBuilder();
            for (String piece : pieces(original, unit.sourceText())) {
                if (piece.isBlank() || !hasTranslatableWords(piece)) { output.append(piece); continue; }
                Plan plan = planFor(original, piece, text -> dictionary(text, language));
                if (plan.exact() != null) {
                    if (!isCoherentProse(original) || !hasIncompleteTooltipProse(piece, plan.exact(), language)) {
                        output.append(plan.restore(plan.exact()));
                        continue;
                    }
                    // A dictionary pattern may only have translated its fixed label, leaving captured prose.
                    plan = new Plan(plan.prefix(), plan.suffix(), piece.strip(), Map.of(), null);
                }
                if (!hasTranslatableWords(plan.text())) { output.append(plan.restore(plan.text())); continue; }
                var shared = document(original, unit, plan, scope);
                if (shared.units().isEmpty()) { output.append(plan.restore(plan.text())); continue; }
                var result = ComponentTranslationRuntimeCore.resolve(shared, language, null, () -> null,
                        response -> {
                            String translated = response.translations().get(shared.units().getFirst().id());
                            if (isCoherentProse(original)) {
                                translated = TooltipPunctuationSupport.clean(piece, translated);
                                if ((original.route() == ComponentTranslationRoute.CHAT_OUTPUT
                                        || "v1".equals(original.semanticSettings().get("styled_sentence")))
                                        && piece.contains("<s")) {
                                    translated = ChatOutputFormatSupport.restoreSentence(piece, translated);
                                }
                                if (isTooltip(original) && hasIncompleteTooltipProse(piece, translated, language)) {
                                    throw new IllegalArgumentException("Translation still contains untranslated source prose");
                                }
                            }
                            return translated;
                        },
                        "shared-hud-text", queue);
                if (result.state() == ComponentTranslationRuntimeCore.State.CACHE_HIT && result.value() != null) {
                    output.append(plan.restore(result.value()));
                } else {
                    if (waiting == null || result.state() == ComponentTranslationRuntimeCore.State.FAILED) waiting = result;
                    output.append(piece);
                }
            }
            values.put(unit.id(), output.toString());
        }
        if (waiting != null) return new ComponentTranslationRuntime.Resolution<>(
                ComponentTranslationRuntime.State.valueOf(waiting.state().name()), null,
                waiting.cacheKey(), waiting.errorMessage());
        return new ComponentTranslationRuntime.Resolution<>(ComponentTranslationRuntime.State.CACHE_HIT,
                renderer.apply(new ComponentTranslationResponse(original.protocol(), values)), "", "");
    }

    public static boolean forceRefresh(ComponentTranslationDocument original, String language) {
        boolean refreshed = false;
        for (var unit : original.units()) for (String piece : pieces(original, unit.sourceText())) {
            if (!hasTranslatableWords(piece)) continue;
            Plan plan = planFor(original, piece, text -> dictionary(text, language));
            if (plan.exact() == null) refreshed |= ComponentTranslationRuntimeCore.forceRefresh(
                    document(original, unit, plan, scope()), language);
        }
        return refreshed;
    }

    static List<String> pieces(ComponentTranslationDocument document, String text) {
        // Paragraph anchors carry grammatical context and must travel together.
        return isCoherentProse(document)
                ? List.of(text) : pieces(text);
    }

    private static boolean isCoherentProse(ComponentTranslationDocument document) {
        return "v1".equals(document.semanticSettings().get("styled_sentence"))
                || isTooltip(document) || document.route() == ComponentTranslationRoute.CHAT_OUTPUT
                && "true".equals(document.semanticSettings().get("shared_npc"));
    }

    private static boolean isTooltip(ComponentTranslationDocument document) {
        return switch (document.route()) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> true;
            default -> false;
        };
    }

    static Plan planFor(ComponentTranslationDocument document, String source, Function<String, String> lookup) {
        Map<String, String> glossary = new TreeMap<>();
        Plan plan = plan(source, term -> {
            String translated = lookup.apply(term);
            if (translated != null && !translated.equalsIgnoreCase(term)) glossary.put(term, plainTranslation(translated));
            return translated;
        });
        // Sharing must never rewrite the input into a Chinese/English word salad.
        // The dictionary is context for the model, not a second translation pass.
        return new Plan(plan.prefix(), plan.suffix(), source.strip(), glossary, plan.exact());
    }

    record Plan(String prefix, String suffix, String text, Map<String, String> terms, String exact) {
        String restore(String translated) {
            for (var entry : terms.entrySet()) if (entry.getKey().startsWith("{taio.term."))
                translated = translated.replace(entry.getKey(), entry.getValue());
            return prefix + translated + suffix;
        }
    }
}

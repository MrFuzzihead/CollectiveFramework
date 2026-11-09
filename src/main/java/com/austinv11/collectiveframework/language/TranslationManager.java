package com.austinv11.collectiveframework.language;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.austinv11.collectiveframework.language.translation.ITranslationProvider;
import com.austinv11.collectiveframework.language.translation.QueryLimitException;
import com.austinv11.collectiveframework.language.translation.TranslationException;
import com.austinv11.collectiveframework.language.translation.YandexProvider;

/**
 * A class for translating text.
 * <p>
 * Providers are tried in registration order. A provider that reports {@link QueryLimitException} is disabled for
 * the rest of the session and the next one is tried; any other {@link TranslationException} is a property of the
 * request (text too long, language unsupported, no API key) and is rethrown immediately rather than being
 * retried against every provider.
 */
public class TranslationManager {

    /** Upper bound on the translation cache, to keep a long session from leaking memory. */
    public static final int MAX_CACHE_ENTRIES = 1024;

    private static final List<ITranslationProvider> translators = new CopyOnWriteArrayList<ITranslationProvider>();

    // The cost of using free APIs: providers that have told us to stop asking
    private static final Set<String> unusableProviders = Collections
        .newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    // Helps reduce queries. Keyed by (text, fromLang, toLang): keying on the text alone returned the German
    // translation for a French request (CODE_REVIEW §1.9).
    private static final Map<CacheKey, String> translationCache = Collections
        .synchronizedMap(new LruCache(MAX_CACHE_ENTRIES));

    static {
        registerTranslationProvider(new YandexProvider());
    }

    /**
     * Register a translation provider, note: the earlier the registration, the higher priority it has
     *
     * @param provider The translation provider
     */
    public static void registerTranslationProvider(ITranslationProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        translators.add(provider);
    }

    /**
     * Removes every registered provider and forgets which ones were rate limited.
     * <p>
     * Mainly useful for tests and for plugins that want to replace the provider list wholesale. The framework's
     * own {@link YandexProvider} is not re-registered automatically.
     */
    public static void clearProviders() {
        translators.clear();
        unusableProviders.clear();
    }

    /**
     * Re-enables providers that were disabled because they hit a query limit.
     */
    public static void resetRateLimits() {
        unusableProviders.clear();
    }

    /**
     * Empties the translation cache.
     */
    public static void clearCache() {
        translationCache.clear();
    }

    /**
     * @return The number of cached translations
     */
    public static int getCacheSize() {
        return translationCache.size();
    }

    /**
     * Translates a given text, letting the provider detect the source language.
     *
     * @param text   String to translate
     * @param toLang Language to translate to
     * @return The translated text
     * @throws TranslationException if no provider can serve the request
     * @throws IOException          if the provider's transport fails
     */
    public static String translate(String text, String toLang) throws TranslationException, IOException {
        return translate(text, null, toLang);
    }

    /**
     * Translates a given text.
     *
     * @param text     String to translate
     * @param fromLang Language the string is from, or null to let the provider detect it
     * @param toLang   Language to translate to
     * @return The translated text
     * @throws TranslationException if no provider can serve the request
     * @throws IOException          if the provider's transport fails
     */
    public static String translate(String text, String fromLang, String toLang)
        throws TranslationException, IOException {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        if (toLang == null) throw new IllegalArgumentException("toLang must not be null");

        CacheKey key = new CacheKey(text, fromLang, toLang);
        String cached = translationCache.get(key);
        if (cached != null) return cached;

        List<ITranslationProvider> candidates = usableProviders(fromLang == null);
        for (ITranslationProvider provider : candidates) {
            try {
                String translation = fromLang == null ? provider.translate(text, toLang)
                    : provider.translate(text, fromLang, toLang);
                translationCache.put(key, translation);
                return translation;
            } catch (QueryLimitException e) {
                // This provider is exhausted; disable it and fall through to the next one.
                unusableProviders.add(provider.getProviderName());
            }
        }
        // No recursion: once every provider is exhausted this simply reports it (CODE_REVIEW §3.12).
        throw new TranslationException(TranslationException.ErrorTypes.NO_VALID_PROVIDERS);
    }

    /**
     * Method which attempts to detect the language of the given string
     *
     * @param text String to detect the language for
     * @return Language code
     * @throws TranslationException if no provider can serve the request
     * @throws IOException          if the provider's transport fails
     */
    public static String detectLanguage(String text) throws TranslationException, IOException {
        if (text == null) throw new IllegalArgumentException("text must not be null");

        List<ITranslationProvider> candidates = usableProviders(true);
        for (ITranslationProvider provider : candidates) {
            try {
                return provider.detectLangauge(text);
            } catch (QueryLimitException e) {
                unusableProviders.add(provider.getProviderName());
            }
        }
        throw new TranslationException(TranslationException.ErrorTypes.NO_VALID_PROVIDERS);
    }

    /**
     * Collects the providers that are still usable, in registration order.
     *
     * @param requireDetect Whether the provider must be able to detect a language
     * @return The usable providers, possibly empty
     */
    private static List<ITranslationProvider> usableProviders(boolean requireDetect) {
        List<ITranslationProvider> usable = new ArrayList<ITranslationProvider>();
        for (ITranslationProvider provider : translators) {
            if (unusableProviders.contains(provider.getProviderName())) continue;
            if (requireDetect && !provider.canDetectLanguage()) continue;
            usable.add(provider);
        }
        return usable;
    }

    /**
     * Cache key for a translation. Includes both languages, so the same text translated to two different
     * languages does not collide.
     */
    static final class CacheKey {

        private final String text;
        private final String fromLang; // null means "detect"
        private final String toLang;

        CacheKey(String text, String fromLang, String toLang) {
            this.text = text;
            this.fromLang = fromLang;
            this.toLang = toLang;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CacheKey)) return false;
            CacheKey key = (CacheKey) other;
            return text.equals(key.text) && Objects.equals(fromLang, key.fromLang) && toLang.equals(key.toLang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, fromLang, toLang);
        }

        @Override
        public String toString() {
            return (fromLang == null ? "auto" : fromLang) + "->" + toLang + ":" + text;
        }
    }

    /** Access-ordered {@link LinkedHashMap} that evicts the least recently used entry. */
    private static final class LruCache extends LinkedHashMap<CacheKey, String> {

        private static final long serialVersionUID = 1L;

        private final int maxEntries;

        LruCache(int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, String> eldest) {
            return size() > maxEntries;
        }
    }
}

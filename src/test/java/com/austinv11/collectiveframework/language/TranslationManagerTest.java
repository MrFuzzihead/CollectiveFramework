package com.austinv11.collectiveframework.language;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.austinv11.collectiveframework.language.translation.ITranslationProvider;
import com.austinv11.collectiveframework.language.translation.QueryLimitException;
import com.austinv11.collectiveframework.language.translation.TranslationException;
import com.austinv11.collectiveframework.language.translation.YandexProvider;

/**
 * CODE_REVIEW §1.8, §1.9 and §3.12.
 * <ul>
 * <li>§1.8 - provider selection used {@code List.contains(int)} (always false) and indexed before the bounds
 * check, so the first exhausted provider threw {@code NO_VALID_PROVIDERS} or {@code IndexOutOfBoundsException}
 * instead of falling through to the next one.</li>
 * <li>§1.9 - the cache was keyed on the text alone, so a French request returned the cached German string; it was
 * also unbounded and not thread safe.</li>
 * <li>§3.12 - exhausting a provider recursed, so a second exhaustion was a {@code StackOverflowError}.</li>
 * </ul>
 */
public class TranslationManagerTest {

    private static class FakeProvider implements ITranslationProvider {

        private final String name;
        private final boolean canDetect;
        private String result = "translated";
        private String detected = "en";
        private boolean rateLimited;
        private TranslationException failure;
        int translateCalls;
        int detectCalls;
        String lastFromLang;
        String lastToLang;

        FakeProvider(String name, boolean canDetect) {
            this.name = name;
            this.canDetect = canDetect;
        }

        FakeProvider rateLimited() {
            this.rateLimited = true;
            return this;
        }

        FakeProvider failing(TranslationException.ErrorTypes type) {
            this.failure = new TranslationException(type);
            return this;
        }

        FakeProvider returning(String value) {
            this.result = value;
            return this;
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public boolean canDetectLanguage() {
            return canDetect;
        }

        @Override
        public String translate(String text, String fromLang, String toLang)
            throws TranslationException, QueryLimitException {
            translateCalls++;
            lastFromLang = fromLang;
            lastToLang = toLang;
            if (rateLimited) throw new QueryLimitException(this);
            if (failure != null) throw failure;
            return result + "[" + toLang + "]";
        }

        @Override
        public String translate(String text, String toLang) throws TranslationException, QueryLimitException {
            translateCalls++;
            lastFromLang = null;
            lastToLang = toLang;
            if (rateLimited) throw new QueryLimitException(this);
            if (failure != null) throw failure;
            return result + "[" + toLang + "]";
        }

        @Override
        public String detectLangauge(String text) throws IOException, QueryLimitException, TranslationException {
            detectCalls++;
            if (rateLimited) throw new QueryLimitException(this);
            if (failure != null) throw failure;
            return detected;
        }
    }

    @Before
    public void resetManager() {
        TranslationManager.clearProviders();
        TranslationManager.clearCache();
    }

    @After
    public void restoreDefaults() {
        TranslationManager.clearProviders();
        TranslationManager.clearCache();
        TranslationManager.registerTranslationProvider(new YandexProvider());
    }

    // ----------------------------------------------------------------------------- provider selection

    @Test
    public void usesTheFirstProvider() throws Exception {
        FakeProvider first = new FakeProvider("first", true);
        TranslationManager.registerTranslationProvider(first);

        Assert.assertEquals("translated[de]", TranslationManager.translate("hello", "en", "de"));
        Assert.assertEquals(1, first.translateCalls);
        Assert.assertEquals("en", first.lastFromLang);
        Assert.assertEquals("de", first.lastToLang);
    }

    /** §1.8 - a rate-limited provider must fall through to the next one instead of throwing. */
    @Test
    public void fallsBackWhenTheFirstProviderIsRateLimited() throws Exception {
        FakeProvider exhausted = new FakeProvider("exhausted", true).rateLimited();
        FakeProvider backup = new FakeProvider("backup", true).returning("backup");
        TranslationManager.registerTranslationProvider(exhausted);
        TranslationManager.registerTranslationProvider(backup);

        Assert.assertEquals("backup[de]", TranslationManager.translate("hello", "en", "de"));
        Assert.assertEquals(1, exhausted.translateCalls);
        Assert.assertEquals(1, backup.translateCalls);
    }

    /** §1.8 / §3.12 - exhausting every provider must report cleanly, not recurse or index out of bounds. */
    @Test
    public void throwsWhenEveryProviderIsRateLimited() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("a", true).rateLimited());
        TranslationManager.registerTranslationProvider(new FakeProvider("b", true).rateLimited());

        try {
            TranslationManager.translate("hello", "en", "de");
            Assert.fail("expected NO_VALID_PROVIDERS");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NO_VALID_PROVIDERS, expected.errorType);
        }
    }

    @Test
    public void throwsWhenNoProviderIsRegistered() throws Exception {
        try {
            TranslationManager.translate("hello", "en", "de");
            Assert.fail("expected NO_VALID_PROVIDERS");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NO_VALID_PROVIDERS, expected.errorType);
        }
    }

    /** §1.8 - auto-detection must skip providers that cannot detect, rather than picking them and failing. */
    @Test
    public void autoDetectSkipsProvidersThatCannotDetect() throws Exception {
        FakeProvider noDetect = new FakeProvider("no-detect", false);
        FakeProvider detector = new FakeProvider("detector", true);
        TranslationManager.registerTranslationProvider(noDetect);
        TranslationManager.registerTranslationProvider(detector);

        Assert.assertEquals("translated[de]", TranslationManager.translate("hello", "de"));
        Assert.assertEquals("the non-detecting provider must not be called", 0, noDetect.translateCalls);
        Assert.assertEquals(1, detector.translateCalls);
        Assert.assertNull(detector.lastFromLang);
    }

    @Test
    public void autoDetectThrowsWhenNoProviderCanDetect() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("no-detect", false));

        try {
            TranslationManager.translate("hello", "de");
            Assert.fail("expected NO_VALID_PROVIDERS");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NO_VALID_PROVIDERS, expected.errorType);
        }
    }

    @Test
    public void autoDetectFallsBackWhenTheDetectorIsRateLimited() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("a", true).rateLimited());
        TranslationManager.registerTranslationProvider(new FakeProvider("b", true).returning("b"));

        Assert.assertEquals("b[de]", TranslationManager.translate("hello", "de"));
    }

    /** A request-level failure (text too long, bad key) must not be retried against every provider. */
    @Test
    public void requestLevelFailuresAreNotRetried() throws Exception {
        FakeProvider failing = new FakeProvider("failing", true).failing(TranslationException.ErrorTypes.TEXT_TOO_LONG);
        FakeProvider backup = new FakeProvider("backup", true);
        TranslationManager.registerTranslationProvider(failing);
        TranslationManager.registerTranslationProvider(backup);

        try {
            TranslationManager.translate("hello", "en", "de");
            Assert.fail("expected TEXT_TOO_LONG");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.TEXT_TOO_LONG, expected.errorType);
        }
        Assert.assertEquals(1, failing.translateCalls);
        Assert.assertEquals(0, backup.translateCalls);
    }

    @Test
    public void rejectsNullArguments() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("a", true));
        try {
            TranslationManager.translate(null, "de");
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
        try {
            TranslationManager.translate("hello", null, null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }

    // ---------------------------------------------------------------------------------------- cache

    /** §1.9 - the cache must include the target language in its key. */
    @Test
    public void cachesPerLanguage() throws Exception {
        FakeProvider provider = new FakeProvider("p", true);
        TranslationManager.registerTranslationProvider(provider);

        Assert.assertEquals("translated[de]", TranslationManager.translate("hello", "en", "de"));
        Assert.assertEquals("translated[fr]", TranslationManager.translate("hello", "en", "fr"));
        Assert.assertEquals("the cache must not collide across languages", 2, provider.translateCalls);
    }

    @Test
    public void cachesPerSourceLanguage() throws Exception {
        FakeProvider provider = new FakeProvider("p", true);
        TranslationManager.registerTranslationProvider(provider);

        TranslationManager.translate("hello", "en", "de");
        TranslationManager.translate("hello", "nl", "de");
        Assert.assertEquals(2, provider.translateCalls);
    }

    @Test
    public void autoDetectAndExplicitSourceDoNotShareACacheEntry() throws Exception {
        FakeProvider provider = new FakeProvider("p", true);
        TranslationManager.registerTranslationProvider(provider);

        TranslationManager.translate("hello", "de");
        TranslationManager.translate("hello", "en", "de");
        Assert.assertEquals(2, provider.translateCalls);
    }

    @Test
    public void repeatedRequestsHitTheCache() throws Exception {
        FakeProvider provider = new FakeProvider("p", true);
        TranslationManager.registerTranslationProvider(provider);

        Assert.assertEquals("translated[de]", TranslationManager.translate("hello", "en", "de"));
        Assert.assertEquals("translated[de]", TranslationManager.translate("hello", "en", "de"));
        Assert.assertEquals(1, provider.translateCalls);
        Assert.assertEquals(1, TranslationManager.getCacheSize());
    }

    /** §1.9 - the cache used to grow without bound. */
    @Test
    public void cacheIsBounded() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("p", true));

        int entries = TranslationManager.MAX_CACHE_ENTRIES + 25;
        for (int i = 0; i < entries; i++) {
            TranslationManager.translate("text-" + i, "en", "de");
        }

        Assert.assertEquals(TranslationManager.MAX_CACHE_ENTRIES, TranslationManager.getCacheSize());
    }

    @Test
    public void failedTranslationsAreNotCached() throws Exception {
        FakeProvider provider = new FakeProvider("p", true).rateLimited();
        TranslationManager.registerTranslationProvider(provider);

        try {
            TranslationManager.translate("hello", "en", "de");
        } catch (TranslationException expected) {
            // no provider left
        }
        Assert.assertEquals(0, TranslationManager.getCacheSize());
    }

    @Test
    public void clearCacheEmptiesTheCache() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("p", true));
        TranslationManager.translate("hello", "en", "de");
        Assert.assertEquals(1, TranslationManager.getCacheSize());

        TranslationManager.clearCache();
        Assert.assertEquals(0, TranslationManager.getCacheSize());
    }

    @Test
    public void resetRateLimitsReenablesAProvider() throws Exception {
        FakeProvider provider = new FakeProvider("p", true).rateLimited();
        TranslationManager.registerTranslationProvider(provider);

        try {
            TranslationManager.translate("hello", "en", "de");
            Assert.fail("expected NO_VALID_PROVIDERS");
        } catch (TranslationException expected) {
            // now disabled
        }

        TranslationManager.resetRateLimits();
        provider.rateLimited = false;
        Assert.assertEquals("translated[de]", TranslationManager.translate("hello", "en", "de"));
    }

    // ------------------------------------------------------------------------------- detectLanguage

    @Test
    public void detectLanguageUsesTheFirstDetectCapableProvider() throws Exception {
        FakeProvider noDetect = new FakeProvider("no-detect", false);
        FakeProvider detector = new FakeProvider("detector", true);
        TranslationManager.registerTranslationProvider(noDetect);
        TranslationManager.registerTranslationProvider(detector);

        Assert.assertEquals("en", TranslationManager.detectLanguage("hello"));
        Assert.assertEquals(0, noDetect.detectCalls);
        Assert.assertEquals(1, detector.detectCalls);
    }

    @Test
    public void detectLanguageFallsBackWhenRateLimited() throws Exception {
        FakeProvider exhausted = new FakeProvider("a", true).rateLimited();
        FakeProvider backup = new FakeProvider("b", true);
        TranslationManager.registerTranslationProvider(exhausted);
        TranslationManager.registerTranslationProvider(backup);

        Assert.assertEquals("en", TranslationManager.detectLanguage("hello"));
        Assert.assertEquals(1, backup.detectCalls);
    }

    @Test
    public void detectLanguageThrowsWhenNoProviderCanDetect() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("no-detect", false));

        try {
            TranslationManager.detectLanguage("hello");
            Assert.fail("expected NO_VALID_PROVIDERS");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NO_VALID_PROVIDERS, expected.errorType);
        }
    }

    // ---------------------------------------------------------------------------------- concurrency

    /**
     * §1.9 - the cache used to be a plain {@link java.util.HashMap} mutated from both the render thread and the
     * netty thread. This is a smoke test for the synchronised wrapper, not a proof of correctness.
     */
    @Test
    public void concurrentTranslationsAreSafe() throws Exception {
        TranslationManager.registerTranslationProvider(new FakeProvider("p", true));

        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final Set<String> results = java.util.Collections.synchronizedSet(new HashSet<String>());
        List<Thread> workers = new ArrayList<Thread>();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread worker = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < 50; i++) {
                            results.add(TranslationManager.translate("text-" + (i % 10), "en", "lang-" + (id % 3)));
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                }
            }, "translation-test-" + t);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        start.countDown();
        Assert.assertTrue("workers did not finish", done.await(30, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("concurrent translation failed", failure.get());

        // 10 distinct texts x 3 distinct target languages.
        Assert.assertEquals(30, TranslationManager.getCacheSize());
        // The fake provider only varies its output by target language.
        Assert.assertEquals(3, results.size());
        Assert.assertTrue(
            "cache exceeded its bound: " + TranslationManager.getCacheSize(),
            TranslationManager.getCacheSize() <= TranslationManager.MAX_CACHE_ENTRIES);
    }
}

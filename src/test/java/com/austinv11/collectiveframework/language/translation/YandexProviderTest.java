package com.austinv11.collectiveframework.language.translation;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * CODE_REVIEW §1.18 - the framework used to ship a hard-coded Yandex API key in source. That key is a leaked
 * credential, cannot be rotated by users, and the {@code yandexApiKey} config option that was supposed to
 * replace it was never read.
 * <p>
 * These tests cover the key plumbing only. Every path that would hit the network is guarded by
 * {@link YandexProvider#requireApiKey()}, so they never leave the JVM.
 */
public class YandexProviderTest {

    private final YandexProvider provider = new YandexProvider();

    @Before
    public void clearKey() {
        YandexProvider.setApiKey(null);
    }

    @After
    public void resetKey() {
        YandexProvider.setApiKey(null);
    }

    // ------------------------------------------------------------------------------- key plumbing

    @Test
    public void noKeyByDefault() {
        Assert.assertFalse(YandexProvider.hasApiKey());
        Assert.assertEquals("", YandexProvider.getApiKey());
    }

    @Test
    public void setApiKeyStoresAndTrimsTheKey() {
        YandexProvider.setApiKey("  trnsl.1.1.example  ");
        Assert.assertTrue(YandexProvider.hasApiKey());
        Assert.assertEquals("trnsl.1.1.example", YandexProvider.getApiKey());
    }

    @Test
    public void setApiKeyWithNullOrBlankDisablesTheProvider() {
        YandexProvider.setApiKey("something");
        YandexProvider.setApiKey(null);
        Assert.assertFalse(YandexProvider.hasApiKey());

        YandexProvider.setApiKey("   ");
        Assert.assertFalse(YandexProvider.hasApiKey());
    }

    // --------------------------------------------------------------------------- guarded entry points

    @Test
    public void translateWithoutAKeyFailsFast() throws Exception {
        try {
            provider.translate("hello", "de");
            Assert.fail("expected a TranslationException without an API key");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NOT_ENOUGH_PARAMETERS, expected.errorType);
        }
    }

    @Test
    public void translateWithExplicitLanguagesWithoutAKeyFailsFast() throws Exception {
        try {
            provider.translate("hello", "en", "de");
            Assert.fail("expected a TranslationException without an API key");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NOT_ENOUGH_PARAMETERS, expected.errorType);
        }
    }

    @Test
    public void detectLanguageWithoutAKeyFailsFast() throws Exception {
        try {
            provider.detectLangauge("hello");
            Assert.fail("expected a TranslationException without an API key");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NOT_ENOUGH_PARAMETERS, expected.errorType);
        }
    }

    @Test
    public void getLangsWithoutAKeyFailsFast() throws Exception {
        try {
            YandexProvider.getLangs();
            Assert.fail("expected a TranslationException without an API key");
        } catch (TranslationException expected) {
            Assert.assertEquals(TranslationException.ErrorTypes.NOT_ENOUGH_PARAMETERS, expected.errorType);
        }
    }

    // ----------------------------------------------------------------------------------- metadata

    @Test
    public void providerMetadata() {
        Assert.assertEquals("Yandex", provider.getProviderName());
        Assert.assertTrue(provider.canDetectLanguage());
    }

    /**
     * Guards against the credential coming back. The old constant was
     * {@code trnsl.1.1.20150228T025035Z.3525a3f37289aaaa...}; nothing in this class may contain a literal
     * Yandex key any more.
     */
    @Test
    public void noApiKeyIsCompiledIntoTheClass() throws Exception {
        java.lang.reflect.Field[] fields = YandexProvider.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            String value = (String) field.get(null);
            Assert.assertNotNull(value);
            Assert.assertFalse("a Yandex API key is hard-coded in " + field.getName(), value.startsWith("trnsl."));
        }
    }
}

package com.austinv11.collectiveframework.language.translation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.austinv11.collectiveframework.utils.StringUtils;
import com.austinv11.collectiveframework.utils.WebUtils;
import com.google.gson.Gson;

/**
 * Yandex.net translation provider.
 * <p>
 * The Yandex API requires a per-application key. It is deliberately not compiled into the framework: a
 * committed key is a leaked credential and cannot be rotated by users. Set it with {@link #setApiKey(String)}
 * before translating (the Minecraft layer wires it to the {@code yandexApiKey} config option).
 */
public class YandexProvider implements ITranslationProvider {

    private static final String TRANSLATE_URL = "https://translate.yandex.net/api/v1.5/tr.json/translate";
    private static final String DETECT_URL = "https://translate.yandex.net/api/v1.5/tr.json/detect";
    private static final String LANGS_URL = "https://translate.yandex.net/api/v1.5/tr.json/getLangs";

    private static volatile String apiKey = "";

    /**
     * Sets the Yandex API key used for all requests. Passing {@code null} or a blank string disables this
     * provider: {@link #translate} and {@link #detectLangauge} then fail with
     * {@link TranslationException.ErrorTypes#NOT_ENOUGH_PARAMETERS} instead of sending an unauthenticated
     * request.
     *
     * @param key The API key, may be null
     */
    public static void setApiKey(String key) {
        apiKey = key == null ? "" : key.trim();
    }

    /**
     * @return The currently configured API key, or an empty string if none is set
     */
    public static String getApiKey() {
        return apiKey;
    }

    /**
     * @return True if an API key has been configured
     */
    public static boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    private static void requireApiKey() throws TranslationException {
        if (!hasApiKey()) throw new TranslationException(TranslationException.ErrorTypes.NOT_ENOUGH_PARAMETERS);
    }

    private static String encode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }

    @Override
    public String getProviderName() {
        return "Yandex";
    }

    @Override
    public String translate(String text, String fromLang, String toLang)
        throws TranslationException, QueryLimitException, IOException {
        return translate(text, fromLang + "-" + toLang);
    }

    @Override
    public String translate(String text, String toLang) throws TranslationException, QueryLimitException, IOException {
        requireApiKey();
        // The text must be URL encoded, not just space-replaced: '&', '#', '%', '+' and every non-ASCII
        // character would otherwise truncate or corrupt the request (CODE_REVIEW §3.11).
        JSONTranslateResponse response = new Gson().fromJson(
            StringUtils.stringFromList(
                WebUtils.readURL(
                    TRANSLATE_URL + "?key=" + encode(apiKey) + "&lang=" + encode(toLang) + "&text=" + encode(text))),
            JSONTranslateResponse.class);
        if (response == null) throw new TranslationException(TranslationException.ErrorTypes.UNABLE_TO_TRANSLATE);

        switch (response.code) {
            case 200:
                break;
            case 401:
            case 402:
            case 403:
            case 404:
                throw new QueryLimitException(this);
            case 413:
                throw new TranslationException(TranslationException.ErrorTypes.TEXT_TOO_LONG);
            case 422:
                throw new TranslationException(TranslationException.ErrorTypes.UNABLE_TO_TRANSLATE);
            case 501:
                throw new TranslationException(TranslationException.ErrorTypes.LANGUAGE_NOT_SUPPORTED);
            default:
                throw new TranslationException(TranslationException.ErrorTypes.UNABLE_TO_TRANSLATE);
        }

        if (response.text == null) throw new TranslationException(TranslationException.ErrorTypes.UNABLE_TO_TRANSLATE);
        StringBuilder translated = new StringBuilder();
        for (String s : response.text) translated.append(s);
        return translated.toString();
    }

    @Override
    public boolean canDetectLanguage() {
        return true;
    }

    @Override
    public String detectLangauge(String text) throws IOException, QueryLimitException, TranslationException {
        requireApiKey();
        JSONDetectResponse response = new Gson().fromJson(
            StringUtils.stringFromList(
                // The text parameter used to be ignored entirely and "Hello+world" was always sent.
                WebUtils.readURL(DETECT_URL + "?key=" + encode(apiKey) + "&text=" + encode(text))),
            JSONDetectResponse.class);
        if (response == null) throw new TranslationException(TranslationException.ErrorTypes.UNABLE_TO_TRANSLATE);
        if (response.code == 401 || response.code == 402 || response.code == 403 || response.code == 404)
            throw new QueryLimitException(this);
        if (response.code != 200) throw new TranslationException(TranslationException.ErrorTypes.UNABLE_TO_TRANSLATE);
        return response.lang;
    }

    /**
     * Method to get the available translations
     *
     * @return Possible language combinations
     */
    public static String[] getLangs() throws IOException, TranslationException {
        requireApiKey();
        JSONLangResponse response = new Gson().fromJson(
            StringUtils.stringFromList(WebUtils.readURL(LANGS_URL + "?key=" + encode(apiKey) + "&ui=us")),
            JSONLangResponse.class);
        return response == null || response.dirs == null ? new String[0] : response.dirs;
    }

    public static class JSONLangResponse {

        public String[] dirs;
    }

    public static class JSONDetectResponse {

        public int code;
        public String lang;
    }

    public static class JSONTranslateResponse {

        public int code;
        public String lang;
        public String[] text;
    }
}

package com.austinv11.collectiveframework.minecraft.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §3.10 - {@code mcLangCodesToUsable} split the code on {@code "_"} and read {@code langInfo[1]},
 * so any code without an underscore threw {@code ArrayIndexOutOfBoundsException}. The {@link java.util.Locale}
 * it built was only ever used to lower-case the language part.
 */
public class MinecraftTranslatorTest {

    @Test
    public void lowercasesAStandardCode() {
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable("en_US"));
        Assert.assertEquals("pt", MinecraftTranslator.mcLangCodesToUsable("pt_BR"));
        Assert.assertEquals("zh", MinecraftTranslator.mcLangCodesToUsable("zh_CN"));
    }

    /** §3.10 - this used to throw {@code ArrayIndexOutOfBoundsException}. */
    @Test
    public void acceptsACodeWithoutARegion() {
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable("en"));
        Assert.assertEquals("de", MinecraftTranslator.mcLangCodesToUsable("de"));
    }

    @Test
    public void lowercasesAnUppercaseCode() {
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable("EN"));
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable("EN_US"));
    }

    @Test
    public void fallsBackToEnglishForNullAndEmpty() {
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable(null));
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable(""));
        Assert.assertEquals("en", MinecraftTranslator.mcLangCodesToUsable("_US"));
    }

    @Test
    public void handlesATrailingUnderscore() {
        Assert.assertEquals("fr", MinecraftTranslator.mcLangCodesToUsable("fr_"));
    }

    @Test
    public void handlesMultipleUnderscores() {
        Assert.assertEquals("sr", MinecraftTranslator.mcLangCodesToUsable("sr_SP_extra"));
    }
}

package com.austinv11.collectiveframework.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ReflectionUtilsTest {

    @SuppressWarnings("unused")
    private static class Sample {

        private int declared = 1;
        public String normal = "x";
    }

    private static class Annotated {

        @Deprecated
        private int first;

        @Deprecated
        public int second;

        private int unannotated;

        @Deprecated
        public void annotatedMethod() {}
    }

    @Test
    public void findsDeclaredAndInheritedFields() {
        Field declared = ReflectionUtils.getDeclaredOrNormalField("declared", Sample.class);
        Assert.assertNotNull(declared);
        Assert.assertEquals("declared", declared.getName());

        Field normal = ReflectionUtils.getDeclaredOrNormalField("normal", Sample.class);
        Assert.assertNotNull(normal);
        Assert.assertEquals("normal", normal.getName());

        Assert.assertNull(ReflectionUtils.getDeclaredOrNormalField("missing", Sample.class));
    }

    @Test
    public void findsAnnotatedFields() {
        List<Field> declared = ReflectionUtils.getDeclaredFieldsWithAnnotation(Deprecated.class, Annotated.class);
        Assert.assertEquals(2, declared.size());

        List<Field> visible = ReflectionUtils.getFieldsWithAnnotation(Deprecated.class, Annotated.class);
        Assert.assertEquals(1, visible.size());
        Assert.assertEquals(
            "second",
            visible.get(0)
                .getName());
    }

    @Test
    public void findsAnnotatedMethods() {
        Assert.assertEquals(
            1,
            ReflectionUtils.getDeclaredMethodsWithAnnotation(Deprecated.class, Annotated.class)
                .size());
        Assert.assertEquals(
            1,
            ReflectionUtils.getMethodsWithAnnotation(Deprecated.class, Annotated.class)
                .size());
    }

    @Test
    public void findsMethodsByName() {
        Assert.assertNotNull(ReflectionUtils.getDeclaredOrNormalMethod("annotatedMethod", Annotated.class));
        Assert.assertNull(ReflectionUtils.getDeclaredOrNormalMethod("nope", Annotated.class));
    }

    @Test
    public void paramsMatchComparesArityAndTypes() throws Exception {
        Method method = String.class.getMethod("substring", int.class, int.class);
        Assert.assertTrue(ReflectionUtils.paramsMatch(method, int.class, int.class));
        Assert.assertFalse(ReflectionUtils.paramsMatch(method, int.class));
        Assert.assertFalse(ReflectionUtils.paramsMatch(method, int.class, long.class));
    }

    @Test
    public void getPrimitiveSafeClassForNameHandlesPrimitives() throws Exception {
        Assert.assertEquals(int.class, ReflectionUtils.getPrimitiveSafeClassForName("int"));
        Assert.assertEquals(boolean.class, ReflectionUtils.getPrimitiveSafeClassForName("boolean"));
        Assert.assertEquals(void.class, ReflectionUtils.getPrimitiveSafeClassForName("void"));
        Assert.assertEquals(String.class, ReflectionUtils.getPrimitiveSafeClassForName("java.lang.String"));
    }

    @Test
    public void isNamePrimitiveOrPrimitiveObjectRecognisesWrappers() {
        Assert.assertTrue(ReflectionUtils.isNamePrimitiveOrPrimitiveObject("int"));
        Assert.assertTrue(ReflectionUtils.isNamePrimitiveOrPrimitiveObject("Integer"));
        Assert.assertTrue(ReflectionUtils.isNamePrimitiveOrPrimitiveObject("character"));
        Assert.assertFalse(ReflectionUtils.isNamePrimitiveOrPrimitiveObject("String"));
    }

    @Test
    public void isPrimitiveObjectRecognisesBoxedValues() {
        Assert.assertTrue(ReflectionUtils.isPrimitiveObject(Integer.valueOf(1)));
        Assert.assertTrue(ReflectionUtils.isPrimitiveObject(Character.valueOf('a')));
        Assert.assertFalse(ReflectionUtils.isPrimitiveObject("string"));
    }

    @Test
    public void coerceStringToJavaObjectInfersAType() {
        Assert.assertEquals(Integer.valueOf(42), ReflectionUtils.coerceStringToJavaObject("42"));
        Assert.assertEquals(Double.valueOf(1.5), ReflectionUtils.coerceStringToJavaObject("1.5"));
        Assert.assertEquals(Boolean.TRUE, ReflectionUtils.coerceStringToJavaObject("true"));
        Assert.assertEquals(Character.valueOf('x'), ReflectionUtils.coerceStringToJavaObject("x"));
        Assert.assertEquals("hello", ReflectionUtils.coerceStringToJavaObject("hello"));
        Assert.assertNull(ReflectionUtils.coerceStringToJavaObject("void"));
    }

    @Test
    public void coerceStringToArraySplitsOnCommas() {
        Assert.assertArrayEquals(
            new Object[] { Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3) },
            ReflectionUtils.coerceStringToArray("{1, 2, 3}"));
    }

    @Test
    public void getSignatureFromObjectUsesJvmDescriptors() {
        Assert.assertEquals("I", ReflectionUtils.getSignatureFromObject(Integer.valueOf(1)));
        Assert.assertEquals("J", ReflectionUtils.getSignatureFromObject(Long.valueOf(1)));
        Assert.assertEquals("F", ReflectionUtils.getSignatureFromObject(Float.valueOf(1)));
        Assert.assertEquals("D", ReflectionUtils.getSignatureFromObject(Double.valueOf(1)));
        Assert.assertEquals("Z", ReflectionUtils.getSignatureFromObject(Boolean.TRUE));
        Assert.assertEquals("C", ReflectionUtils.getSignatureFromObject(Character.valueOf('a')));
        Assert.assertEquals("B", ReflectionUtils.getSignatureFromObject(Byte.valueOf((byte) 1)));
        Assert.assertEquals("S", ReflectionUtils.getSignatureFromObject(Short.valueOf((short) 1)));
        Assert.assertEquals("Ljava/lang/String;", ReflectionUtils.getSignatureFromObject("s"));
        Assert.assertEquals("[I", ReflectionUtils.getSignatureFromObject(new int[0]));
        Assert.assertEquals("[Ljava/lang/String;", ReflectionUtils.getSignatureFromObject(new String[0]));
    }

    @Test
    public void getTypeFromSignatureHandlesPrimitivesAndObjects() throws Exception {
        Assert.assertEquals(int.class, ReflectionUtils.getTypeFromSignature("I"));
        Assert.assertEquals(void.class, ReflectionUtils.getTypeFromSignature("V"));
        Assert.assertEquals(int[].class, ReflectionUtils.getTypeFromSignature("[I"));
        Assert.assertEquals(String.class, ReflectionUtils.getTypeFromSignature("Ljava/lang/String;"));
    }

    @Test
    public void classImplementsInterfaceChecksAssignability() {
        Assert.assertTrue(ReflectionUtils.classImplementsInterface(String.class, CharSequence.class));
        Assert.assertFalse(ReflectionUtils.classImplementsInterface(String.class, Runnable.class));
        // The first argument must be a class, the second an interface.
        Assert.assertFalse(ReflectionUtils.classImplementsInterface(CharSequence.class, String.class));
    }

    /**
     * CODE_REVIEW §2.6 - {@code getTypeFromSignature("[Ljava/lang/String;")} strips the leading {@code [}
     * but keeps the trailing {@code ;}, producing {@code java.lang.String;} which cannot be loaded.
     */
    @Test
    public void getTypeFromSignatureShouldHandleObjectArrays() throws Exception {
        Assert.assertEquals(String[].class, ReflectionUtils.getTypeFromSignature("[Ljava/lang/String;"));
    }

    /**
     * CODE_REVIEW §2.6 - {@code objectToString} writes the *enclosing* object's signature for every field
     * (and emits public fields twice, since it walks both {@code getDeclaredFields()} and
     * {@code getFields()}), so the output cannot be read back by {@code objectFromString}.
     */
    @Test
    public void objectToStringShouldTagEachFieldWithItsOwnType() {
        String serialised = ReflectionUtils.objectToString(new Sample());
        Assert.assertTrue(serialised, serialised.contains("I:declared="));
        Assert.assertTrue(serialised, serialised.contains("Ljava/lang/String;:normal="));
    }

    /**
     * CODE_REVIEW §2.6 - {@code objectFromString} immediately throws
     * {@link StringIndexOutOfBoundsException} on the leading {@code \{} line, because it looks for a
     * {@code :} that is not there.
     */
    @Test
    public void objectRoundTripPreservesFieldValues() throws Exception {
        String serialised = ReflectionUtils.objectToString(new Sample());
        Sample restored = (Sample) ReflectionUtils.objectFromString(serialised, new Sample());
        Assert.assertEquals(1, restored.declared);
        Assert.assertEquals("x", restored.normal);
    }

    /** A public field declared on the class used to be emitted twice, once per {@code getFields} walk. */
    @Test
    public void objectToStringEmitsEachFieldOnce() {
        String serialised = ReflectionUtils.objectToString(new Sample());
        int first = serialised.indexOf(":normal=");
        Assert.assertTrue("field missing from output: " + serialised, first >= 0);
        Assert.assertEquals(
            "public fields must not be emitted twice: " + serialised,
            -1,
            serialised.indexOf(":normal=", first + 1));
    }

    /** §2.7 - a one-character String used to come back as a Character. */
    @Test
    public void objectRoundTripKeepsAOneCharacterStringAString() throws Exception {
        Sample original = new Sample();
        original.normal = "y";

        Sample restored = (Sample) ReflectionUtils
            .objectFromString(ReflectionUtils.objectToString(original), new Sample());

        Assert.assertEquals("y", restored.normal);
    }

    @Test
    public void objectFromStringRejectsANonLiteral() throws Exception {
        try {
            ReflectionUtils.objectFromString("not an object", new Sample());
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }

    @Test
    public void objectFromStringToleratesAnUnknownField() throws Exception {
        Sample restored = (Sample) ReflectionUtils
            .objectFromString("{\n\tI:missing=1\n\tI:declared=7\n}", new Sample());
        Assert.assertEquals(7, restored.declared);
    }

    @Test
    public void coerceStringToArrayHandlesStrings() throws Exception {
        Object[] array = ReflectionUtils.coerceStringToArray("[a,b]", "");
        Assert.assertArrayEquals(new Object[] { "a", "b" }, array);
    }

    @Test
    public void coerceStringToArrayHandlesAnEmptyArray() throws Exception {
        Assert.assertEquals(0, ReflectionUtils.coerceStringToArray("[]", "").length);
    }
}

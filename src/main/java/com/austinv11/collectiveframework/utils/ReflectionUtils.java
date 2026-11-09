package com.austinv11.collectiveframework.utils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.austinv11.collectiveframework.utils.math.MathUtils;

/**
 * Class that contains a few useful utilities to simplify reflection
 */
public class ReflectionUtils {

    /**
     * Gets a list of declared methods with the given annotation
     *
     * @param annotation The annotation to search for
     * @param clazz      The class to search in
     * @return The methods with the annotation
     */
    public static List<Method> getDeclaredMethodsWithAnnotation(Class<? extends Annotation> annotation, Class clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        List<Method> annotated = new ArrayList<Method>();
        for (Method m : methods) if (m.isAnnotationPresent(annotation)) annotated.add(m);
        return annotated;
    }

    /**
     * Gets a list of declared fields with the given annotation
     *
     * @param annotation The annotation to search for
     * @param clazz      The class to search in
     * @return The fields with the annotation
     */
    public static List<Field> getDeclaredFieldsWithAnnotation(Class<? extends Annotation> annotation, Class clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<Field> annotated = new ArrayList<Field>();
        for (Field f : fields) if (f.isAnnotationPresent(annotation)) annotated.add(f);
        return annotated;
    }

    /**
     * Gets a list of methods with the given annotation
     *
     * @param annotation The annotation to search for
     * @param clazz      The class to search in
     * @return The methods with the annotation
     */
    public static List<Method> getMethodsWithAnnotation(Class<? extends Annotation> annotation, Class clazz) {
        Method[] methods = clazz.getMethods();
        List<Method> annotated = new ArrayList<Method>();
        for (Method m : methods) if (m.isAnnotationPresent(annotation)) annotated.add(m);
        return annotated;
    }

    /**
     * Gets a list of fields with the given annotation
     *
     * @param annotation The annotation to search for
     * @param clazz      The class to search in
     * @return The fields with the annotation
     */
    public static List<Field> getFieldsWithAnnotation(Class<? extends Annotation> annotation, Class clazz) {
        Field[] fields = clazz.getFields();
        List<Field> annotated = new ArrayList<Field>();
        for (Field f : fields) if (f.isAnnotationPresent(annotation)) annotated.add(f);
        return annotated;
    }

    /**
     * Checks if the given method has the required parameters
     *
     * @param method The method
     * @param params The parameters (ORDER MATTERS!)
     * @return True of the parameters match, else false
     */
    public static boolean paramsMatch(Method method, Class<?>... params) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length == params.length) {
            for (int i = 0; i < parameters.length; i++) if (!parameters[i].getName()
                .equals(params[i].getName())) return false;
            return true;
        }
        return false;
    }

    /**
     * Attempts to find either a declared or normal field (in that order)
     *
     * @param fieldName The field to get
     * @param clazz     The class to search
     * @return The field, or null if it wasn't found
     */
    public static Field getDeclaredOrNormalField(String fieldName, Class clazz) {
        for (Field f : clazz.getDeclaredFields()) if (f.getName()
            .equals(fieldName)) return f;
        for (Field f1 : clazz.getFields()) if (f1.getName()
            .equals(fieldName)) return f1;
        return null;
    }

    /**
     * Attempts to find either a declared or normal method (in that order)
     *
     * @param methodName The method to get
     * @param clazz      The class to search
     * @return The field, or null if it wasn't found
     */
    public static Method getDeclaredOrNormalMethod(String methodName, Class clazz) {
        for (Method m : clazz.getDeclaredMethods()) if (m.getName()
            .equals(methodName)) return m;
        for (Method m1 : clazz.getMethods()) if (m1.getName()
            .equals(methodName)) return m1;
        return null;
    }

    /**
     * Gets the jvm signature for an object
     *
     * @param o The object
     * @return The signature
     */
    public static String getSignatureFromObject(Object o) {
        if (o.getClass()
            .isArray())
            return o.getClass()
                .getName()
                .replace(".", "/");
        if (o instanceof Integer) return "I";
        if (o instanceof Long) return "J";
        if (o instanceof Double) return "D";
        if (o instanceof Float) return "F";
        if (o instanceof Boolean) return "Z";
        if (!(o instanceof String) && o instanceof Character) return "C";
        if (o instanceof Byte) return "B";
        if (o instanceof Void) return "V";
        if (o instanceof Short) return "S";
        return "L" + o.getClass()
            .getName()
            .replace(".", "/") + ";";
    }

    /**
     * Gets the class for a signature (only handles one signature at a time)
     *
     * @param signature The signature
     * @return The class representing the signature inputted
     * @throws ClassNotFoundException
     */
    public static Class getTypeFromSignature(String signature) throws ClassNotFoundException {
        if (signature == null || signature.isEmpty()) return null;
        if (signature.charAt(0) == '[') {
            signature = signature.replace("[", "");
            if (signature.equals("I")) return int[].class;
            if (signature.equals("J")) return long[].class;
            if (signature.equals("D")) return double[].class;
            if (signature.equals("F")) return float[].class;
            if (signature.equals("Z")) return boolean[].class;
            if (signature.equals("C")) return char[].class;
            if (signature.equals("B")) return byte[].class;
            if (signature.equals("S")) return short[].class;
            // The trailing ';' was kept, so this asked for a class called "java.lang.String;"
            // (CODE_REVIEW §2.6).
            if (signature.contains("L")) return Array
                .newInstance(
                    Class.forName(
                        signature.substring(1, signature.length() - 1)
                            .replace("/", ".")),
                    1)
                .getClass();
        } else {
            if (signature.equals("I")) return int.class;
            if (signature.equals("J")) return long.class;
            if (signature.equals("D")) return double.class;
            if (signature.equals("F")) return float.class;
            if (signature.equals("Z")) return boolean.class;
            if (signature.equals("C")) return char.class;
            if (signature.equals("B")) return byte.class;
            if (signature.equals("V")) return void.class;
            if (signature.equals("S")) return short.class;
            if (signature.contains("L")) return Class.forName(
                signature.substring(1, signature.length() - 1)
                    .replace("/", "."));
        }
        return null;
    }

    /**
     * Gets classes for a given name, including primitives
     *
     * @param name The name
     * @return The class
     * @throws ClassNotFoundException
     */
    public static Class getPrimitiveSafeClassForName(String name) throws ClassNotFoundException {
        if (name.equalsIgnoreCase("int")) return Integer.TYPE;
        if (name.equalsIgnoreCase("long")) return Long.TYPE;
        if (name.equalsIgnoreCase("double")) return Double.TYPE;
        if (name.equalsIgnoreCase("float")) return Float.TYPE;
        if (name.equalsIgnoreCase("boolean")) return Boolean.TYPE;
        if (name.equalsIgnoreCase("char")) return Character.TYPE;
        if (name.equalsIgnoreCase("byte")) return Byte.TYPE;
        if (name.equalsIgnoreCase("void")) return Void.TYPE;
        if (name.equalsIgnoreCase("short")) return Short.TYPE;
        return Class.forName(name);
    }

    /**
     * This method checks if the specified class name is of a primitive.
     *
     * @param name The class name
     * @return True if the name is of a primitive data type
     * @deprecated Character-for-character identical to {@link #isNamePrimitiveOrPrimitiveObject(String)}, which
     *             is the one the framework uses. Kept as a delegating alias (CODE_REVIEW §4.1).
     */
    @Deprecated
    public static boolean isNamePrimitive(String name) {
        return isNamePrimitiveOrPrimitiveObject(name);
    }

    /**
     * This method checks if the specified class name is of a primitive, includes primitive object names
     *
     * @param name The class name
     * @return True if the name is of a primitive data type
     */
    public static boolean isNamePrimitiveOrPrimitiveObject(String name) {
        return name.equalsIgnoreCase("int") || name.equalsIgnoreCase("long")
            || name.equalsIgnoreCase("double")
            || name.equalsIgnoreCase("float")
            || name.equalsIgnoreCase("boolean")
            || name.equalsIgnoreCase("char")
            || name.equalsIgnoreCase("byte")
            || name.equalsIgnoreCase("void")
            || name.equalsIgnoreCase("short")
            || name.equalsIgnoreCase("integer")
            || name.equalsIgnoreCase("character");
    }

    /**
     * Checks if the given object is a primitive wrapper object
     *
     * @param o The object
     * @return True of the object represents a primitive wrapper object
     */
    public static boolean isPrimitiveObject(Object o) {
        return o instanceof Integer || o instanceof Long
            || o instanceof Double
            || o instanceof Float
            || o instanceof Boolean
            || o instanceof Character
            || o instanceof Byte
            || o instanceof Void
            || o instanceof Short;
    }

    /**
     * This attempts to coerce a string into a standard java object
     *
     * @param containingString The string to coerce
     * @return The object, either a {@link Double}, {@link Integer}, {@link Character},
     *         {@link Boolean}, null, or {@link String}
     */
    public static Object coerceStringToJavaObject(String containingString) {
        if (MathUtils.isStringNumber(containingString)) {
            if (containingString.contains(".")) return Double.parseDouble(containingString);
            else return Integer.parseInt(containingString);
        }
        if (containingString.length() == 1) return containingString.charAt(0);
        if (containingString.equals("false") || containingString.equals("true"))
            return Boolean.parseBoolean(containingString);
        if (containingString.equals("void")) return null;
        return containingString;
    }

    /**
     * Creates an array from a string
     *
     * @param containingString The string
     * @return The array
     */
    public static Object[] coerceStringToArray(String containingString) {
        containingString = containingString.replace("{", "")
            .replace("}", "")
            .trim();
        String[] objects = containingString.split(",");
        Object[] array = new Object[objects.length];
        for (int i = 0; i < objects.length; i++) array[i] = coerceStringToJavaObject(objects[i].trim());
        return array;
    }

    /**
     * Creates an array from a string
     *
     * @param containingString The string
     * @param toObject         The type for the array
     * @return The array
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws IOException
     */
    public static Object[] coerceStringToArray(String containingString, Object toObject)
        throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        containingString = containingString.trim();
        if (containingString.startsWith("[")) containingString = containingString.substring(1);
        if (containingString.endsWith("]"))
            containingString = containingString.substring(0, containingString.length() - 1);
        if (containingString.trim()
            .isEmpty()) return new Object[0];

        // The prototype is used as-is when it is a String or a wrapper, rather than being re-instantiated:
        // re-instantiating a String produced "" and lost the element entirely (CODE_REVIEW §2.6).
        Object prototype = toObject instanceof String || isPrimitiveObject(toObject) ? toObject
            : toObject.getClass()
                .newInstance(); // FIXME: Don't use newInstance()
        String[] objects = containingString.split(",");
        Object[] array = new Object[objects.length];
        for (int i = 0; i < objects.length; i++) array[i] = objectFromString(objects[i].trim(), prototype);
        return array;
    }

    /**
     * Tries to create an object from a string
     *
     * @param objectString The string representing the object
     * @param object       The object to deserialize the string into
     * @return The object
     * @throws IOException
     */
    public static Object objectFromString(String objectString, Object object)
        throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        if (object == null) if (objectString.contains("[") && objectString.endsWith("]")) {
            return coerceStringToArray(objectString);
        } else {
            return coerceStringToJavaObject(objectString);
        }
        if (object instanceof ISerializable) return ((ISerializable) object).deserialize(objectString);
        if (object.getClass()
            .isArray()) return coerceStringToArray(objectString, object);
        // A String or a primitive wrapper "deserialises" to the text itself; this is what makes
        // coerceStringToArray work for a String[] (CODE_REVIEW §2.6).
        if (object instanceof String) return objectString;
        if (isPrimitiveObject(object)) return coerceStringToJavaObject(objectString.trim());

        String text = objectString.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new IllegalArgumentException("Not an object literal: " + objectString);
        }

        int end = text.length() - 1; // index of the closing brace
        int index = 1; // past the opening brace
        while (true) {
            index = skipWhitespace(text, index);
            if (index >= end) break;

            int colon = text.indexOf(':', index);
            int equals = text.indexOf('=', index);
            if (colon < 0 || equals < 0 || colon > equals || equals >= end) {
                throw new IllegalArgumentException("Malformed field at offset " + index + " in " + objectString);
            }
            String signature = text.substring(index, colon)
                .trim();
            String fieldName = text.substring(colon + 1, equals)
                .trim();
            int valueEnd = valueEnd(text, equals + 1, signature, end);
            String valueText = text.substring(equals + 1, valueEnd);

            Field field = getDeclaredOrNormalField(fieldName, object.getClass());
            if (field != null) {
                field.setAccessible(true);
                field.set(object, valueFromText(signature, valueText));
            }
            index = valueEnd;
        }
        return object;
    }

    private static int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        return index;
    }

    /**
     * Finds where a field value ends.
     *
     * @param text      The whole literal
     * @param start     The first character of the value
     * @param signature The value's type signature
     * @param limit     The index of the enclosing closing brace
     * @return The index just past the value
     */
    private static int valueEnd(String text, int start, String signature, int limit) {
        if (signature.startsWith("[")) return matchingEnd(text, start, '[', ']', limit);
        if (signature.startsWith("L")) {
            int first = skipWhitespace(text, start);
            if (first < limit && text.charAt(first) == '{') return matchingEnd(text, first, '{', '}', limit);
        }
        int newline = text.indexOf('\n', start);
        return newline < 0 || newline > limit ? limit : newline;
    }

    private static int matchingEnd(String text, int start, char open, char close, int limit) {
        int depth = 0;
        for (int i = start; i < limit; i++) {
            char c = text.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return limit;
    }

    /**
     * Builds a value from its signature and text form.
     *
     * @param signature The type signature
     * @param text      The text form
     * @return The value
     * @throws ClassNotFoundException if the signature names an unknown class
     */
    private static Object valueFromText(String signature, String text)
        throws ClassNotFoundException, IOException, IllegalAccessException, InstantiationException {
        Class<?> type = getTypeFromSignature(signature);
        if (type == null) throw new IllegalArgumentException("Unknown type signature '" + signature + "'");
        if (type.isArray()) {
            return coerceStringToArray(text.trim(), Array.newInstance(type.getComponentType(), 0));
        }
        if (type.isPrimitive() || type.isAssignableFrom(String.class) || isPrimitiveObjectType(type)) {
            return coerceForType(type, text);
        }
        return objectFromString(text, type.newInstance()); // FIXME: Don't use newInstance()
    }

    private static boolean isPrimitiveObjectType(Class<?> type) {
        return type == Integer.class || type == Long.class
            || type == Double.class
            || type == Float.class
            || type == Boolean.class
            || type == Character.class
            || type == Byte.class
            || type == Short.class;
    }

    /**
     * Coerces text to a known type.
     * <p>
     * {@link #coerceStringToJavaObject(String)} has to guess the type, so it turns {@code "x"} into a
     * {@link Character} and {@code "5"} into an {@link Integer} regardless of where the value is going. When the
     * destination type is known - which it is when reading a serialized field - guessing produced values the
     * field could not accept (CODE_REVIEW §2.7).
     *
     * @param type The destination type
     * @param text The text form
     * @return The value
     */
    private static Object coerceForType(Class<?> type, String text) {
        String trimmed = text.trim();
        if (type == String.class || type.isAssignableFrom(String.class)) return text;
        if (type == int.class || type == Integer.class) return Integer.valueOf(trimmed);
        if (type == long.class || type == Long.class) return Long.valueOf(trimmed);
        if (type == double.class || type == Double.class) return Double.valueOf(trimmed);
        if (type == float.class || type == Float.class) return Float.valueOf(trimmed);
        if (type == boolean.class || type == Boolean.class) return Boolean.valueOf(trimmed);
        if (type == char.class || type == Character.class) return trimmed.isEmpty() ? '\0' : trimmed.charAt(0);
        if (type == byte.class || type == Byte.class) return Byte.valueOf(trimmed);
        if (type == short.class || type == Short.class) return Short.valueOf(trimmed);
        return coerceStringToJavaObject(trimmed);
    }

    /**
     * Translates an object into a string readable by {@link #objectFromString}
     *
     * @param o The object
     * @return The String
     */
    public static String objectToString(Object o) {
        return objectToString(o, 0);
    }

    /**
     * Translates an object into a string readable by {@link #objectFromString}
     *
     * @param o    The object
     * @param tabs The number of tab indents
     * @return The string
     */
    public static String objectToString(Object o, int tabs) {
        if (o.getClass()
            .isArray()) {
            String array = "[";
            for (int i = 0; i < Array.getLength(o); i++) {
                array = array + "," + objectToString(Array.get(o, i), tabs);
            }
            array = array.replaceFirst(",", "");
            return array + "]";
        }
        if (o instanceof ISerializable) return ((ISerializable) o).serialize();
        if (isPrimitiveObject(o) || o instanceof String) return o.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        java.util.Set<String> written = new java.util.HashSet<String>();
        // Walks the declared fields and then the visible ones. A public field declared on the class itself
        // appears in both, so it used to be emitted twice; `written` keeps it to one line.
        for (Field f : allFields(o.getClass())) {
            if (!written.add(f.getName())) continue;
            f.setAccessible(true);
            try {
                Object value = f.get(o);
                sb.append(StringUtils.repeatString("\t", tabs + 1));
                // Tagged with the *field's* type, not the enclosing object's. Every field used to carry the
                // parent's signature, so nothing could be read back (CODE_REVIEW §2.6).
                sb.append(getSignatureFromObject(value));
                sb.append(":");
                sb.append(f.getName());
                sb.append("=");
                sb.append(objectToString(value, tabs + 1));
                sb.append("\n");
            } catch (IllegalAccessException e) {
                e.printStackTrace();// This should never be reached
            }
        }
        sb.append(StringUtils.repeatString("\t", tabs));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Every field of the class, declared ones first and then the visible inherited ones.
     *
     * @param type The class
     * @return The fields
     */
    private static java.util.List<Field> allFields(Class<?> type) {
        java.util.List<Field> fields = new java.util.ArrayList<Field>();
        for (Field f : type.getDeclaredFields()) fields.add(f);
        for (Field f : type.getFields()) fields.add(f);
        return fields;
    }

    /**
     * Checks if a class implements another class
     *
     * @param classToExamine The class to check
     * @param interfaceClass The interface to look for
     * @return True if the class implements the provided interface
     */
    public static boolean classImplementsInterface(Class classToExamine, Class interfaceClass) {
        return interfaceClass.isInterface() && interfaceClass.isAssignableFrom(classToExamine);
    }
}

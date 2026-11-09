package com.austinv11.collectiveframework.minecraft.config;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.common.config.Property;

import com.austinv11.collectiveframework.utils.ReflectionUtils;

/**
 * A default config proxy for primitive (also includes strings) and array types.
 * <p>
 * Values are written with a small backslash escape so that a {@code String} containing a newline, a
 * backslash or (inside an array) a comma survives a round trip. Previously the value was written verbatim and
 * arrays were joined with unescaped commas, so {@code ["a,b", "c"]} came back as three elements and an empty
 * array came back as one empty element (CODE_REVIEW §2.9).
 * <p>
 * A malformed value now throws {@link ConfigException} instead of being returned as the raw string for the
 * caller to trip over (CODE_REVIEW §2.8).
 */
public class DefaultProxy implements IConfigProxy {

    private static final char ESCAPE = '\\';
    private static final String ARRAY_SUFFIX = " Array";

    @Override
    public boolean canSerializeObject(Object o) {
        if (o == null) return false;
        return o.getClass()
            .isArray() || ReflectionUtils.isPrimitiveObject(o)
            || o instanceof String
            || o instanceof Property.Type;
    }

    @Override
    public boolean isKeyUsable(String key) {
        if (key == null) return false;
        return key.contains("Array") || ReflectionUtils.isNamePrimitiveOrPrimitiveObject(key) || key.contains("String");
    }

    @Override
    public String getKey(Object o) {
        if (o instanceof Property.Type) return convertForgeConfigPropertyTypeToFrameworkName((Property.Type) o);
        else if (o.getClass()
            .isArray())
            return convertPrimitiveNameToObjectName(
                o.getClass()
                    .getComponentType()
                    .getSimpleName())
                + ARRAY_SUFFIX;
        else return convertPrimitiveNameToObjectName(
            o.getClass()
                .getSimpleName());
    }

    private String convertForgeConfigPropertyTypeToFrameworkName(Property.Type type) {
        return type.name()
            .substring(0, 1)
            + type.name()
                .toLowerCase(Locale.US)
                .substring(1);
    }

    private String convertPrimitiveNameToObjectName(String primitiveName) {
        if (primitiveName.equalsIgnoreCase("int")) return "Integer";
        if (primitiveName.equalsIgnoreCase("long")) return "Long";
        if (primitiveName.equalsIgnoreCase("double")) return "Double";
        if (primitiveName.equalsIgnoreCase("float")) return "Float";
        if (primitiveName.equalsIgnoreCase("boolean")) return "Boolean";
        if (primitiveName.equalsIgnoreCase("char")) return "Character";
        if (primitiveName.equalsIgnoreCase("byte")) return "Byte";
        if (primitiveName.equalsIgnoreCase("void")) return "Void";
        if (primitiveName.equalsIgnoreCase("short")) return "Short";
        return primitiveName;
    }

    @Override
    public String serialize(Object o) throws ConfigException {
        if (!canSerializeObject(o)) throw new ConfigException("Cannot serialize object " + o);
        if (!o.getClass()
            .isArray()) return escape(String.valueOf(o), false);

        int length = Array.getLength(o);
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) out.append(',');
            Object element = Array.get(o, i);
            String serialized;
            try {
                serialized = ConfigRegistry.serialize(element);
            } catch (ConfigException e) {
                throw new ConfigException("Cannot serialize array element " + i + ": " + e.getMessage());
            }
            out.append(escape(serialized, true));
        }
        return out.append(']')
            .toString();
    }

    @Override
    public Object deserialize(String key, String s) throws ConfigException {
        // Deliberately no fallback to the raw string: a value that does not parse is an error, and silently
        // returning a String made the caller throw IllegalArgumentException from Field#set instead
        // (CODE_REVIEW §2.8).
        return deserializeUnSafe(key, s);
    }

    /**
     * Deserializes without any tolerance for type errors.
     *
     * @param key The type key
     * @param s   The serialized value
     * @return The value
     * @throws ConfigException if the key or the value is not usable
     */
    public Object deserializeUnSafe(String key, String s) throws ConfigException {
        if (s == null) throw new ConfigException("Cannot deserialize a null value");
        if (!isKeyUsable(key)) throw new ConfigException("Key '" + key + "' is invalid for this proxy");

        if (key.contains("Array")) return deserializeArray(key, s);
        return deserializeScalar(key, unescape(s));
    }

    private Object deserializeArray(String key, String s) throws ConfigException {
        String body = s.trim();
        if (!body.startsWith("[") || !body.endsWith("]"))
            throw new ConfigException("Array value '" + s + "' is not wrapped in brackets");
        body = body.substring(1, body.length() - 1);
        if (body.isEmpty()) return emptyArray(key);

        List<String> parts = splitEscaped(body);
        String elementKey = key.replace(ARRAY_SUFFIX, "");
        Object[] deserialized = new Object[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            deserialized[i] = ConfigRegistry.deserialize(elementKey, unescape(parts.get(i)));
        }

        try {
            if (key.contains("Integer"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToInt(deserialized);
            if (key.contains("Long"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToLong(deserialized);
            if (key.contains("Double"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToDouble(deserialized);
            if (key.contains("Float"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToFloat(deserialized);
            if (key.contains("Boolean"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToBoolean(deserialized);
            if (key.contains("Character"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToChar(deserialized);
            if (key.contains("Byte"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToByte(deserialized);
            if (key.contains("Void")) return null;
            if (key.contains("Short"))
                return com.austinv11.collectiveframework.utils.ArrayUtils.convertObjectArrayToShort(deserialized);
            if (key.contains("String"))
                return java.util.Arrays.copyOf(deserialized, deserialized.length, String[].class);
        } catch (RuntimeException e) {
            throw new ConfigException("Array value '" + s + "' does not match type " + key + ": " + e.getMessage());
        }
        return deserialized;
    }

    private static Object emptyArray(String key) {
        if (key.contains("Integer")) return new int[0];
        if (key.contains("Long")) return new long[0];
        if (key.contains("Double")) return new double[0];
        if (key.contains("Float")) return new float[0];
        if (key.contains("Boolean")) return new boolean[0];
        if (key.contains("Character")) return new char[0];
        if (key.contains("Byte")) return new byte[0];
        if (key.contains("Void")) return null;
        if (key.contains("Short")) return new short[0];
        if (key.contains("String")) return new String[0];
        return new Object[0];
    }

    private Object deserializeScalar(String key, String s) throws ConfigException {
        try {
            if (key.equalsIgnoreCase("integer")) return Integer.parseInt(s.trim());
            if (key.equalsIgnoreCase("long")) return Long.parseLong(s.trim());
            if (key.equalsIgnoreCase("double")) return Double.parseDouble(s.trim());
            if (key.equalsIgnoreCase("float")) return Float.parseFloat(s.trim());
            if (key.equalsIgnoreCase("boolean")) return Boolean.parseBoolean(s.trim());
            if (key.equalsIgnoreCase("character")) {
                if (s.isEmpty()) throw new ConfigException("A character value cannot be empty");
                return s.charAt(0);
            }
            if (key.equalsIgnoreCase("byte")) return Byte.parseByte(s.trim());
            if (key.equalsIgnoreCase("void")) return null;
            if (key.equalsIgnoreCase("short")) return Short.parseShort(s.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("'" + s + "' is not a valid " + key);
        }
        return s;
    }

    // ------------------------------------------------------------------------------------ escaping

    private static String escape(String value, boolean forArray) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case ESCAPE:
                    out.append(ESCAPE)
                        .append(ESCAPE);
                    break;
                case '\n':
                    out.append(ESCAPE)
                        .append('n');
                    break;
                case '\r':
                    out.append(ESCAPE)
                        .append('r');
                    break;
                case '\t':
                    out.append(ESCAPE)
                        .append('t');
                    break;
                case ',':
                case ']':
                    if (forArray) out.append(ESCAPE);
                    out.append(c);
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Reverses {@link #escape(String, boolean)}. An unrecognised escape sequence is kept verbatim, so a value
     * written by an older version - a Windows path such as {@code C:\data}, for instance - still reads back
     * unchanged.
     */
    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ESCAPE || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case ESCAPE:
                    out.append(ESCAPE);
                    break;
                case ',':
                    out.append(',');
                    break;
                case ']':
                    out.append(']');
                    break;
                default:
                    out.append(ESCAPE)
                        .append(next);
            }
        }
        return out.toString();
    }

    /** Splits on commas that are not preceded by a backslash, keeping the escape sequences intact. */
    private static List<String> splitEscaped(String value) {
        List<String> parts = new ArrayList<String>();
        StringBuilder current = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ESCAPE && i + 1 < value.length()) {
                current.append(c)
                    .append(value.charAt(++i));
            } else if (c == ',') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }
}

package com.austinv11.collectiveframework.minecraft.config;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Property;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.utils.ArrayUtils;
import com.austinv11.collectiveframework.utils.ReflectionUtils;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;

/**
 * A registry for configs
 */
public class ConfigRegistry {

    private static List<ConfigProxy> toBeInitialized = new ArrayList<ConfigProxy>();

    public static List<ConfigProxy> configs = new ArrayList<ConfigProxy>();
    private static List<IConfigProxy> proxies = new ArrayList<IConfigProxy>();

    static {
        registerConfigProxy(new DefaultProxy());
    }

    /**
     * Registers a config with the registry
     *
     * @param config The config to register
     * @throws ConfigException
     */
    public static void registerConfig(Object config) throws ConfigException {
        if (config == null) throw new ConfigException("Cannot register a null config!");
        if (!config.getClass()
            .isAnnotationPresent(Config.class))
            throw new ConfigException("Config " + config.toString() + " does not contain a Config annotation!");
        Config configAnnotation = config.getClass()
            .getAnnotation(Config.class);
        try {
            ConfigProxy configProxy = new ConfigProxy(configAnnotation, config);
            toBeInitialized.add(configProxy);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConfigException(e.getMessage());
        }
    }

    /**
     * Registers a config proxy
     *
     * @param proxy The config proxy
     */
    public static void registerConfigProxy(IConfigProxy proxy) {
        proxies.add(proxy);
    }

    /**
     * Only meant for internal use
     */
    public static void init() {
        for (ConfigProxy config : toBeInitialized) initialize(config);
        toBeInitialized.clear();
    }

    private static void initialize(ConfigProxy configProxy) {
        IConfigurationHandler handler = configProxy.handler;
        ConfigLoadEvent.Init event = new ConfigLoadEvent.Init();
        event.config = handler.convertToString(configProxy.config);
        event.configName = configProxy.fileName;
        event.isRevert = false;
        MinecraftForge.EVENT_BUS.post(event);
        handler.loadFile(configProxy.fileName, configProxy.config, configProxy.fields);
        configs.add(configProxy);
    }

    /**
     * Gets the key for the passed object
     *
     * @param o The object
     * @return The key
     */
    public static String getKey(Object o) {
        for (IConfigProxy proxy : proxies) if (proxy.canSerializeObject(o)) return proxy.getKey(o);
        return "@NULL@";
    }

    /**
     * Discovers the config fields of a class, grouped by category and sorted by name.
     * <p>
     * Shared by {@link ConfigProxy} and {@link DefaultConfigurationHandler#convertToString(Object)}: the latter
     * used to serialise nothing at all unless {@code loadFile} had already populated its field map, so
     * {@link ConfigLoadEvent.Init} carried an empty config string (CODE_REVIEW §1.6).
     *
     * @param type       The config class
     * @param config     The config instance
     * @param annotation The config annotation
     * @return Category name to (field name to field)
     */
    static TreeMap<String, Map<String, Field>> discoverFields(Class<?> type, Object config, Config annotation) {
        TreeMap<String, Map<String, Field>> fields = new TreeMap<String, Map<String, Field>>();
        collectFields(fields, type, config, annotation, new ArrayList<String>());
        return fields;
    }

    /**
     * Registers every usable config field on the class and its superclasses.
     * <p>
     * The previous implementation walked {@code getDeclaredFields()} and {@code getFields()} with no filter, so
     * compiler-generated fields (a synthetic {@code $jacocoData}, {@code this$0}, {@code $assertionsDisabled})
     * and {@code static final} constants became config entries - and a {@code static final} field cannot be
     * assigned at all. It also missed {@code private} superclass fields (CODE_REVIEW §3.18).
     *
     * @param fields     The map to fill
     * @param type       The class to inspect
     * @param config     The config instance
     * @param annotation The config annotation
     * @param visited    Names already registered, so a subclass field shadows its superclass namesake
     */
    private static void collectFields(TreeMap<String, Map<String, Field>> fields, Class<?> type, Object config,
        Config annotation, List<String> visited) {
        if (type == null || type == Object.class) return;
        collectFields(fields, type.getSuperclass(), config, annotation, visited);

        for (Field f : type.getDeclaredFields()) {
            if (f.isSynthetic()) continue;
            // A static final field cannot be written, and a non-static final field cannot be either.
            if (Modifier.isFinal(f.getModifiers())) continue;
            if (ArrayUtils.indexOf(annotation.exclude(), f.getName()) != -1) continue;
            if (visited.contains(f.getName())) continue;
            if (!isSerializable(config, f)) {
                CollectiveFramework.LOGGER.warn(
                    "Config field " + type.getName() + "." + f.getName() + " has no registered type proxy; ignoring");
                continue;
            }
            visited.add(f.getName());
            String category = f.isAnnotationPresent(Description.class) ? f.getAnnotation(Description.class)
                .category() : "General";
            Map<String, Field> categoryFields = fields.get(category);
            if (categoryFields == null) {
                categoryFields = new TreeMap<String, Field>();
                fields.put(category, categoryFields);
            }
            categoryFields.put(f.getName(), f);
        }
    }

    private static boolean isSerializable(Object config, Field f) {
        try {
            f.setAccessible(true);
            return ConfigRegistry.canSerialize(f.get(config));
        } catch (IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether any registered proxy can handle the given value.
     *
     * @param o The value
     * @return True if the value can be serialized
     */
    public static boolean canSerialize(Object o) {
        if (o == null) return false;
        for (IConfigProxy proxy : proxies) if (proxy.canSerializeObject(o)) return true;
        return false;
    }

    /**
     * Serializes the passed object into a string
     *
     * @param o The object to serialize
     * @return The serialized string
     * @throws ConfigException
     */
    public static String serialize(Object o) throws ConfigException {
        for (IConfigProxy proxy : proxies) if (proxy.canSerializeObject(o)) return proxy.serialize(o);
        return "@NULL@";
    }

    /**
     * Deserializes the passed string with the passed key
     *
     * @param key    The key representing the object type
     * @param string The serialized string
     * @return The deserialized object
     * @throws ConfigException
     */
    public static Object deserialize(String key, String string) throws ConfigException {
        for (IConfigProxy proxy : proxies) if (proxy.isKeyUsable(key)) return proxy.deserialize(key, string);
        return null;
    }

    /**
     * Returns a list of configuration elements for GUI configs
     *
     * @param configObject config instance
     * @param modid        mod id to use for lang entries
     * @return config element implementations
     */
    public static ArrayList<IConfigElement> getCategories(Object configObject, String modid) throws ConfigException {
        ArrayList<IConfigElement> categories = new ArrayList<>();
        for (ConfigProxy proxy : configs) {
            if (proxy.isFor(configObject)) try {
                categories.addAll(proxy.handler.getCategories(configObject, modid));
            } catch (IllegalAccessException | ConfigException e) {
                throw new ConfigException(e);
            }
        }
        return categories;
    }

    /**
     * Parses config entries and updates the items in memory before saving to a file
     *
     * @param config  config object
     * @param entries entries to parse and save
     */
    public static void onGuiClosed(Object config, List<GuiConfigEntries.IConfigEntry> entries) throws ConfigException {
        for (ConfigProxy proxy : configs) if (proxy.isFor(config)) proxy.handler.onGuiClosed(config, entries);
    }

    /**
     * Returns the file path of a config
     *
     * @param config config object
     * @return filepath
     */
    public static String getFilePath(Object config) {
        for (ConfigProxy proxy : configs)
            if (proxy.isFor(config)) return new File("./config/" + proxy.fileName).getAbsolutePath();
        return null;
    }

    /**
     * Convert the java type to the enum type expected by a config property
     *
     * @param o value object
     * @return Parsed type or string
     */
    public static Property.Type getForgeConfigType(Object o) {
        if (o.getClass()
            .isArray()) return Property.Type.STRING;
        return Property.Type.tryParse(
            o.getClass()
                .getSimpleName()
                .toUpperCase(Locale.US)
                .charAt(0));
    }

    /**
     * The default {@link com.austinv11.collectiveframework.minecraft.config.IConfigurationHandler} for configs
     */
    public static class DefaultConfigurationHandler implements IConfigurationHandler {

        private Map<String, Map<String, Field>> current = new HashMap<String, Map<String, Field>>();

        private File cachedFile;

        @Override
        public void setValue(String configValue, String category, Object value, Object config) {
            setValue(configValue, category, value, config, true);
        }

        @Override
        public void setValue(String configValue, String category, Object value, Object config, boolean saveToFile) {
            Map<String, Field> fields = current.containsKey(category) ? current.get(category)
                : new HashMap<String, Field>();
            Field field = fields.containsKey(configValue) ? fields.get(configValue)
                : ReflectionUtils.getDeclaredOrNormalField(configValue, config.getClass());
            try {
                field.set(config, value);
            } catch (IllegalAccessException e) {
                e.printStackTrace(); // This should never be reached
            }
            if (saveToFile) try {
                writeFile(cachedFile, config);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!current.containsKey(category) || !fields.containsKey(configValue)) {
                fields.put(configValue, field);
                current.put(category, fields);
            }
        }

        @Override
        public Object getValue(String configValue, String category, Object config) {
            if (!hasValue(configValue, category)) return null;
            Field field = current.get(category)
                .get(configValue);
            try {
                field.setAccessible(true);
                // Returns the value held by the field, not the java.lang.reflect.Field itself
                // (CODE_REVIEW §1.4).
                return field.get(config);
            } catch (IllegalAccessException e) {
                CollectiveFramework.LOGGER
                    .warn("Unable to read config value " + category + "." + configValue + ": " + e.getMessage());
                return null;
            }
        }

        @Override
        public void loadFile(String fileName, Object config, Map<String, Map<String, Field>> hint) {
            current = hint;
            cachedFile = new File("./config/" + fileName);
            if (cachedFile.exists()) try {
                readFile(cachedFile, config);
            } catch (Exception e) {
                e.printStackTrace();
            }
            else {
                try {
                    cachedFile.createNewFile();
                    writeFile(cachedFile, config);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void loadFromString(String string, Object config, Map<String, Map<String, Field>> hint) {
            try {
                readFromReader(new BufferedReader(new StringReader(string)), config);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public File getConfigFile(String fileName, Object config) {
            return cachedFile;
        }

        @Override
        public boolean hasValue(String configValue, String category) {
            return current.containsKey(category) && current.get(category)
                .containsKey(configValue);
        }

        @Override
        public String convertToString(Object config) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                writeToStream(new PrintStream(stream), config, fieldsFor(config));
                return stream.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public ArrayList<IConfigElement> getCategories(Object configObject, String modid)
            throws IllegalAccessException, ConfigException {
            if (CollectiveFramework.proxy.getSide()
                .equals(Side.SERVER)) throw new ConfigException("Cannot call method on a server.");
            ArrayList<IConfigElement> categories = new ArrayList<>();
            // fieldsFor() rather than current, so the GUI works before loadFile has run (CODE_REVIEW §1.6).
            Map<String, Map<String, Field>> fields = fieldsFor(configObject);
            for (String categoryName : fields.keySet()) {
                String languageKey = String.format("%s.lang.config.%s", modid, categoryName);
                ConfigCategory category = new ConfigCategory(languageKey);
                category.setLanguageKey(languageKey);
                for (String fieldName : fields.get(categoryName)
                    .keySet()) {
                    Field field = fields.get(categoryName)
                        .get(fieldName);
                    field.setAccessible(true);
                    String valueString = serialize(field.get(configObject));
                    String languageKeyProperty = String.format("%s.%s", languageKey, fieldName);
                    Property property = new Property(
                        languageKeyProperty,
                        valueString,
                        ConfigRegistry.getForgeConfigType(field.get(configObject)));
                    property.setLanguageKey(languageKeyProperty);
                    property.comment = field.isAnnotationPresent(Description.class)
                        ? field.getAnnotation(Description.class)
                            .comment()
                        : null;
                    category.put(fieldName, property);
                }
                categories.add(new ConfigElement(category));
            }
            return categories;
        }

        @Override
        public void onGuiClosed(Object configObject, List<GuiConfigEntries.IConfigEntry> entries)
            throws ConfigException {
            if (CollectiveFramework.proxy.getSide()
                .equals(Side.SERVER)) throw new ConfigException("Cannot call method on a server.");
            for (GuiConfigEntries.IConfigEntry category : entries) {
                if (category.getConfigElement()
                    .isProperty()) // Ignore non-category elements in root
                    continue;
                List<IConfigElement> elements = category.getConfigElement()
                    .getChildElements();
                for (IConfigElement element : elements) {
                    if (!element.isProperty()) // Ignore nested categories
                        continue;
                    // Will fail if the mod id or category (the two user defined values) contain a period/full stop
                    // "[mod id].lang.config.[category name].[entry name]"
                    Pattern pattern = Pattern.compile("^([^.]*)\\.(?:[^.]*\\.){2}([^.]*)\\.([^.]*)");
                    Matcher matcher = pattern.matcher(element.getName());
                    if (!matcher.find()) {
                        CollectiveFramework.LOGGER.warn("Failed to parse config element name: " + element.getName());
                        continue;
                    }
                    String categoryString = matcher.group(2);
                    String fieldName = matcher.group(3);
                    Field field = ReflectionUtils.getDeclaredOrNormalField(fieldName, configObject.getClass());
                    if (field != null) {
                        field.setAccessible(true);
                        try {
                            ConfigGuiType configGuiType = element.getType();
                            Property.Type propertyType = Property.Type.tryParse(
                                configGuiType.name()
                                    .charAt(0));
                            Object value;
                            if (field.get(configObject)
                                .getClass()
                                .isArray())
                                value = deserialize(getKey(field.get(configObject)), String.valueOf(element.get()));
                            else value = deserialize(getKey(propertyType), String.valueOf(element.get()));
                            if (value == null || !value.getClass()
                                .equals(
                                    field.get(configObject)
                                        .getClass()))
                                throw new ConfigException();
                            field.set(configObject, value);
                            current.get(categoryString)
                                .put(fieldName, field);
                        } catch (IllegalAccessException | ConfigException e) {
                            CollectiveFramework.LOGGER.debug(e);
                            CollectiveFramework.LOGGER.warn(
                                "Failed to parse config entry: " + fieldName + "=" + String.valueOf(element.get()));
                        }
                    }
                }
            }
            try {
                writeFile(cachedFile, configObject);
            } catch (IOException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        /**
         * The field map to write. Falls back to discovering it from the config class when {@link #loadFile} has
         * not run yet, which is the case for {@link ConfigLoadEvent.Init} and for
         * {@code CollectiveFramework#onServerJoin} (CODE_REVIEW §1.6).
         */
        private Map<String, Map<String, Field>> fieldsFor(Object config) {
            if (!current.isEmpty()) return current;
            if (config == null) return current;
            Config annotation = config.getClass()
                .getAnnotation(Config.class);
            if (annotation == null) return current;
            return ConfigRegistry.discoverFields(config.getClass(), config, annotation);
        }

        private void writeToStream(PrintStream writer, Object config, Map<String, Map<String, Field>> fields)
            throws IllegalAccessException, ConfigException {
            for (String category : fields.keySet()) {
                // The block is only opened once a field is actually written, and is always closed again.
                // Previously the closing brace was emitted only when the loop reached i == size, so a category
                // whose last field was skipped (clientSideOnly on a dedicated server) produced an unterminated
                // block and an unparseable file (CODE_REVIEW §1.5).
                boolean opened = false;
                for (String field : fields.get(category)
                    .keySet()) {
                    Field f = fields.get(category)
                        .get(field);
                    f.setAccessible(true);
                    if (f.isAnnotationPresent(Description.class) && f.getAnnotation(Description.class)
                        .clientSideOnly() && CollectiveFramework.proxy.getSide() == Side.SERVER) continue;
                    if (!opened) {
                        writer.println(category + " {");
                        opened = true;
                    }
                    String comment = f.isAnnotationPresent(Description.class) ? f.getAnnotation(Description.class)
                        .comment() : "None! Tell the mod author to include a comment!";
                    writer.println("\t# " + comment);
                    writer.println("\t" + getKey(f.get(config)) + ":" + field + "=" + serialize(f.get(config)));
                    writer.println();
                }
                if (opened) writer.println("}");
            }
            writer.flush();
            writer.close();
        }

        private void writeFile(File file, Object config) throws IOException, IllegalAccessException, ConfigException {
            PrintStream writer = new PrintStream(file);
            writeToStream(writer, config, fieldsFor(config));
        }

        private void readFile(File file, Object config)
            throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            readFromReader(reader, config);
            try {
                writeFile(file, config);
            } catch (ConfigException e) {
                e.printStackTrace();
            }
        }

        /**
         * Parses the config file format written by {@link #writeToStream(PrintStream, Object)}:
         * 
         * <pre>
         * Category {
         *     # comment
         *     Type:field=value
         *
         * }
         * </pre>
         * 
         * The previous implementation was a three-state line counter that assumed exactly one comment line, one
         * entry line and one blank line per field. Any deviation - a hand edit, a multi-line comment, a value
         * containing {@code =}, or a field skipped while writing - silently dropped every following value
         * (CODE_REVIEW §1.6). This walks the structure explicitly and reports what it cannot parse.
         */
        private void readFromReader(BufferedReader reader, Object config) throws IOException {
            String category = null;
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                if (category == null) {
                    if (!trimmed.endsWith("{")) {
                        warn("line " + lineNumber + ": expected a category header, found '" + trimmed + "'");
                        continue;
                    }
                    category = trimmed.substring(0, trimmed.length() - 1)
                        .trim();
                    if (category.isEmpty()) {
                        warn("line " + lineNumber + ": empty category name");
                        category = null;
                    }
                    continue;
                }

                if (trimmed.equals("}")) {
                    category = null;
                    continue;
                }

                int colon = trimmed.indexOf(':');
                int equals = trimmed.indexOf('=');
                if (colon < 0 || equals < 0 || colon > equals) {
                    warn("line " + lineNumber + ": expected 'Type:field=value', found '" + trimmed + "'");
                    continue;
                }

                String typeKey = trimmed.substring(0, colon)
                    .trim();
                String fieldName = trimmed.substring(colon + 1, equals)
                    .trim();
                // Deliberately not trimmed: leading spaces after '=' may be part of a String value.
                String rawValue = trimmed.substring(equals + 1);

                Field field = ReflectionUtils.getDeclaredOrNormalField(fieldName, config.getClass());
                if (field == null) {
                    warn("line " + lineNumber + ": no field named '" + fieldName + "' in " + category);
                    continue;
                }
                try {
                    Object value = deserialize(typeKey, rawValue);
                    field.setAccessible(true);
                    field.set(config, value);
                } catch (ConfigException e) {
                    warn("line " + lineNumber + ": " + e.getMessage() + " (keeping the default)");
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    warn("line " + lineNumber + ": cannot assign '" + rawValue + "' to " + fieldName + ": " + e);
                }
            }
            if (category != null) warn("unterminated category '" + category + "'");
        }

        private static void warn(String message) {
            CollectiveFramework.LOGGER.warn("Config parse: " + message);
        }
    }

    public static class ConfigProxy {

        public Object config;
        public IConfigurationHandler handler;
        public String fileName;
        public boolean doesSync;
        public TreeMap<String, Map<String, Field>> fields = new TreeMap<String, Map<String, Field>>();

        public ConfigProxy(Config annotation, Object config)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
            this.config = config;
            this.handler = (IConfigurationHandler) Class.forName(annotation.handler())
                .newInstance();
            this.fileName = annotation.fileName()
                .equals("@NULL@")
                    ? config.getClass()
                        .getSimpleName() + ".cfg"
                    : annotation.fileName();
            this.doesSync = annotation.doesSync();

            this.fields = ConfigRegistry.discoverFields(config.getClass(), config, annotation);
        }

        /**
         * Whether this proxy manages the given config object. Instances are matched by class, not by
         * identity: {@code CollectiveFramework} registers one instance while the config GUI hands over
         * another, and identity comparison silently produced an empty config screen (CODE_REVIEW §1.2).
         *
         * @param configObject The config object to test
         * @return True if this proxy manages that config
         */
        public boolean isFor(Object configObject) {
            return configObject != null && (config == configObject || config.getClass() == configObject.getClass());
        }
    }

    public static void onConfigReload(ConfigLoadEvent.Pre event) {
        if (!event.isCanceled()) {
            if (!event.isRevert) {
                CollectiveFramework.LOGGER.info("Reloading config '" + event.configName + "'");
                ConfigProxy proxy = findConfigProxyForConfigFile(configs, event.configName);
                if (proxy == null) {
                    CollectiveFramework.LOGGER.error("There was an error reloading the config!");
                    return;
                }
                proxy.handler.loadFromString(event.config, proxy.config, proxy.fields);
            } else {
                CollectiveFramework.LOGGER.info("Reverting config '" + event.configName + "'");
                ConfigProxy proxy = findConfigProxyForConfigFile(configs, event.configName);
                if (proxy == null) {
                    CollectiveFramework.LOGGER.error("There was an error reverting the config!");
                    return;
                }
                proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);
            }
        }
    }

    /**
     * Reloads every synced config from its local file, discarding values that were pushed by a server.
     * <p>
     * Called on the client when it disconnects, so a server's config values do not leak into the next world or
     * into single player. Only configs registered with {@code doesSync = true} are touched.
     *
     * @return The number of configs that were reloaded
     */
    public static int revertSyncedConfigs() {
        int reverted = 0;
        for (ConfigProxy proxy : configs) {
            if (!proxy.doesSync) continue;
            CollectiveFramework.LOGGER.info("Reverting synced config '" + proxy.fileName + "' from disk");
            proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);
            reverted++;
        }
        return reverted;
    }

    private static ConfigProxy findConfigProxyForConfigFile(List<ConfigProxy> proxies, String filename) {
        for (ConfigProxy proxy : proxies) if (proxy.fileName.equals(filename)) return proxy;
        return null;
    }
}

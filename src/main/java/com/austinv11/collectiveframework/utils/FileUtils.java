package com.austinv11.collectiveframework.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class to simplify file I/O
 */
public class FileUtils {

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    /**
     * Simple method to write to a file
     *
     * @param file   File to write to
     * @param string String to write
     * @throws IOException
     */
    public static void safeWrite(File file, String string) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create directory " + parent);
        }
        // UTF-8 rather than the platform default, and closed on every path.
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write(string);
            writer.flush();
        }
    }

    /**
     * Simple method to read all the contents of a file
     *
     * @param file File to read
     * @return The contents
     * @throws IOException
     */
    public static List<String> readAll(File file) throws IOException {
        // Looped on Reader.ready(), which can return false before end of file, so the last line (and anything
        // after a slow read) was silently dropped (CODE_REVIEW §3.9).
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    /**
     * Unzips a given file to the set directory.
     * <p>
     * Entries that would land outside {@code toDirectory} are rejected rather than written, so a crafted archive
     * cannot escape the target directory (CODE_REVIEW §3.9).
     *
     * @param file        The file to unzip
     * @param toDirectory The directory to unzip to
     * @throws IOException if the archive is unreadable or contains an unsafe entry
     */
    public static void unzip(File file, File toDirectory) throws IOException {
        if (file == null || toDirectory == null) throw new IllegalArgumentException("file and directory required");
        File root = toDirectory.getCanonicalFile();
        if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory()) {
            throw new IOException("Unable to create directory " + root);
        }

        try (ZipInputStream zips = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry zip;
            while ((zip = zips.getNextEntry()) != null) {
                File target = new File(root, zip.getName()).getCanonicalFile();
                if (!target.equals(root) && !target.getPath()
                    .startsWith(root.getPath() + File.separator)) {
                    throw new IOException("Refusing to extract '" + zip.getName() + "' outside " + root);
                }
                if (zip.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs() && !target.isDirectory()) {
                        throw new IOException("Unable to create directory " + target);
                    }
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Unable to create directory " + parent);
                }
                try (OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    // `> 0` stopped early on a zero-length read; -1 is the only end-of-entry signal.
                    while ((len = zips.read(buffer)) != -1) out.write(buffer, 0, len);
                }
            }
        }
    }

    /**
     * Gets a list of classes from a jar file.
     * <p>
     * The classes are loaded through a {@link URLClassLoader} over the jar, so entries that are not on the
     * running classpath can be resolved. They are loaded without initialisation. Entries that cannot be loaded
     * are logged and skipped rather than swallowed by an empty {@code catch} (CODE_REVIEW §3.9).
     *
     * @param file The jar file
     * @return The classes that could be loaded
     * @throws IOException            if the jar is unreadable
     * @throws ClassNotFoundException never thrown; declared for source compatibility
     */
    public static List<Class> parseClassesFromJar(File file) throws IOException, ClassNotFoundException {
        List<Class> classes = new ArrayList<Class>();
        if (file == null || !file.exists() || file.isDirectory()) return classes;

        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { file.toURI()
                .toURL() },
            FileUtils.class.getClassLoader()); JarInputStream jis = new JarInputStream(new FileInputStream(file))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                String className = name.substring(0, name.length() - ".class".length())
                    .replace('/', '.');
                try {
                    classes.add(Class.forName(className, false, loader));
                } catch (ClassNotFoundException | LinkageError e) {
                    LOG.debug("Skipping unloadable class " + className + " in " + file.getName());
                }
            }
        }
        return classes;
    }
}

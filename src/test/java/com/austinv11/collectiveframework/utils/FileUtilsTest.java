package com.austinv11.collectiveframework.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CODE_REVIEW §3.9 - {@code readAll} looped on {@code Reader.ready()} (which can return false before end of
 * file, silently dropping the tail), {@code unzip} had no zip-slip guard and used {@code read(...) > 0} instead
 * of {@code != -1}, and {@code parseClassesFromJar} swallowed every failure in an empty {@code catch} and could
 * not load classes that were not already on the classpath.
 */
public class FileUtilsTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    // --------------------------------------------------------------------------------- safeWrite

    @Test
    public void safeWriteCreatesTheParentDirectoryAndWritesUtf8() throws Exception {
        File target = new File(new File(folder.getRoot(), "a/b/c"), "out.txt");

        FileUtils.safeWrite(target, "h\u00e9llo");

        Assert.assertTrue(target.isFile());
        Assert.assertEquals("h\u00e9llo", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void safeWriteOverwrites() throws Exception {
        File target = folder.newFile("out.txt");
        FileUtils.safeWrite(target, "first");
        FileUtils.safeWrite(target, "second");
        Assert.assertEquals(
            "second",
            FileUtils.readAll(target)
                .get(0));
    }

    // ------------------------------------------------------------------------------------ readAll

    @Test
    public void readAllReturnsEveryLine() throws Exception {
        File file = folder.newFile("lines.txt");
        Files.write(file.toPath(), "one\ntwo\nthree".getBytes(StandardCharsets.UTF_8));

        List<String> lines = FileUtils.readAll(file);

        Assert.assertEquals(3, lines.size());
        Assert.assertEquals("three", lines.get(2));
    }

    /** §3.9 - the last line has no trailing newline, which is where {@code Reader.ready()} truncates. */
    @Test
    public void readAllKeepsTheFinalLineWithoutATrailingNewline() throws Exception {
        File file = folder.newFile("no-newline.txt");
        Files.write(file.toPath(), "first\nlast".getBytes(StandardCharsets.UTF_8));

        Assert.assertEquals(
            2,
            FileUtils.readAll(file)
                .size());
    }

    @Test
    public void readAllOnAnEmptyFileReturnsNothing() throws Exception {
        File file = folder.newFile("empty.txt");
        Assert.assertTrue(
            FileUtils.readAll(file)
                .isEmpty());
    }

    // -------------------------------------------------------------------------------------- unzip

    @Test
    public void unzipExtractsNestedEntries() throws Exception {
        File archive = folder.newFile("plain.zip");
        writeZip(archive, "a/b.txt", "content");

        File target = folder.newFolder("out");
        FileUtils.unzip(archive, target);

        File extracted = new File(new File(target, "a"), "b.txt");
        Assert.assertTrue(extracted.isFile());
        Assert.assertEquals("content", new String(Files.readAllBytes(extracted.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void unzipHandlesAZeroLengthEntry() throws Exception {
        File archive = folder.newFile("empty-entry.zip");
        writeZip(archive, "empty.txt", "");

        File target = folder.newFolder("out");
        FileUtils.unzip(archive, target);

        File extracted = new File(target, "empty.txt");
        Assert.assertTrue(extracted.isFile());
        Assert.assertEquals(0L, extracted.length());
    }

    /** §3.9 - an entry of {@code ../escape.txt} used to be written outside the target directory. */
    @Test
    public void unzipRefusesToEscapeTheTargetDirectory() throws Exception {
        File archive = folder.newFile("slip.zip");
        writeZip(archive, "../escaped.txt", "pwned");

        File target = folder.newFolder("out");
        File escaped = new File(folder.getRoot(), "escaped.txt");

        try {
            FileUtils.unzip(archive, target);
            Assert.fail("expected IOException for a zip-slip entry");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains("outside"));
        }
        Assert.assertFalse("the entry escaped the target directory", escaped.exists());
    }

    // ----------------------------------------------------------------------- parseClassesFromJar

    @Test
    public void parseClassesFromJarLoadsAClassFromTheArchive() throws Exception {
        File jar = folder.newFile("classes.jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("com/austinv11/collectiveframework/utils/FileUtils.class"));
            out.write(readOwnClassBytes());
            out.closeEntry();
        }

        List<Class> classes = FileUtils.parseClassesFromJar(jar);

        Assert.assertEquals(1, classes.size());
        Assert.assertEquals(
            FileUtils.class.getName(),
            classes.get(0)
                .getName());
    }

    @Test
    public void parseClassesFromJarIgnoresNonClassEntries() throws Exception {
        File jar = folder.newFile("resources.jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Assert.assertTrue(
            FileUtils.parseClassesFromJar(jar)
                .isEmpty());
    }

    /** §3.9 - this used to be an empty {@code catch}, hiding the failure entirely. */
    @Test
    public void parseClassesFromJarSkipsAnUnloadableClassWithoutThrowing() throws Exception {
        File jar = folder.newFile("broken.jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("com/example/Broken.class"));
            out.write("not a class file".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Assert.assertTrue(
            FileUtils.parseClassesFromJar(jar)
                .isEmpty());
    }

    @Test
    public void parseClassesFromJarHandlesMissingAndDirectoryInputs() throws Exception {
        Assert.assertTrue(
            FileUtils.parseClassesFromJar(new File(folder.getRoot(), "nope.jar"))
                .isEmpty());
        Assert.assertTrue(
            FileUtils.parseClassesFromJar(folder.getRoot())
                .isEmpty());
        Assert.assertTrue(
            FileUtils.parseClassesFromJar(null)
                .isEmpty());
    }

    // ------------------------------------------------------------------------------------ helpers

    private static void writeZip(File archive, String entryName, String content) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private static byte[] readOwnClassBytes() throws Exception {
        try (InputStream stream = FileUtils.class.getResourceAsStream("FileUtils.class")) {
            Assert.assertNotNull("FileUtils.class is not on the test classpath", stream);
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return buffer.toByteArray();
        }
    }
}

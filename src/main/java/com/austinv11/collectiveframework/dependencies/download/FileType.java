package com.austinv11.collectiveframework.dependencies.download;

/**
 * These represent the abilities of a download provider
 */
public enum FileType {

    BINARY,
    PLAIN_TEXT,
    OTHER;

    /**
     * Gets a filetype enum for one not listed.
     *
     * @param otherType File type
     * @return {@link #OTHER}
     * @deprecated This used to write {@code otherType} into the shared {@link #OTHER} constant, so two callers
     *             (or two threads) corrupted each other's value and every later {@code toString()} changed with
     *             it. An enum constant cannot carry per-caller data, so the name is no longer stored and
     *             {@link #toString()} always returns {@code "OTHER"} (CODE_REVIEW §3.8). Register an
     *             {@link IDownloadProvider} whose capabilities include {@link #OTHER} and carry the custom name
     *             out of band instead.
     */
    @Deprecated
    public static FileType getFileTypeForOther(String otherType) {
        return OTHER;
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link #OTHER} used to stringify to {@code null} (and NPE in {@code NoProviderFoundException}) unless
     * {@link #getFileTypeForOther(String)} had been called first. It is now always {@code "OTHER"}.
     */
    @Override
    public String toString() {
        return name();
    }
}

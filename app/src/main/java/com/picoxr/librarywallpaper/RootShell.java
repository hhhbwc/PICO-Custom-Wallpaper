package com.picoxr.librarywallpaper;

import java.io.File;

final class RootShell {
    private RootShell() {
    }

    static boolean makeReadable(File directory, File image) {
        return execute("chmod 755 " + quote(directory.getAbsolutePath())
                + " && chmod 644 " + quote(image.getAbsolutePath()));
    }

    static boolean execute(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\\"'\\\"'") + "'";
    }
}

package com.picoxr.librarywallpaper.data;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort {@code su} helper used by the module UI to restart the hooked apps after
 * apply/restore. Runs on background threads only.
 *
 * <p>Hardened against a hanging {@code su} (for example a superuser prompt nobody can
 * answer inside the headset): streams are drained while waiting and the child process is
 * destroyed after a timeout instead of blocking its worker thread forever.</p>
 */
public final class RootShell {
    private static final long TIMEOUT_SECONDS = 5;

    private RootShell() {
    }

    public static boolean restartApp(String packageName, String launchAction) {
        return execute("am force-stop " + packageName)
                && execute("am start -a " + launchAction);
    }

    public static boolean execute(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).start();
            drain(process);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drain(Process process) {
        Thread stdout = new Thread(() -> consume(process.getInputStream()), "RootShell-stdout");
        Thread stderr = new Thread(() -> consume(process.getErrorStream()), "RootShell-stderr");
        stdout.setDaemon(true);
        stderr.setDaemon(true);
        stdout.start();
        stderr.start();
    }

    private static void consume(InputStream stream) {
        try {
            byte[] buffer = new byte[512];
            while (stream.read(buffer) >= 0) {
                // discard
            }
        } catch (Exception ignored) {
            // stream closed when the process dies or times out
        } finally {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }
}

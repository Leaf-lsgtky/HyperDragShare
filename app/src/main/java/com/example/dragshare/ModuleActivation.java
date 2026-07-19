package com.example.dragshare;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Current-build injection handshake and root availability checks for the settings UI. */
final class ModuleActivation {
    static final String METHOD_REPORT_INJECTED = "report_injected";
    static final String EXTRA_VERSION_CODE = "version_code";

    private static final String PREFS_NAME = "module_activation";
    private static final String KEY_INJECTED_VERSION = "injected_version";
    private static final long ROOT_TIMEOUT_SECONDS = 4L;
    private static final String PORTAL_SERVICE_COMMAND =
            "am startservice --user current "
                    + "-a miui.intent.action.TEXT_CONTENT_EXTENSION "
                    + "-n com.miui.contentextension/"
                    + "com.miui.contentextension.services.TextContentExtensionService";
    private static final String PORTAL_BLACKLIST_ACTIVITY_COMMAND =
            "am start --user current -n com.miui.contentextension/"
                    + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity";

    private ModuleActivation() {}

    static boolean reportInjected(Context portalContext) {
        if (portalContext == null) {
            return false;
        }
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        try {
            portalContext.getContentResolver().call(
                    ImageStagingClient.BASE_URI,
                    METHOD_REPORT_INJECTED,
                    null,
                    extras);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void recordInjected(Context moduleContext, Bundle extras) {
        long reportedVersion = extras == null
                ? -1L
                : extras.getLong(EXTRA_VERSION_CODE, -1L);
        if (!matchesCurrentBuild(reportedVersion)) {
            return;
        }
        moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_INJECTED_VERSION, reportedVersion)
                .apply();
    }

    static boolean isCurrentBuildInjected(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_INJECTED_VERSION, -1L));
    }

    static boolean hasRootAccess() {
        return runRootCommand("id -u");
    }

    /**
     * Starts Taplus' own exported service so an already-running service receives
     * onStartCommand and a stopped service loads the current LSPosed hook.
     */
    static boolean requestPortalInjectionHandshake() {
        return runRootCommand(PORTAL_SERVICE_COMMAND);
    }

    static String portalHandshakeCommand() {
        return PORTAL_SERVICE_COMMAND;
    }

    /** Starts Taplus' non-exported blacklist activity as root for the current Android user. */
    static boolean openPortalBlacklistSettings() {
        return runRootCommand(portalBlacklistCommand());
    }

    static String portalBlacklistCommand() {
        return PORTAL_BLACKLIST_ACTIVITY_COMMAND;
    }

    private static boolean runRootCommand(String command) {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } finally {
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                    // Process cleanup only.
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // Process cleanup only.
                }
                process.destroy();
            }
        }
    }

    static boolean matchesCurrentBuild(long reportedVersion) {
        return reportedVersion == BuildConfig.VERSION_CODE;
    }
}

package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModuleActivationTest {
    @Test
    public void injectionAndPortalRootReportsMustMatchTheRunningBuild() {
        assertTrue(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE));
        assertFalse(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE - 1L));

        Context context = RuntimeEnvironment.getApplication();
        Bundle rootDenied = new Bundle();
        rootDenied.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        rootDenied.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, rootDenied);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle rootGranted = new Bundle();
        rootGranted.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        rootGranted.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        ModuleActivation.recordInjected(context, rootGranted);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle staleRootReport = new Bundle();
        staleRootReport.putLong(
                ModuleActivation.EXTRA_VERSION_CODE,
                BuildConfig.VERSION_CODE - 1L);
        staleRootReport.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, staleRootReport);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
    }

    @Test
    public void handshakeOnlyStartsThePortalService() {
        String command = ModuleActivation.portalHandshakeCommand();
        assertTrue(command.contains("com.miui.contentextension/"));
        assertTrue(command.contains("TextContentExtensionService"));
        assertFalse(command.contains("com.miui.contentcatcher"));
        assertFalse(command.contains("force-stop"));
    }

    @Test
    public void blacklistCommandStartsOnlyTheRequestedPortalActivity() {
        assertEquals(
                "am start --user current -n com.miui.contentextension/"
                        + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity",
                ModuleActivation.portalBlacklistCommand());
    }
}

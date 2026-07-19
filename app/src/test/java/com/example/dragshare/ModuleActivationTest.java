package com.example.dragshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ModuleActivationTest {
    @Test
    public void injectionReportMustMatchTheRunningBuild() {
        assertTrue(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE));
        assertFalse(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE - 1L));
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

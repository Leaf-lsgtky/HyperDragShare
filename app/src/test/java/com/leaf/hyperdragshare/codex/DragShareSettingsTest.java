package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.content.res.Configuration;
import android.os.Bundle;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class DragShareSettingsTest {
    @Test
    public void defaultsKeepExistingLightBehavior() {
        DragShareSettings settings = DragShareSettings.defaults();

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode);
        assertEquals(
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                settings.contentCaptureMode);
        assertEquals(DragShareSettings.STYLE_SIMPLE, settings.uiStyle);
        assertEquals(DragShareSettings.DEFAULT_EDGE_TRIGGER_DP, settings.edgeTriggerDp);
        assertEquals(
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond);
        assertEquals(
                DragShareSettings.DEFAULT_BLOCK_BACKGROUND_SCROLL,
                settings.blockBackgroundScroll);
        assertEquals(
                DragShareSettings.DEFAULT_TEXT_SHARING_ENABLED,
                settings.textSharingEnabled);
        assertEquals(
                DragShareSettings.DEFAULT_IMAGE_SHARING_ENABLED,
                settings.imageSharingEnabled);
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
        assertTrue(settings.preloadTextSegmenter);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                settings.simpleMenuPosition);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                settings.simpleMenuOpacityPercent);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                settings.simpleMenuCornerRadiusDp);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                settings.simpleMenuEdgeDistanceDp);
        assertEquals(
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                settings.iconOpacityPercent);
        assertEquals(
                DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
                settings.modernBlurRadiusDp);
        assertEquals(
                DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
                settings.modernGlassOpacityPercent);
        assertTrue(settings.closeMenuWhenPointerLeaves);
        assertTrue(settings.hiddenTargetKeys.isEmpty());
        assertTrue(settings.targetOrder.isEmpty());
        assertFalse(settings.accessibilityLandscapeRecognitionEnabled);
        assertTrue(settings.accessibilityBlacklistedPackages.isEmpty());
        assertEquals(
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                settings.accessibilityLongPressTimeoutMillis);
        assertEquals(
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                settings.accessibilityRecognitionSensitivityPercent);
        assertEquals(650, settings.resolveAccessibilityLongPressTimeoutMillis(650));
        assertEquals(1f, settings.accessibilityTouchSlopMultiplier(), 0f);
        assertFalse(settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE));
        assertTrue(settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_PORTRAIT));
    }

    @Test
    public void invalidValuesAreClampedToSupportedRanges() {
        DragShareSettings settings = new DragShareSettings(
                99,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode);
        assertEquals(DragShareSettings.STYLE_SIMPLE, settings.uiStyle);
        assertEquals(DragShareSettings.MIN_EDGE_TRIGGER_DP, settings.edgeTriggerDp);
        assertEquals(
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond);
    }

    @Test
    public void darkModeAndUpperEdgeValuesAreRetained() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_DARK,
                DragShareSettings.STYLE_PORTAL,
                DragShareSettings.MAX_EDGE_TRIGGER_DP,
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                true);

        assertEquals(DragShareSettings.COLOR_DARK, settings.colorMode);
        assertEquals(DragShareSettings.STYLE_PORTAL, settings.uiStyle);
        assertEquals(DragShareSettings.MAX_EDGE_TRIGGER_DP, settings.edgeTriggerDp);
        assertEquals(
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond);
        assertEquals(true, settings.blockBackgroundScroll);
    }

    @Test
    public void circleStyleIsAcceptedAndPreserved() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_CIRCLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false);

        assertEquals(DragShareSettings.STYLE_CIRCLE, settings.uiStyle);
    }

    @Test
    public void modernStyleAndBlurParametersAreClampedAndRoundTrip() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_DARK,
                DragShareSettings.STYLE_MODERN,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                false,
                new LinkedHashSet<>(),
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                true,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE);

        assertTrue(settings.isModernStyle());
        assertEquals(
                DragShareSettings.MAX_MODERN_BLUR_RADIUS_DP,
                settings.modernBlurRadiusDp);
        assertEquals(
                DragShareSettings.MIN_MODERN_GLASS_OPACITY_PERCENT,
                settings.modernGlassOpacityPercent);
        DragShareSettings fromBundle = DragShareSettings.fromBundle(settings.toBundle());
        assertEquals(DragShareSettings.STYLE_MODERN, fromBundle.uiStyle);
        assertEquals(settings.modernBlurRadiusDp, fromBundle.modernBlurRadiusDp);
        assertEquals(settings.modernGlassOpacityPercent, fromBundle.modernGlassOpacityPercent);
    }

    @Test
    public void sharingSwitchesAndTargetRulesAreRetained() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                false,
                true,
                new LinkedHashSet<>(Arrays.asList("pkg/.Hidden")),
                Arrays.asList("pkg/.Second", "pkg/.First"));

        assertFalse(settings.isSharingEnabled(false));
        assertTrue(settings.isSharingEnabled(true));
        assertFalse(settings.isTargetVisible("pkg/.Hidden"));
        assertTrue(settings.isTargetVisible("pkg/.Visible"));
        assertEquals(
                Arrays.asList("pkg/.Second", "pkg/.First"),
                settings.targetOrder);
    }

    @Test
    public void simpleMenuOptionsAreClamped() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                99,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                false,
                new LinkedHashSet<>(),
                Arrays.asList());

        assertEquals(DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION, settings.simpleMenuPosition);
        assertEquals(
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
                settings.simpleMenuOpacityPercent);
        assertEquals(
                DragShareSettings.MAX_SIMPLE_MENU_CORNER_RADIUS_DP,
                settings.simpleMenuCornerRadiusDp);
        assertFalse(settings.closeMenuWhenPointerLeaves);
    }

    @Test
    public void portalSettingsRetainSimpleMenuBackgroundOpacity() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL);

        DragShareSettings portalSettings = DragShareSettings.fromBundle(settings.toBundle());

        assertTrue(portalSettings.isPortalCaptureMode());
        assertEquals(
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
                portalSettings.simpleMenuOpacityPercent);
        assertEquals(
                0.2f,
                DragShareController.simpleMenuBackgroundOpacityFraction(
                        portalSettings.simpleMenuOpacityPercent),
                0f);
    }

    @Test
    public void edgeDistanceAndIconOpacityAreClamped() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                true,
                new LinkedHashSet<>(),
                Arrays.asList());

        assertEquals(
                DragShareSettings.MAX_SIMPLE_MENU_EDGE_DISTANCE_DP,
                settings.simpleMenuEdgeDistanceDp);
        assertEquals(
                DragShareSettings.MIN_ICON_OPACITY_PERCENT,
                settings.iconOpacityPercent);
    }

    @Test
    public void hiddenTargetsDoNotEnterSettingsOrder() {
        ShareTarget hidden = ShareTarget.saveToLocal(null);
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                new LinkedHashSet<>(Arrays.asList(hidden.key())),
                Arrays.asList(hidden.key()));

        List<ShareTarget> ordered = ShareTargetRepository.orderForSettings(
                Arrays.asList(hidden),
                settings);
        assertTrue(ordered.isEmpty());
    }

    @Test
    public void builtInActionsAppearForMatchingPayloads() {
        DragShareSettings settings = DragShareSettings.defaults();

        List<ShareTarget> textTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                false);
        List<ShareTarget> imageTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                true);

        assertEquals(2, textTargets.size());
        assertTrue(textTargets.get(0).isCopyTextToClipboard());
        assertTrue(textTargets.get(1).isTextSegmentation());
        assertEquals(2, imageTargets.size());
        assertTrue(imageTargets.get(0).isCopyImageToClipboard());
        assertTrue(imageTargets.get(1).isSaveToLocal());
    }

    @Test
    public void accessibilityCaptureModeRoundTripsThroughBundle() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
                true,
                new LinkedHashSet<>(Arrays.asList("pkg.accessibility.blacklisted")),
                700,
                150);

        assertTrue(settings.isAccessibilityCaptureMode());
        assertTrue(settings.accessibilityLandscapeRecognitionEnabled);
        assertTrue(settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE));
        assertTrue(settings.isAccessibilityPackageBlacklisted("pkg.accessibility.blacklisted"));
        assertEquals(700, settings.accessibilityLongPressTimeoutMillis);
        assertEquals(150, settings.accessibilityRecognitionSensitivityPercent);
        assertEquals(1.5f, settings.accessibilityTouchSlopMultiplier(), 0f);
        assertEquals(
                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
                DragShareSettings.fromBundle(settings.toBundle()).contentCaptureMode);
        assertTrue(DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityLandscapeRecognitionEnabled);
        assertTrue(DragShareSettings.fromBundle(settings.toBundle())
                .isAccessibilityPackageBlacklisted("pkg.accessibility.blacklisted"));
        assertEquals(700, DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityLongPressTimeoutMillis);
        assertEquals(150, DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityRecognitionSensitivityPercent);
    }

    @Test
    public void legacyCopyPreferencesMigrateToSeparateVisibleTargets() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                false,
                new LinkedHashSet<>(),
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                false,
                false,
                true);

        assertFalse(settings.preloadTextSegmenter);
        assertFalse(settings.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
        List<ShareTarget> textTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                false);
        List<ShareTarget> imageTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                true);
        assertEquals(1, textTargets.size());
        assertTrue(textTargets.get(0).isTextSegmentation());
        assertEquals(2, imageTargets.size());
        assertTrue(imageTargets.get(0).isCopyImageToClipboard());
        assertTrue(imageTargets.get(1).isSaveToLocal());
        assertFalse(DragShareSettings.fromBundle(settings.toBundle()).preloadTextSegmenter);
        assertFalse(DragShareSettings.fromBundle(settings.toBundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(DragShareSettings.fromBundle(settings.toBundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
        assertTrue(DragShareSettings.fromBundle(new Bundle()).preloadTextSegmenter);
        assertTrue(DragShareSettings.fromBundle(new Bundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(DragShareSettings.fromBundle(new Bundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));

        DragShareSettings sharedCopyHidden = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                new LinkedHashSet<>(Arrays.asList(DragShareSettings.TARGET_COPY)),
                Arrays.asList());
        assertFalse(sharedCopyHidden.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertFalse(sharedCopyHidden.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
    }

    @Test
    public void accessibilityGestureValuesAreClamped() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
                false,
                new LinkedHashSet<>(),
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);

        assertEquals(
                DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                settings.accessibilityLongPressTimeoutMillis);
        assertEquals(
                DragShareSettings.MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                settings.accessibilityRecognitionSensitivityPercent);
    }

    @Test
    public void invalidCaptureModesMigrateToPortal() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                99);
        assertTrue(settings.isPortalCaptureMode());

        Bundle oldBundle = new Bundle();
        assertTrue(DragShareSettings.fromBundle(oldBundle).isPortalCaptureMode());
    }

    @Test
    public void backgroundScrollSettingRoundTripsThroughPortalBundle() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                true);

        assertTrue(settings.blockBackgroundScroll);
        assertTrue(DragShareSettings.fromBundle(settings.toBundle()).blockBackgroundScroll);
    }

    @Test
    public void accessibilityBlacklistCombinesUserAndBuiltInPackages() {
        assertTrue(AccessibilityBlacklist.isBlockedByPackages(
                "pkg.user",
                new LinkedHashSet<>(Arrays.asList("pkg.user")),
                new LinkedHashSet<>()));
        assertTrue(AccessibilityBlacklist.isBlockedByPackages(
                "pkg.builtin",
                new LinkedHashSet<>(),
                new LinkedHashSet<>(Arrays.asList("pkg.builtin"))));
        assertFalse(AccessibilityBlacklist.isBlockedByPackages(
                "pkg.allowed",
                new LinkedHashSet<>(Arrays.asList("pkg.user")),
                new LinkedHashSet<>(Arrays.asList("pkg.builtin"))));
    }

}

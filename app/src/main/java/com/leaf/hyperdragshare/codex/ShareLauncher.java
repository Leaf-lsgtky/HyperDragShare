package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
final class ShareLauncher {
    private ShareLauncher() {}

    static void launch(
            Context context,
            CapturedContent payload,
            ShareTarget target,
            Uri stagedImage,
            DragShareToast toast) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(payload.mimeType());
            intent.setComponent(target.component);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (!payload.isImage()) {
                intent.putExtra(Intent.EXTRA_TEXT, payload.text);
            } else {
                if (stagedImage == null) {
                    throw new IllegalArgumentException("Image URI is not ready");
                }
                intent.setDataAndType(stagedImage, payload.mimeType());
                intent.putExtra(Intent.EXTRA_STREAM, stagedImage);
                intent.putExtra(Intent.EXTRA_TITLE, "drag-share.jpg");
                intent.setClipData(ClipData.newUri(
                        context.getContentResolver(), "drag-share-image", stagedImage));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ImageStagingClient.grantReadAccess(
                        context,
                        stagedImage,
                        target.component.getPackageName());
            }
            context.startActivity(intent);
        } catch (Throwable error) {
            DragShareLog.w("DragShare/Share", "launch failed target="
                    + target.component.flattenToShortString(), error);
            if (stagedImage != null) {
                try {
                    ImageStagingClient.revokeReadAccess(
                            context,
                            stagedImage,
                            target.component.getPackageName());
                } catch (Throwable ignored) {
                    // Preserve the original launch failure.
                }
            }
            if (toast != null) {
                toast.show("无法打开 " + target.label);
            }
        }
    }
}

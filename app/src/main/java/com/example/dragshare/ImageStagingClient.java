package com.example.dragshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageStagingClient {
    interface Callback {
        void onStaged(Uri uri);

        void onFailure(Throwable error);
    }

    static final String AUTHORITY = "com.example.dragshare.share";
    static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    static final String METHOD_STAGE = "stage_image";
    static final String METHOD_GRANT = "grant_image";
    static final String METHOD_REVOKE = "revoke_image";
    static final String EXTRA_BYTES = "bytes";
    static final String EXTRA_PACKAGE = "package";
    static final String RESULT_URI = "uri";
    static final String RESULT_GRANTED = "granted";

    private static final int MAX_BINDER_PAYLOAD = 700 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "drag-share-image-stage");
        thread.setDaemon(true);
        return thread;
    });

    private ImageStagingClient() {}

    static void stage(Context context, Bitmap bitmap, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                byte[] bytes = BitmapEncoder.encodeJpeg(bitmap, MAX_BINDER_PAYLOAD);
                if (bytes.length == 0 || bytes.length > MAX_BINDER_PAYLOAD) {
                    throw new IllegalStateException("Unable to fit image in Binder transaction");
                }
                Bundle extras = new Bundle();
                extras.putByteArray(EXTRA_BYTES, bytes);
                Bundle result = context.getContentResolver().call(
                        BASE_URI, METHOD_STAGE, null, extras);
                String uriValue = result == null ? null : result.getString(RESULT_URI);
                if (uriValue == null) {
                    throw new IllegalStateException("Image provider returned no URI");
                }
                callback.onStaged(Uri.parse(uriValue));
            } catch (Throwable error) {
                callback.onFailure(error);
            }
        });
    }

    static void grantReadAccess(Context context, Uri uri, String packageName) {
        Bundle extras = new Bundle();
        extras.putString(RESULT_URI, uri.toString());
        extras.putString(EXTRA_PACKAGE, packageName);
        Bundle result = context.getContentResolver().call(
                BASE_URI, METHOD_GRANT, null, extras);
        if (result == null || !result.getBoolean(RESULT_GRANTED)) {
            throw new SecurityException("Image provider did not grant URI access");
        }
    }

    static void revokeReadAccess(Context context, Uri uri, String packageName) {
        Bundle extras = new Bundle();
        extras.putString(RESULT_URI, uri.toString());
        extras.putString(EXTRA_PACKAGE, packageName);
        context.getContentResolver().call(BASE_URI, METHOD_REVOKE, null, extras);
    }
}

package com.leaf.hyperdragshare.codex;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class ShareImageProvider extends ContentProvider {
    private static final String TAG = "DragShareProvider";
    private static final long MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_FILE_BYTES = 750 * 1024;

    private File shareDirectory;

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }
        shareDirectory = new File(getContext().getCacheDir(), "shared-images");
        return shareDirectory.exists() || shareDirectory.mkdirs();
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (ModuleActivation.METHOD_REPORT_INJECTED.equals(method)) {
            enforcePortalCaller();
            ModuleActivation.recordInjected(contextOrThrow(), extras);
            return Bundle.EMPTY;
        }
        if (DragShareSettings.METHOD_GET_SETTINGS.equals(method)) {
            enforcePortalCaller();
            return DragShareSettings.readLocal(contextOrThrow()).toBundle();
        }
        if (ImageStagingClient.METHOD_GRANT.equals(method)) {
            enforcePortalCaller();
            return grantImage(extras);
        }
        if (ImageStagingClient.METHOD_REVOKE.equals(method)) {
            enforcePortalCaller();
            return revokeImage(extras);
        }
        if (!ImageStagingClient.METHOD_STAGE.equals(method)) {
            return super.call(method, arg, extras);
        }
        enforcePortalCaller();
        if (extras == null) {
            throw new IllegalArgumentException("Missing image bytes");
        }
        byte[] bytes = extras.getByteArray(ImageStagingClient.EXTRA_BYTES);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Invalid staged image size");
        }

        cleanupExpiredFiles();
        String token = UUID.randomUUID().toString();
        File temporary = new File(shareDirectory, token + ".tmp");
        File destination = fileForToken(token);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        } catch (IOException error) {
            temporary.delete();
            throw new IllegalStateException("Unable to stage shared image", error);
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("Unable to publish staged image");
        }

        Uri uri = uriForToken(token);
        grantReadAccessAsOwner(
                MainHook.TAPLUS_PACKAGE,
                uri);
        Log.i(TAG, "staged bytes=" + bytes.length);
        Bundle result = new Bundle();
        result.putString(ImageStagingClient.RESULT_URI, uri.toString());
        return result;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider");
        }
        File file = resolveFile(uri);
        if (!file.isFile()) {
            throw new FileNotFoundException(uri.toString());
        }
        Log.i(TAG, "open uid=" + Binder.getCallingUid() + " bytes=" + file.length());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return resolveToken(uri) == null ? null : "image/jpeg";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        File file = resolveFile(uri);
        String[] columns = projection == null
                ? new String[]{
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                String token = resolveToken(uri);
                row.add(token == null ? "drag-share.jpg" : ShareUriToken.fileName(token));
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else if (MediaStore.MediaColumns.MIME_TYPE.equals(column)) {
                row.add("image/jpeg");
            } else if (MediaStore.MediaColumns.DATE_MODIFIED.equals(column)) {
                row.add(file.lastModified() / 1000L);
            } else if (MediaStore.MediaColumns.TITLE.equals(column)) {
                row.add("DragShare image");
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Use call(stage_image)");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private Bundle grantImage(Bundle extras) {
        Uri uri = requireShareUri(extras);
        String packageName = requirePackageName(extras);
        File file = resolveFile(uri);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Staged image no longer exists");
        }
        grantReadAccessAsOwner(
                packageName,
                uri);
        Log.i(TAG, "granted package=" + packageName);
        Bundle result = new Bundle();
        result.putBoolean(ImageStagingClient.RESULT_GRANTED, true);
        return result;
    }

    private Bundle revokeImage(Bundle extras) {
        Uri uri = requireShareUri(extras);
        String packageName = requirePackageName(extras);
        revokeReadAccessAsOwner(
                packageName,
                uri);
        return Bundle.EMPTY;
    }

    private Uri requireShareUri(Bundle extras) {
        String value = extras == null
                ? null
                : extras.getString(ImageStagingClient.RESULT_URI);
        Uri uri = value == null ? null : Uri.parse(value);
        if (uri == null || resolveToken(uri) == null) {
            throw new IllegalArgumentException("Invalid share URI");
        }
        return uri;
    }

    private String requirePackageName(Bundle extras) {
        String packageName = extras == null
                ? null
                : extras.getString(ImageStagingClient.EXTRA_PACKAGE);
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing target package");
        }
        return packageName;
    }

    private void enforcePortalCaller() {
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return;
        }
        PackageManager packageManager = contextOrThrow().getPackageManager();
        String[] packages = packageManager.getPackagesForUid(callingUid);
        if (packages != null) {
            for (String packageName : packages) {
                if (MainHook.TAPLUS_PACKAGE.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Only Taplus can stage share images");
    }

    private File resolveFile(Uri uri) {
        String token = resolveToken(uri);
        if (token == null) {
            throw new IllegalArgumentException("Invalid share URI");
        }
        return fileForToken(token);
    }

    private String resolveToken(Uri uri) {
        if (!ImageStagingClient.AUTHORITY.equals(uri.getAuthority())) {
            return null;
        }
        if (uri.getPathSegments().size() != 2
                || !"shared".equals(uri.getPathSegments().get(0))) {
            return null;
        }
        return ShareUriToken.parse(uri.getPathSegments().get(1));
    }

    private File fileForToken(String token) {
        return new File(shareDirectory, token + ".jpg");
    }

    private void cleanupExpiredFiles() {
        File[] files = shareDirectory.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS;
        for (File file : files) {
            if (file.lastModified() < cutoff) {
                String token = ShareUriToken.parse(file.getName());
                if (token != null) {
                    revokeReadAccessAsOwner(null, uriForToken(token));
                }
                file.delete();
            }
        }
    }

    private Uri uriForToken(String token) {
        return ImageStagingClient.BASE_URI.buildUpon()
                .appendPath("shared")
                .appendPath(ShareUriToken.fileName(token))
                .build();
    }

    private void grantReadAccessAsOwner(String packageName, Uri uri) {
        long identity = Binder.clearCallingIdentity();
        try {
            contextOrThrow().grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void revokeReadAccessAsOwner(String packageName, Uri uri) {
        long identity = Binder.clearCallingIdentity();
        try {
            if (packageName == null) {
                contextOrThrow().revokeUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                contextOrThrow().revokeUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private android.content.Context contextOrThrow() {
        android.content.Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider is not attached");
        }
        return context;
    }
}

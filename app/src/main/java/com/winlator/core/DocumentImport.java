package com.winlator.core;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads documents through their provider instead of guessing a filesystem path. */
public final class DocumentImport {
    private DocumentImport() {}

    public static File copyToCache(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("No document selected");
        File file = File.createTempFile("component-import-", ".tmp", context.getCacheDir());
        boolean complete = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(file)) {
                if (input == null || !StreamUtils.copy(input, output))
                    throw new IOException("Cannot read selected document");
            }
            complete = true;
            return file;
        }
        finally {
            if (!complete) file.delete();
        }
    }

    public static String displayName(Context context, Uri uri) throws IOException {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                String name = index >= 0 ? cursor.getString(index) : null;
                if (name != null && !name.isEmpty() && !name.equals(".") && !name.equals("..") &&
                        !name.contains("/") && !name.contains("\\")) return name;
            }
        }
        throw new IOException("Document has no usable filename");
    }
}

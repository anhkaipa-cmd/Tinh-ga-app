package com.anhthu.tinhga;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public class EggExcelProvider extends ContentProvider {
    public static final String AUTHORITY = "com.anhthu.tinhga.excel";
    public static final Uri SHARE_URI = Uri.parse("content://" + AUTHORITY + "/report");
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"report".equals(uri.getLastPathSegment()) || !"r".equals(mode)) {
            throw new FileNotFoundException("Đường dẫn Excel không hợp lệ");
        }
        File file=new File(new File(getContext().getCacheDir(),"egg_exports"), "Can-trung.xlsx");
        if (!file.isFile()) throw new FileNotFoundException("Chưa xuất báo cáo");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
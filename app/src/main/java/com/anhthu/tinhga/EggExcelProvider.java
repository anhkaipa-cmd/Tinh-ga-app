package com.anhthu.tinhga;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;

/** Read-only content URI for sharing the generated Excel workbook with Zalo/Gmail/etc. */
public class EggExcelProvider extends ContentProvider {
    public static final String AUTHORITY = "com.anhthu.tinhga.excel";
    public static final Uri SHARE_URI = Uri.parse("content://" + AUTHORITY + "/report");
    public static final String MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private File workbook() {
        return new File(new File(getContext().getCacheDir(),"egg_exports"),"Can-trung.xlsx");
    }

    private boolean valid(Uri uri) {
        return uri != null && AUTHORITY.equals(uri.getAuthority()) &&
            "/report".equals(uri.getPath());
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return valid(uri) ? MIME : null;
    }

    /** Many share targets need DISPLAY_NAME / SIZE before reading openFile. */
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                   String[] selectionArgs, String sortOrder) {
        if(!valid(uri)) return null;
        String[] defaults = { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        String[] requested = projection == null ? defaults : projection;
        ArrayList<String> columns = new ArrayList<>();
        ArrayList<Object> values = new ArrayList<>();
        File file = workbook();
        for(String col:requested) {
            if(OpenableColumns.DISPLAY_NAME.equals(col)) {
                columns.add(col);
                values.add("Can-trung.xlsx");
            } else if(OpenableColumns.SIZE.equals(col)) {
                columns.add(col);
                values.add(file.isFile()?file.length():0L);
            }
        }
        MatrixCursor result=new MatrixCursor(columns.toArray(new String[0]));
        result.addRow(values.toArray());
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)
            throws FileNotFoundException {
        if(!valid(uri) || mode == null || !mode.startsWith("r")) {
            throw new FileNotFoundException("Yêu cầu đọc Excel không hợp lệ");
        }
        File file=workbook();
        if(!file.isFile()) throw new FileNotFoundException("Chưa có báo cáo Excel");
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri,ContentValues values) { return null; }
    @Override public int delete(Uri uri,String selection,String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs) { return 0; }
}

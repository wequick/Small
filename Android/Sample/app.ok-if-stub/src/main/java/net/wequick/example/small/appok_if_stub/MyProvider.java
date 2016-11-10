package net.wequick.example.small.appok_if_stub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

/**
 * Created by galen on 2016/11/4.
 */
public class MyProvider extends ContentProvider {

    DBHelper mDbHelper = null;
    SQLiteDatabase db = null;

    private static final String TAG = "MyProvider";
    private static final String CONTENT_URI = "net.wequick.example.small";
    private static final String TABLE_NAME = "test";

    private static final UriMatcher mMatcher;
    static{
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(CONTENT_URI, TABLE_NAME, 1);
    }

    @Override
    public String getType(Uri uri) {
        switch (mMatcher.match(uri)) {
            case 1:
                return TABLE_NAME;
            default:
                throw new IllegalArgumentException("Unknown URI" + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.i(TAG, "insert to " + uri);
        if (mMatcher.match(uri) != 1){
            throw new IllegalArgumentException("Unknown URI" + uri);
        }
        
        db.insert(TABLE_NAME, null, values);
        return uri;
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new DBHelper(getContext());
        db = mDbHelper.getReadableDatabase();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Log.i(TAG, "query from " + uri);
        Cursor c;
        switch (mMatcher.match(uri)) {
            case 1:
                c =  db.query(TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI" + uri);
        }

        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    protected class DBHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "test.db";
        private static final int DATABASE_VERSION = 1;

        public DBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)  throws SQLException {
            db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_NAME + "(id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR NOT NULL);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)  throws SQLException {
            db.execSQL("DROP TABLE IF EXISTS "+ TABLE_NAME + ";");
            onCreate(db);
        }
    }
}

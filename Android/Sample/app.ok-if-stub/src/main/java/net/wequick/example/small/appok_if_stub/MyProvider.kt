package net.wequick.example.small.appok_if_stub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log

/**
 * Created by galen on 2016/11/4.
 */
class MyProvider : ContentProvider() {

    private var mDbHelper: DBHelper? = null
    internal var db: SQLiteDatabase? = null

    override fun getType(uri: Uri): String? {
        when (mMatcher.match(uri)) {
            1 -> return TABLE_NAME
            else -> throw IllegalArgumentException("Unknown URI" + uri)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.i(TAG, "insert to " + uri)
        if (mMatcher.match(uri) != 1) {
            throw IllegalArgumentException("Unknown URI" + uri)
        }

        db!!.insert(TABLE_NAME, null, values)
        return uri
    }

    override fun onCreate(): Boolean {
        mDbHelper = DBHelper(context)
        db = mDbHelper!!.readableDatabase
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        Log.i(TAG, "query from " + uri)
        val c: Cursor
        when (mMatcher.match(uri)) {
            1 -> c = db!!.query(TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
            else -> throw IllegalArgumentException("Unknown URI" + uri)
        }

        return c
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int {
        // TODO Auto-generated method stub
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        // TODO Auto-generated method stub
        return 0
    }

    protected inner class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        @Throws(SQLException::class)
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_NAME(id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR NOT NULL);")
        }

        @Throws(SQLException::class)
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME;")
            onCreate(db)
        }
    }

    companion object {

        private val TAG = "MyProvider"
        private val CONTENT_URI = "net.wequick.example.small"
        private val TABLE_NAME = "test"
        private val DATABASE_NAME = "test.db"
        private val DATABASE_VERSION = 1

        private val mMatcher: UriMatcher

        init {
            mMatcher = UriMatcher(UriMatcher.NO_MATCH)
            mMatcher.addURI(CONTENT_URI, TABLE_NAME, 1)
        }
    }
}

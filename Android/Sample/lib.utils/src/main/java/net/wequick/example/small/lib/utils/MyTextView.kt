package net.wequick.example.small.lib.utils

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.TextView

/**
 * Created by leon on 2016/2/17.
 */
class MyTextView : TextView {
    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs == null) {
            return
        }

        val ta = context.obtainStyledAttributes(attrs, R.styleable.MyTextView)
        val label = ta.getString(R.styleable.MyTextView_label)
        text = "MyTextView: " + label!!
    }
}

package net.wequick.example.small.lib.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by leon on 2016/2/17.
 */
public class MyTextView extends TextView {
    public MyTextView(Context context) {
        super(context);
        init(context, null);
    }

    public MyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MyTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context,AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MyTextView);
        String label = ta.getString(R.styleable.MyTextView_label);
        setText("MyTextView: " + label);
    }
}

package net.wequick.small;

import android.databinding.DataBindingComponent;
import android.databinding.ViewDataBinding;

/**
 * Created by galen on 07/06/2017.
 */

public interface DataBinderMappable {
    ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, android.view.View view, int layoutId);
    ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, android.view.View[] views, int layoutId);
    int getLayoutId(String tag);
    String convertBrIdToString(int id);
}

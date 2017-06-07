package net.wequick.small;

import android.databinding.ViewDataBinding;
import android.databinding.DataBindingComponent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by galen on 07/06/2017.
 */

public class DataBinderMapper {

    private HashMap<String, DataBinderMappable> dataBinderMappers;
    private ArrayList<String> unresolvedPackages;
    private String bindingLayoutTag;
    private String bindingPackageName;

    private String getPackageName(int resId) {
        return Small.getContext().getResources().getResourcePackageName(resId);
    }

    private DataBinderMappable getSubMapper(View view) {
        return getSubMapper(getPackageName(view.getId()));
    }

    private DataBinderMappable getSubMapper(String pkg) {
        if (unresolvedPackages != null && unresolvedPackages.contains(pkg)) {
            return null;
        }

        DataBinderMappable subMapper = null;
        if (dataBinderMappers != null) {
            subMapper = dataBinderMappers.get(pkg);
        }
        if (subMapper == null) {
            try {
                Class bindingClass = Class.forName(pkg + ".databinding.DataBinderMapper");
                subMapper = (DataBinderMappable) bindingClass.getConstructors()[0].newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                if (unresolvedPackages == null) {
                    unresolvedPackages = new ArrayList<>();
                }
                unresolvedPackages.add(pkg);
                return null;
            }
        }

        if (dataBinderMappers == null) {
            dataBinderMappers = new HashMap<>();
        }
        dataBinderMappers.put(pkg, subMapper);
        bindingPackageName = pkg;
        return subMapper;
    }

    public ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, android.view.View view, int layoutId) {
        DataBinderMappable subMapper = getSubMapper(view);
        if (subMapper == null) {
            return null;
        }

        layoutId = subMapper.getLayoutId(bindingLayoutTag);
        if (layoutId == 0) {
            bindingPackageName = null;
            throw new IllegalArgumentException("View is not a binding layout");
        }

        return subMapper.getDataBinder(bindingComponent, view, layoutId);
    }

    ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, android.view.View[] views, int layoutId) {
        DataBinderMappable subMapper = getSubMapper(views[0]);
        if (subMapper == null) {
            return null;
        }

        layoutId = subMapper.getLayoutId(bindingLayoutTag);
        if (layoutId == 0) {
            bindingPackageName = null;
            throw new IllegalArgumentException("View is not a binding layout");
        }

        return subMapper.getDataBinder(bindingComponent, views, layoutId);
    }

    int getLayoutId(String tag) {
        bindingLayoutTag = tag;
        return 1;
    }

    String convertBrIdToString(int id) {
        if (bindingPackageName == null) {
            return null;
        }

        DataBinderMappable subMapper = getSubMapper(bindingPackageName);
        if (subMapper == null) {
            return null;
        }

        return subMapper.convertBrIdToString(id);
    }
}

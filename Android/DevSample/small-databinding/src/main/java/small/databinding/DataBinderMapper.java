package small.databinding;

import android.databinding.ViewDataBinding;
import android.databinding.DataBindingComponent;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;

import net.wequick.small.Small;

public class DataBinderMapper {

    private static final String TAG = "SmallDataBinding";
    private static final int PASSING_LAYOUT_ID = 1;

    private HashMap<String, DataBinderMapperWrapper> dataBinderMappers;
    private ArrayList<String> unresolvedPackages;
    private String bindingLayoutTag;
    private String bindingPackageName;

    private String getPackageName(int resId) {
        try {
            return Small.getContext().getResources().getResourcePackageName(resId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get package name from resource id: "
                    + String.format("0x%08x", resId));
            return null;
        }
    }

    private DataBinderMapperWrapper getSubMapper(int layoutId) {
        return getSubMapper(getPackageName(layoutId));
    }

    private DataBinderMapperWrapper getSubMapper(String pkg) {
        if (pkg == null) {
            return null;
        }

        if (unresolvedPackages != null && unresolvedPackages.contains(pkg)) {
            return null;
        }

        DataBinderMapperWrapper subMapper = null;
        if (dataBinderMappers != null) {
            subMapper = dataBinderMappers.get(pkg);
        }
        if (subMapper == null) {
            subMapper = DataBinderMapperWrapper.wrap(pkg);
            if (subMapper == null) {
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

    public ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, View view, int layoutId) {
        DataBinderMapperWrapper subMapper = getSubMapper(layoutId);
        if (subMapper == null) {
            return null;
        }

        layoutId = subMapper.getLayoutId((String) view.getTag());
        if (layoutId == 0) {
            bindingPackageName = null;
            throw new IllegalArgumentException("View is not a binding layout");
        }

        return subMapper.getDataBinder(bindingComponent, view, layoutId);
    }

    ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, View[] views, int layoutId) {
        DataBinderMapperWrapper subMapper = getSubMapper(layoutId);
        if (subMapper == null) {
            return null;
        }

        layoutId = subMapper.getLayoutId((String) views[0].getTag());
        if (layoutId == 0) {
            bindingPackageName = null;
            throw new IllegalArgumentException("View is not a binding layout");
        }

        return subMapper.getDataBinder(bindingComponent, views, layoutId);
    }

    int getLayoutId(String tag) {
        // Passing a non-zero layout id so that we can invoke the `getDataBinder' method
        // in which we'll resolve the real layout id.
        return PASSING_LAYOUT_ID;
    }

    String convertBrIdToString(int id) {
        if (bindingPackageName == null) {
            return null;
        }

        DataBinderMapperWrapper subMapper = getSubMapper(bindingPackageName);
        if (subMapper == null) {
            return null;
        }

        return subMapper.convertBrIdToString(id);
    }
}

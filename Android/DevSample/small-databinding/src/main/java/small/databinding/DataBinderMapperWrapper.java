/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package small.databinding;

import android.databinding.DataBindingComponent;
import android.databinding.ViewDataBinding;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class DataBinderMapperWrapper {

    private Object mBase;
    private Method mGetDataBinderForView;
    private Method mGetDataBinderForViews;
    private Method mGetLayoutId;
    private Method mConvertBrIdToString;

    private DataBinderMapperWrapper() { }

    public static DataBinderMapperWrapper wrap(String pkg) {
        DataBinderMapperWrapper wrapper = new DataBinderMapperWrapper();

        try {
            Class bindingClass = Class.forName(pkg + ".databinding.DataBinderMapper");
            Constructor constructor = bindingClass.getConstructor(new Class[]{});
            constructor.setAccessible(true);
            Object base = constructor.newInstance();

            Method m = bindingClass.getDeclaredMethod("getDataBinder", DataBindingComponent.class, View.class, int.class);
            m.setAccessible(true);
            wrapper.mGetDataBinderForView = m;

            m = bindingClass.getDeclaredMethod("getDataBinder", DataBindingComponent.class, View[].class, int.class);
            m.setAccessible(true);
            wrapper.mGetDataBinderForViews = m;

            m = bindingClass.getDeclaredMethod("getLayoutId", String.class);
            m.setAccessible(true);
            wrapper.mGetLayoutId = m;

            m = bindingClass.getDeclaredMethod("convertBrIdToString", int.class);
            m.setAccessible(true);
            wrapper.mConvertBrIdToString = m;

            wrapper.mBase = base;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get DataBinderMapper for package '" + pkg + "'.", e);
        }

        return wrapper;
    }

    ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, View view, int layoutId) {
        try {
            return (ViewDataBinding) mGetDataBinderForView.invoke(mBase, bindingComponent, view, layoutId);
        } catch (Exception e) {
            return null;
        }
    }

    ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, View[] views, int layoutId) {
        try {
            return (ViewDataBinding) mGetDataBinderForViews.invoke(mBase, bindingComponent, views, layoutId);
        } catch (Exception e) {
            return null;
        }
    }

    int getLayoutId(String tag) {
        try {
            return (int) mGetLayoutId.invoke(mBase, tag);
        } catch (Exception e) {
            return 0;
        }
    }

    String convertBrIdToString(int id) {
        try {
            return (String) mConvertBrIdToString.invoke(mBase, id);
        } catch (Exception e) {
            return null;
        }
    }
}

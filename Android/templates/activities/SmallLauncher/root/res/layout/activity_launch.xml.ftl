<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0099cc"
    tools:context="${relativePackage}.${activityClass}">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp" android:layout_weight="4"></LinearLayout>

    <!-- The primary full-screen view. This can be replaced with whatever view
         is needed to present your content, e.g. VideoView, SurfaceView,
         TextureView, etc. -->
    <TextView android:id="@+id/fullscreen_content" android:layout_width="match_parent"
        android:layout_height="0dp" android:layout_weight="1" android:keepScreenOn="true" android:textColor="#4fc3f7"
        android:textStyle="bold" android:textSize="50sp" android:gravity="center"
        android:text="@string/dummy_content" />
    
    <!-- The tips for first launching -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="0dp" android:layout_weight="1"
        android:id="@+id/prepare_text"
        android:visibility="invisible"
        android:text="@string/initializing" android:gravity="center" android:textColor="#33b5e5" android:textSize="14sp"/>
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp" android:layout_weight="3"></LinearLayout>

    <!-- Footer copyright -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="0dp" android:layout_weight="1"
        android:text="@string/copyright" android:gravity="center" android:textColor="#33b5e5" android:textSize="12sp"/>

</FrameLayout>

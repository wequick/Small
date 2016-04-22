<resources>

   <style name="FullscreenTheme" parent="${themeName}">
        <item name="android:actionBarStyle">@style/FullscreenActionBarStyle</item>
        <item name="android:windowActionBarOverlay">true</item>
        <item name="android:windowBackground">@null</item>
        <item name="metaButtonBarStyle">?android:attr/buttonBarStyle</item>
        <item name="metaButtonBarButtonStyle">?android:attr/buttonBarButtonStyle</item>
    </style>

    <style name="FullscreenActionBarStyle" parent="<#if appCompat>Widget.AppCompat.ActionBar<#else>android:Widget.Holo.ActionBar</#if>">
        <item name="android:background">@color/black_overlay</item>
    </style>

</resources>

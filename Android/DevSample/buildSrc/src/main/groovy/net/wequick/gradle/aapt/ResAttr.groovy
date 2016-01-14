/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License; Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *    public static int  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing; software
 * distributed under the License is distributed on an "AS IS" BASIS; WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND; either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.wequick.gradle.aapt

/**
 * enum from libs/androidfw/Command.cpp
 */
public enum ResAttr {
    public static int LABEL_ATTR = 0x01010001;
    public static int ICON_ATTR = 0x01010002;
    public static int NAME_ATTR = 0x01010003;
    public static int PERMISSION_ATTR = 0x01010006;
    public static int EXPORTED_ATTR = 0x01010010;
    public static int GRANT_URI_PERMISSIONS_ATTR = 0x0101001b;
    public static int RESOURCE_ATTR = 0x01010025;
    public static int DEBUGGABLE_ATTR = 0x0101000f;
    public static int VALUE_ATTR = 0x01010024;
    public static int VERSION_CODE_ATTR = 0x0101021b;
    public static int VERSION_NAME_ATTR = 0x0101021c;
    public static int SCREEN_ORIENTATION_ATTR = 0x0101001e;
    public static int MIN_SDK_VERSION_ATTR = 0x0101020c;
    public static int MAX_SDK_VERSION_ATTR = 0x01010271;
    public static int REQ_TOUCH_SCREEN_ATTR = 0x01010227;
    public static int REQ_KEYBOARD_TYPE_ATTR = 0x01010228;
    public static int REQ_HARD_KEYBOARD_ATTR = 0x01010229;
    public static int REQ_NAVIGATION_ATTR = 0x0101022a;
    public static int REQ_FIVE_WAY_NAV_ATTR = 0x01010232;
    public static int TARGET_SDK_VERSION_ATTR = 0x01010270;
    public static int TEST_ONLY_ATTR = 0x01010272;
    public static int ANY_DENSITY_ATTR = 0x0101026c;
    public static int GL_ES_VERSION_ATTR = 0x01010281;
    public static int SMALL_SCREEN_ATTR = 0x01010284;
    public static int NORMAL_SCREEN_ATTR = 0x01010285;
    public static int LARGE_SCREEN_ATTR = 0x01010286;
    public static int XLARGE_SCREEN_ATTR = 0x010102bf;
    public static int REQUIRED_ATTR = 0x0101028e;
    public static int INSTALL_LOCATION_ATTR = 0x010102b7;
    public static int SCREEN_SIZE_ATTR = 0x010102ca;
    public static int SCREEN_DENSITY_ATTR = 0x010102cb;
    public static int REQUIRES_SMALLEST_WIDTH_DP_ATTR = 0x01010364;
    public static int COMPATIBLE_WIDTH_LIMIT_DP_ATTR = 0x01010365;
    public static int LARGEST_WIDTH_LIMIT_DP_ATTR = 0x01010366;
    public static int PUBLIC_KEY_ATTR = 0x010103a6;
    public static int CATEGORY_ATTR = 0x010103e8;
    public static int BANNER_ATTR = 0x10103f2;
    public static int ISGAME_ATTR = 0x10103f4;
}

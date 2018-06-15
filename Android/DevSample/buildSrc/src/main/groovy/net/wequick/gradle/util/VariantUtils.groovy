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
package net.wequick.gradle.util

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput

final class VariantUtils {

    static void allOutputs(BaseVariant variant, Closure<BaseVariantOutput> closure) {
        if (variant.outputs.size() > 0) {
//            if (variant.outputs[0].hasProperty('outputFile')) {
                variant.outputs.each closure
                return
//            }
        }

        variant.outputs.all closure
    }
}

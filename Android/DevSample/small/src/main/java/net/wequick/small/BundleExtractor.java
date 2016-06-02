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
package net.wequick.small;

import java.io.File;

/**
 * Extract bundle interface, implemented by the BundleLauncher implementation and pass to
 * the BundleParser to specify the extraction behavior of each entries in the bundle archive.
 *
 */
public interface BundleExtractor {

    /** The path to extract the bundle entries */
    File getExtractPath(Bundle bundle);

    /**
     * The file for the specify <tt>entryName</tt> to extract.
     *
     * @return <tt>null</tt> if no need to extract
     */
    File getExtractFile(Bundle bundle, String entryName);
}

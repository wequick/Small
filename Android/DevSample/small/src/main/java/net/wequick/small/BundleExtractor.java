package net.wequick.small;

import java.io.File;

/**
 * Created by galen on 16/5/31.
 */
public interface BundleExtractor {

    File getExtractPath(Bundle bundle);
    File getExtractFile(Bundle bundle, String entryName);
}

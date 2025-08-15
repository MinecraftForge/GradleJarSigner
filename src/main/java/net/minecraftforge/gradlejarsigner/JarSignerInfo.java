/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.File;

public interface JarSignerInfo {
    void setAlias(CharSequence value);

    void setAlias(Provider<? extends CharSequence> value);

    void setStorePass(CharSequence value);

    void setStorePass(Provider<? extends CharSequence> value);

    void setKeyPass(CharSequence value);

    void setKeyPass(Provider<? extends CharSequence> value);

    /**
     * A base64 encode string containing the keystore data. This will be written to a temporary file and then deleted
     * after the task is run.
     *
     * @param value Base64 encode keystore
     */
    void setKeyStoreData(CharSequence value);

    /**
     * A base64 encode string containing the keystore data. This will be written to a temporary file and then deleted
     * after the task is run.
     *
     * @param value Base64 encode keystore
     */
    void setKeyStoreData(Provider<? extends CharSequence> value);

    void setKeyStoreFile(Object value);

    void setKeyStoreFile(File value);

    void setKeyStoreFile(RegularFile value);

    void setKeyStoreFile(Provider<?> value);
}

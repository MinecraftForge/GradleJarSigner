/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.File;

interface JarSignerInfoInternal extends JarSignerInfo {
    JarSignerInfoContainer getContainer();

    @Override
    default void setAlias(CharSequence value) {
        this.getContainer().setAlias(value);
    }

    @Override
    default void setAlias(Provider<? extends CharSequence> value) {
        this.getContainer().setAlias(value);
    }

    @Override
    default void setStorePass(CharSequence value) {
        this.getContainer().setStorePass(value);
    }

    @Override
    default void setStorePass(Provider<? extends CharSequence> value) {
        this.getContainer().setStorePass(value);
    }

    @Override
    default void setKeyPass(CharSequence value) {
        this.getContainer().setKeyPass(value);
    }

    @Override
    default void setKeyPass(Provider<? extends CharSequence> value) {
        this.getContainer().setKeyPass(value);
    }

    @Override
    default void setKeyStoreData(CharSequence value) {
        this.getContainer().setKeyStoreData(value);
    }

    @Override
    default void setKeyStoreData(Provider<? extends CharSequence> value) {
        this.getContainer().setKeyStoreData(value);
    }

    @Override
    default void setKeyStoreFile(Object value) {
        this.getContainer().setKeyStoreFile(value);
    }

    @Override
    default void setKeyStoreFile(File value) {
        this.getContainer().setKeyStoreFile(value);
    }

    @Override
    default void setKeyStoreFile(RegularFile value) {
        this.getContainer().setKeyStoreFile(value);
    }

    @Override
    default void setKeyStoreFile(Provider<?> value) {
        this.getContainer().setKeyStoreFile(value);
    }
}

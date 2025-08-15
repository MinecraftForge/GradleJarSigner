/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.Zip;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.function.Consumer;

abstract class GradleJarSignerExtension implements GradleJarSignerExtensionInternal {
    private final Project project;
    private final JarSignerInfoContainer container;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject GradleJarSignerExtension(ObjectFactory objects, Project project) {
        this.project = project;
        this.container = objects.newInstance(JarSignerInfoContainer.class, project);
    }

    @Override
    public ISignTask sign(Zip task, @Nullable Action<? super ISignTask> cfg) {
        return this.getObjects().newInstance(SignTask.class, this.container, task, cfg);
    }

    public void fromEnvironmentVariables() {
        fromEnvironmentVariables(this.project.getName());
    }

    public void fromEnvironmentVariables(CharSequence prefix) {
        autoDetect(prefix != null ? prefix.toString() : null);
    }

    public void fromEnvironmentVariables(Provider<? extends CharSequence> prefix) {
        autoDetect(prefix.map(Object::toString).getOrNull());
    }

    public void autoDetect() {
        autoDetect(this.project.getName());
    }

    public void autoDetect(CharSequence prefix) {
        autoDetect(prefix != null ? prefix.toString() : null);
    }

    public void autoDetect(Provider<? extends CharSequence> prefix) {
        autoDetect(prefix.map(Object::toString).getOrNull());
    }

    private void autoDetect(String prefix) {
        set(prefix, "SIGN_KEY_ALIAS", this::setAlias);
        set(prefix, "SIGN_KEY_PASSWORD", this::setKeyPass);
        set(prefix, "SIGN_KEYSTORE_PASSWORD", this::setStorePass);
        set(prefix, "SIGN_KEYSTORE_DATA", this::setKeyStoreData);
    }

    @Override
    public JarSignerInfoContainer getContainer() {
        return this.container;
    }

    // TODO [GradleJarSigner] Get values lazily in 2.0
    //  This eagerly evaluates GStrings
    private void set(@Nullable String prefix, String key, Consumer<String> prop) {
        String data = null;
        if (prefix != null) {
            data = (String) project.findProperty(prefix + '.' + key);
            if (data == null)
                data = System.getenv(prefix + '.' + key);
        }

        if (data == null)
            data = (String) project.findProperty(key);
        if (data == null)
            data = System.getenv(key);

        if (data != null)
            prop.accept(data);
    }
}

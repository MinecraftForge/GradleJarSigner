/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import java.io.File;
import java.util.function.Consumer;

import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Zip;

import groovy.lang.Closure;

public class GradleJarSignerExtension {
    private final Project project;
    private String alias;
    private String storePass;
    private String keyPass;
    private String keyStoreData;
    private File keyStoreFile;

    public GradleJarSignerExtension(Project project) {
        this.project = project;
    }

    public SignTask sign(Zip task) {
        return new SignTask(this, task, null);
    }

    public SignTask sign(Zip task, Closure<SignTask> cfg) {
        return new SignTask(this, task, cfg);
    }

    public void fromEnvironmentVariables() {
        set("SIGN_KEY_ALIAS", this::setAlias);
        set("SIGN_KEY_PASSWORD", this::setKeyPass);
        set("SIGN_KEYSTORE_PASSWORD", this::setStorePass);
        set("SIGN_KEYSTORE_DATA", this::setKeyStoreData);
    }

    public void setAlias(String value) {
        this.alias = value;
    }

    public void setStorePass(String value) {
        this.storePass = value;
    }

    public void setKeyPass(String value) {
        this.keyPass = value;
    }

    /**
     * A base64 encode string containing the keystore data.
     * This will be written to a temporary file and then deleted after the task is run.
     */
    public void setKeyStoreData(String value) {
        this.keyStoreData = value;
    }

    public void setKeyStoreFile(File value) {
        this.keyStoreFile = value;
    }

    // Package private because I intentionally don't want getters for key info.
    void fill(SignTask task) {
        if (this.alias != null)
            task.setAlias(this.alias);
        if (this.keyPass != null)
            task.setKeyPass(this.keyPass);
        if (this.storePass != null)
            task.setStorePass(this.storePass);
        if (this.keyStoreData != null)
            task.setKeyStoreData(this.keyStoreData);
        if (this.keyStoreFile != null)
            task.setKeyStoreFile(this.keyStoreFile);
    }

    private void set(String key, Consumer<String> prop) {
        var data = System.getenv(key);
        if (data != null)
            prop.accept(data);
        else
            this.project.getLogger().warn("Missing " + key + " Environment Variable");
    }
}

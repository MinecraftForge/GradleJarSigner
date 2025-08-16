/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.Action;
import org.gradle.api.tasks.bundling.Zip;

public interface GradleJarSignerExtension extends JarSignerInfo {
    String NAME = "jarSigner";

    default SignTask sign(Zip task) {
        return this.sign(task, null);
    }

    SignTask sign(Zip task, Action<? super SignTask> cfg);

    void fromEnvironmentVariables();

    void fromEnvironmentVariables(CharSequence prefix);

    void autoDetect();

    void autoDetect(CharSequence prefix);
}

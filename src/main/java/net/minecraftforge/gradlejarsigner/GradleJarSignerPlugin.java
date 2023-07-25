/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GradleJarSignerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.getExtensions().create("jarSigner", GradleJarSignerExtension.class, target);
    }
}

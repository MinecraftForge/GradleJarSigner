/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.inject.Inject;

abstract class GradleJarSignerPlugin implements Plugin<Project> {
    @Inject
    public GradleJarSignerPlugin() { }

    @Override
    public void apply(Project target) {
        target.getExtensions().create(IGradleJarSignerExtension.NAME, GradleJarSignerExtension.class, target);
    }
}

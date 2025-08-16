/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

interface GradleJarSignerExtensionInternal extends GradleJarSignerExtension, JarSignerInfoInternal, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(GradleJarSignerExtension.class);
    }
}

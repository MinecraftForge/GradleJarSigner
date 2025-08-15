/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import org.gradle.api.tasks.util.PatternFilterable;

public interface ISignTask extends JarSignerInfo, PatternFilterable {
}

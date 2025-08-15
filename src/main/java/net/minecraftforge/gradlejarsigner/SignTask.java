/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import groovy.util.MapEntry;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

abstract class SignTask implements SignTaskInternal {
    private final JarSignerInfoContainer container;
    private final Zip parent;
    private final PatternSet patternSet = new PatternSet();

    protected abstract @Inject ObjectFactory getObjects();

    SignTask(JarSignerInfoContainer container, Zip parent, @Nullable Action<? super ISignTask> config) {
        this.parent = parent;

        this.container = this.getObjects().newInstance(JarSignerInfoContainer.class, this.parent.getProject());

        container.fill(this.container);
        if (config != null)
            config.execute(this);
        this.addProperties();
        this.parent.doLast(this::signSafe);
    }

    private void addProperties() {
        TaskInputs in = this.parent.getInputs();
        if (!patternSet.isEmpty()) {
            in.property("signJar.patternSet.excludes", patternSet.getExcludes());
            in.property("signJar.patternSet.includes", patternSet.getIncludes());
        }
        in.property("signJar.alias", this.container.alias).optional(true);
        in.property("signJar.storePass", this.container.storePass).optional(true);
        in.property("signJar.keyPass", this.container.keyPass).optional(true);
        in.property("signJar.keyStoreData", this.container.keyStoreData).optional(true);
        if (this.container.keyStoreFile.isPresent())
            in.file(this.container.keyStoreFile);
    }

    private <T extends Task> void signSafe(T task) {
        try {
            if (hasEnoughInfo())
                this.sign(task);
            else
                task.getLogger().warn("Jar will be unsigned, missing key information");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasEnoughInfo() {
        return this.container.alias.isPresent() &&
               this.container.storePass.isPresent() &&
               this.container.keyPass.isPresent() &&
               (this.container.keyStoreData.isPresent() || this.container.keyStoreFile.isPresent());
    }

    @SuppressWarnings("unchecked")
    private static File getTaskArchiveFile(Zip task) {
        try {
            // Try getting the new thing first, to avoid potential deprecation methods
            Provider<RegularFile> archiveFile = (Provider<RegularFile>) Objects.requireNonNull(
                InvokerHelper.getProperty(task, "archiveFile"),
                "Could not find Zip.archiveFile property. Must be in older Gradle."
            );
            return archiveFile.get().getAsFile();
        } catch (Exception suppressed) {
            // In older gradle? hope the old thing works.
            try {
                return task.getArchivePath();
            } catch (Exception e) {
                // Well, we tried. Add the first exception so it's not lost in the stacktrace.
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    private <T extends Task> void sign(T task) throws IOException {
        final Map<String, Entry<byte[], Long>> ignoredStuff = new HashMap<>();

        File tmp = this.parent.getTemporaryDir();
        File output = getTaskArchiveFile(this.parent);
        File original = new File(tmp, output.getName() + ".original");
        Files.move(output.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);

        File input = original;
        if (!patternSet.isEmpty()) {
            input = new File(tmp, input.getName() + ".unsigned");
            processInputJar(original, input, ignoredStuff);
            if (!ignoredStuff.isEmpty())
                output = new File(tmp, input.getName() + ".signed");
        }

        File keyStore;
        if (this.container.keyStoreFile.isPresent()) {
            if (this.container.keyStoreData.isPresent())
                throw new IllegalStateException("Both KeyStoreFile and KeyStoreData can not be set at the same time");
            keyStore = this.container.keyStoreFile.get();
        } else if (this.container.keyStoreData.isPresent()) {
            byte[] data = Base64.getDecoder().decode(this.container.keyStoreData.get().getBytes(StandardCharsets.UTF_8));
            keyStore = new File(tmp, "keystore");
            Files.write(keyStore.toPath(), data);
        } else {
            throw new IllegalArgumentException("SignJar needs either a Base64 encoded KeyStore file, or a path to a KeyStore file");
        }

        Map<String, String> map = new HashMap<>();
        map.put("alias", this.container.alias.get());
        map.put("storePass", this.container.storePass.get());
        map.put("jar", input.getAbsolutePath());
        map.put("signedJar", output.getAbsolutePath());
        map.put("keyStore", keyStore.getAbsolutePath());
        if (this.container.keyPass.isPresent())
            map.put("keypass", this.container.keyPass.get());

        try {
            this.parent.getProject().getAnt().invokeMethod("signjar", map);
        } finally {
            if (!this.container.keyStoreFile.isPresent())
                keyStore.delete();
        }

        if (!ignoredStuff.isEmpty())
            writeOutputJar(output, getTaskArchiveFile(this.parent), ignoredStuff);
    }

    private void processInputJar(File input, File output, final Map<String, Entry<byte[], Long>> unsigned) throws IOException {
        final Spec<FileTreeElement> spec = patternSet.getAsSpec();

        output.getParentFile().mkdirs();
        try (JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
            this.parent.getProject().zipTree(input).visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails details) {
                    try {
                        String path = details.getPath();
                        ZipEntry entry = new ZipEntry(path.endsWith("/") ? path : path + "/");
                        outs.putNextEntry(entry);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                @SuppressWarnings("unchecked")
                public void visitFile(FileVisitDetails details) {
                    try {
                        if (spec.isSatisfiedBy(details)) {
                            ZipEntry entry = new ZipEntry(details.getPath());
                            entry.setTime(details.getLastModified());
                            outs.putNextEntry(entry);
                            details.copyTo(outs);
                            outs.closeEntry();
                        } else {
                            InputStream stream = details.open();
                            ByteArrayOutputStream tmp = new ByteArrayOutputStream(stream.available());
                            byte[] buf = new byte[0x100];
                            int len;
                            while ((len = stream.read(buf)) != -1)
                                tmp.write(buf, 0, len);

                            byte[] data = tmp.toByteArray();
                            unsigned.put(details.getPath(), new MapEntry(data, details.getLastModified()));
                            stream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void writeOutputJar(File signedJar, File outputJar, Map<String, Entry<byte[], Long>> unsigned) throws IOException {
        outputJar.getParentFile().mkdirs();

        JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar)));

        byte[] buf = new byte[0x100];
        ZipFile base = new ZipFile(signedJar);
        for (ZipEntry e : Collections.list(base.entries())) {
            if (e.isDirectory()) {
                outs.putNextEntry(e);
            } else {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                outs.putNextEntry(n);
                InputStream in = base.getInputStream(e);
                int len;
                while ((len = in.read(buf)) != -1)
                    outs.write(buf, 0, len);
                outs.closeEntry();
            }
        }
        base.close();

        for (Entry<String, Entry<byte[], Long>> e : unsigned.entrySet()) {
            ZipEntry n = new ZipEntry(e.getKey());
            n.setTime(e.getValue().getValue());
            outs.putNextEntry(n);
            outs.write(e.getValue().getKey());
            outs.closeEntry();
        }

        outs.close();
    }

    @Override
    public JarSignerInfoContainer getContainer() {
        return this.container;
    }

    @Override
    public PatternFilterable patternFilterable() {
        return this.patternSet;
    }
}

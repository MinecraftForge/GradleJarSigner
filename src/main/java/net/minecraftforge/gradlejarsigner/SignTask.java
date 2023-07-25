/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradlejarsigner;

import java.io.BufferedOutputStream;
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
import java.util.Set;
import java.util.Map.Entry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.Task;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import groovy.lang.Closure;
import groovy.util.MapEntry;

public class SignTask implements PatternFilterable {
    private final GradleJarSignerExtension ext;
    private final Zip parent;
    private final Closure<SignTask> config;
    private final Property<String> alias;
    private final Property<String> storePass;
    private final Property<String> keyPass;
    private final Property<String> keyStoreData;
    private final RegularFileProperty keyStoreFile;
    private final PatternSet patternSet = new PatternSet();

    @SuppressWarnings("serial")
    SignTask(GradleJarSignerExtension ext, Zip parent, Closure<SignTask> config) {
        this.ext = ext;
        this.parent = parent;
        this.config = config;
        if (this.config != null)
            this.config.setDelegate(this);

        var objs = this.parent.getProject().getObjects();

        this.alias = objs.property(String.class);
        this.storePass = objs.property(String.class);
        this.keyPass = objs.property(String.class);
        this.keyStoreData = objs.property(String.class);
        this.keyStoreFile = objs.fileProperty();

        this.parent.configure(new Closure<Object>(parent) {
            @SuppressWarnings("unused")
            public Object doCall() {
                SignTask.this.ext.fill(SignTask.this);
                if (SignTask.this.config != null)
                    SignTask.this.config.call();
                return SignTask.this.addProperties();
            }
        });
        this.parent.doLast(this::signSafe);
    }

    private Object addProperties() {
        var in = this.parent.getInputs();
        if (!patternSet.isEmpty()) {
            in.property("signJar.patternSet.excludes", patternSet.getExcludes());
            in.property("signJar.patternSet.includes", patternSet.getIncludes());
        }
        in.property("signJar.alias", this.alias).optional(true);
        in.property("signJar.storePass", this.storePass).optional(true);
        in.property("signJar.keyPass", this.keyPass).optional(true);
        in.property("signJar.keyStoreData", this.keyStoreData).optional(true);
        if (this.keyStoreFile.isPresent())
            in.file(this.keyStoreFile);
        return null;
    }

    private <T extends Task> void signSafe(T task) {
        try {
            if (hasEnoughInfo())
                this.sign(task);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasEnoughInfo() {
        return this.alias.isPresent() &&
               this.storePass.isPresent() &&
               this.keyPass.isPresent() &&
               (this.keyStoreData.isPresent() || this.keyStoreFile.isPresent());
    }

    private <T extends Task> void sign(T task) throws IOException {
        final Map<String, Entry<byte[], Long>> ignoredStuff = new HashMap<>();

        var tmp = this.parent.getTemporaryDir();
        var output = this.parent.getArchiveFile().get().getAsFile();
        var original = new File(tmp, output.getName() + ".original");
        Files.move(output.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);

        var input = original;
        if (!patternSet.isEmpty()) {
            input = new File(tmp, input.getName() + ".unsigned");
            processInputJar(original, input, ignoredStuff);
            if (!ignoredStuff.isEmpty())
                output = new File(tmp, input.getName() + ".signed");
        }

        File keyStore;
        if (this.keyStoreFile.isPresent()) {
            if (this.keyStoreData.isPresent())
                throw new IllegalStateException("Both KeyStoreFile and KeyStoreData can not be set at the same time");
            keyStore = this.keyStoreFile.get().getAsFile();
        } else if (this.keyStoreData.isPresent()) {
            var data = Base64.getDecoder().decode(this.keyStoreData.get().getBytes(StandardCharsets.UTF_8));
            keyStore = new File(tmp, "keystore");
            Files.write(keyStore.toPath(), data);
        } else {
            throw new IllegalArgumentException("SignJar needs either a Base64 encoded KeyStore file, or a path to a KeyStore file");
        }

        var map = new HashMap<String, String>();
        map.put("alias", this.alias.get());
        map.put("storePass", this.storePass.get());
        map.put("jar", input.getAbsolutePath());
        map.put("signedJar", output.getAbsolutePath());
        map.put("keyStore", keyStore.getAbsolutePath());
        if (this.keyPass.isPresent())
            map.put("keypass", this.keyPass.get());

        try {
            this.parent.getProject().getAnt().invokeMethod("signjar", map);
        } finally {
            if (!this.keyStoreFile.isPresent())
                keyStore.delete();
        }

        if (!ignoredStuff.isEmpty())
            writeOutputJar(output, this.parent.getArchiveFile().get().getAsFile(), ignoredStuff);
    }

    private void processInputJar(File input, File output, final Map<String, Entry<byte[], Long>> unsigned) throws IOException {
        var spec = patternSet.getAsSpec();

        output.getParentFile().mkdirs();
        try (var outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
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
                            var data = stream.readAllBytes();
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

        ZipFile base = new ZipFile(signedJar);
        for (ZipEntry e : Collections.list(base.entries())) {
            if (e.isDirectory()) {
                outs.putNextEntry(e);
            } else {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                outs.putNextEntry(n);
                base.getInputStream(e).transferTo(outs);
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

    public void setAlias(String value) {
        this.alias.set(value);
    }

    public void setStorePass(String value) {
        this.storePass.set(value);
    }

    public void setKeyPass(String value) {
        this.keyPass.set(value);
    }

    /**
     * A base64 encode string containing the keystore data.
     * This will be written to a temporary file and then deleted after the task is run.
     */
    public void setKeyStoreData(String value) {
        this.keyStoreData.set(value);
    }

    public void setKeyStoreFile(File value) {
        this.keyStoreFile.set(value);
    }

    @Override
    public PatternFilterable exclude(String... arg0) {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Iterable<String> arg0) {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> arg0) {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(@SuppressWarnings("rawtypes") Closure arg0) {
        return patternSet.exclude(arg0);
    }

    @Internal
    @Override
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Internal
    @Override
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Override
    public PatternFilterable include(String... arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Iterable<String> arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(@SuppressWarnings("rawtypes") Closure arg0) {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> arg0) {
        return patternSet.setExcludes(arg0);
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> arg0) {
        return patternSet.setIncludes(arg0);
    }
}

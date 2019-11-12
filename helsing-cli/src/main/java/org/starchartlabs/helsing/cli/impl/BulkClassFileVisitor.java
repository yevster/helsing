/*
 * Copyright (c) Nov 12, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.cli.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

//TODO romeara
public class BulkClassFileVisitor extends SimpleFileVisitor<Path> {

    private final ClassVisitor classVisitor;

    public BulkClassFileVisitor(ClassVisitor classVisitor) {
        this.classVisitor = Objects.requireNonNull(classVisitor);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);

        if (file.toString().matches(".*\\.class")) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                ClassReader cr = new ClassReader(inputStream);

                cr.accept(classVisitor, 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return FileVisitResult.CONTINUE;
    }

}

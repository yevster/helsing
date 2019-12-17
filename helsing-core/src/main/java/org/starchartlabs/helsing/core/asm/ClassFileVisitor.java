/*
 * Copyright (c) Dec 9, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.asm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

public class ClassFileVisitor extends SimpleFileVisitor<Path> {

    private static final String CLASS_FILE_REGEX = ".*\\.class";

    private final ClassVisitor classVisitor;

    private final Predicate<Path> fileFilter;

    public ClassFileVisitor(ClassVisitor classVisitor, Predicate<Path> fileFilter) {
        this.classVisitor = Objects.requireNonNull(classVisitor);
        this.fileFilter = Objects.requireNonNull(fileFilter);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);

        if (file.toString().matches(CLASS_FILE_REGEX) && fileFilter.test(file)) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                ClassReader cr = new ClassReader(inputStream);

                cr.accept(classVisitor, 0);
            }
        }

        return FileVisitResult.CONTINUE;
    }

}

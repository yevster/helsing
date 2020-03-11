/*
 * Copyright (c) Feb 18, 2020 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Predicate;

//TODO romeara
public class FilteringFileVisitor extends SimpleFileVisitor<Path> {

    private final Predicate<Path> fileFilter;

    private final FileConsumer fileVisitor;

    public FilteringFileVisitor(Predicate<Path> fileFilter, FileConsumer fileVisitor) {
        this.fileFilter = Objects.requireNonNull(fileFilter);
        this.fileVisitor = Objects.requireNonNull(fileVisitor);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);

        if (fileFilter.test(file)) {
            fileVisitor.acceptFile(file);
        }

        return FileVisitResult.CONTINUE;
    }

}

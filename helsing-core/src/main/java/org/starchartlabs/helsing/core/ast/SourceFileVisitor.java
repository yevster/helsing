/*
 * Copyright (c) Dec 16, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.ast;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SourceFileVisitor extends SimpleFileVisitor<Path> {

    private static final String SOURCE_FILE_REGEX = ".*\\.java";

    private final Consumer<String> sourceVisitor;

    private final Predicate<Path> fileFilter;

    public SourceFileVisitor(Consumer<String> sourceVisitor, Predicate<Path> fileFilter) {
        this.sourceVisitor = Objects.requireNonNull(sourceVisitor);
        this.fileFilter = Objects.requireNonNull(fileFilter);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);

        if (file.toString().matches(SOURCE_FILE_REGEX) && fileFilter.test(file)) {
            String contents = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .collect(Collectors.joining("\n"));

            sourceVisitor.accept(contents);
        }

        return FileVisitResult.CONTINUE;
    }

}

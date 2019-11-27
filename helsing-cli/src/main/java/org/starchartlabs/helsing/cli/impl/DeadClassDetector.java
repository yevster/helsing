/*
 * Copyright (c) Nov 26, 2019 StarChart Labs Authors.
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.alloy.core.Preconditions;

public class DeadClassDetector {

    private static final int ASM_API = Opcodes.ASM7;

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ClassUseTracer tracer;

    public DeadClassDetector(ClassUseTracer tracer) {
        this.tracer = Objects.requireNonNull(tracer);
    }

    public Set<String> getDeadClasses(Path directory, Set<String> externalApiClasses) {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(externalApiClasses);
        Preconditions.checkArgument(Files.isDirectory(directory), "%s is not a directory", directory.toString());

        try {
            logger.info("Beginning analysis of {}", directory);

            // Walk class files and compile a full list of available source
            AvailableSourceVisitor sourceVisitor = new AvailableSourceVisitor(ASM_API);
            Files.walkFileTree(directory, new BulkClassFileVisitor(sourceVisitor));

            Set<String> sourceClassNames = sourceVisitor.getSourceClassNames();

            sourceClassNames.stream()
            .forEach(name -> logger.debug("Found source class {}", name));

            Predicate<String> exclusionFilter = getExclusionFilter(externalApiClasses);

            sourceClassNames = sourceClassNames.stream()
                    .filter(exclusionFilter)
                    .collect(Collectors.toSet());

            sourceClassNames.stream()
            .forEach(name -> tracer.traceClassFeature(name, "(source file found)"));

            logger.info("{} source classes found to evaluate for uses within the code base", sourceClassNames.size());

            // Find references to known source files
            ReferencedClassVisitor referenceVisitor = new ReferencedClassVisitor(ASM_API, sourceClassNames, tracer);
            Files.walkFileTree(directory, new BulkClassFileVisitor(referenceVisitor));

            sourceClassNames.removeAll(referenceVisitor.getReferencedClasses());

            // If there are still dead classes, try AST parsing for constant references
            if (!sourceClassNames.isEmpty()) {
                logger.info(
                        "Classes not referenced by method found - running in-depth analysis for other reference types");

                Files.walkFileTree(directory, new BulkSourceFileVisitor(sourceClassNames, sourceClassNames::remove));
            }

            logger.info("Found {} classes with no detected references", sourceClassNames.size());

            return sourceClassNames;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Predicate<String> getExclusionFilter(Set<String> externalApiClasses) {
        Collection<PathMatcher> matchers = externalApiClasses.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());

        return (className -> !matchers.stream()
                .anyMatch(matcher -> matcher.matches(Paths.get(className.trim().toLowerCase()))));
    }

}

/*
 * Copyright (c) Dec 5, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.alloy.core.Preconditions;
import org.starchartlabs.helsing.core.asm.AsmUtils;
import org.starchartlabs.helsing.core.asm.AvailableClassVisitor;
import org.starchartlabs.helsing.core.asm.ClassFileVisitor;
import org.starchartlabs.helsing.core.asm.ReferenceClassVisitor;
import org.starchartlabs.helsing.core.ast.CompilationUnitVisitor;
import org.starchartlabs.helsing.core.ast.SourceFileVisitor;
import org.starchartlabs.helsing.core.model.ClassUseConsumer;

//TODO romeara
public class DeadClassAnalyzer {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Predicate<Path> externalApiFileFilter;

    private final Optional<String> traceClass;

    public DeadClassAnalyzer(Predicate<Path> externalApiFileFilter, @Nullable String traceClass) {
        this.externalApiFileFilter = Objects.requireNonNull(externalApiFileFilter);
        this.traceClass = Optional.ofNullable(traceClass);
    }

    public Set<String> findDeadClasses(Path directory, Predicate<Path> fileFilter) throws IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(fileFilter);

        Preconditions.checkArgument(Files.isDirectory(directory), "%s is not a directory", directory.toString());

        logger.info("Analyzing {} for dead classes", directory);

        // Find all available classes to evaluate as used or unused
        Set<String> unusedClasses = findAvailableClasses(directory, fileFilter);
        ClassUseConsumer consumer = new ClassUseConsumer(unusedClasses, traceClass.orElse(null));

        logger.info("Found {} classes to analyze", consumer.getUnusedClasses().size());

        // Process byte-code references within the directory
        consumer = removeStructuralUses(directory, fileFilter, consumer);

        // If classes remain without uses, do a more detailed source analysis for things like references to constants
        if (!consumer.getUnusedClasses().isEmpty()) {
            logger.info("{} classes are not referenced in ways detectable in byte-code - checking source",
                    consumer.getUnusedClasses().size());

            consumer = removeConstantReferences(directory, fileFilter, consumer);
        }

        logger.info("{} unused classes found", consumer.getUnusedClasses().size());

        return consumer.getUnusedClasses();
    }

    private Set<String> findAvailableClasses(Path directory, Predicate<Path> fileFilter) throws IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(fileFilter);

        // When finding classes to analyze, both include/exclude AND APIs intended for outside use need to be taken into
        // account
        Predicate<Path> includedApplicationClassesFilter = fileFilter.and(externalApiFileFilter);

        AvailableClassVisitor classVisitor = new AvailableClassVisitor(AsmUtils.ASM_API);

        // Traverse the class files of the given directory and determine which should be analyzed for uses
        Files.walkFileTree(directory, classVisitor.getFileVisitor(includedApplicationClassesFilter));

        return classVisitor.getClassNames();
    }

    private ClassUseConsumer removeStructuralUses(Path directory, Predicate<Path> fileFilter, ClassUseConsumer consumer)
            throws IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(fileFilter);
        Objects.requireNonNull(consumer);

        ReferenceClassVisitor classVisitor = new ReferenceClassVisitor(AsmUtils.ASM_API, consumer);
        ClassFileVisitor fileVisitor = new ClassFileVisitor(classVisitor, fileFilter);

        // Traverse the class files of the given directory and find bytecode-accessible references to relevant classes
        Files.walkFileTree(directory, fileVisitor);

        return consumer;
    }

    private ClassUseConsumer removeConstantReferences(Path directory, Predicate<Path> fileFilter,
            ClassUseConsumer consumer)
            throws IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(fileFilter);
        Objects.requireNonNull(consumer);

        CompilationUnitVisitor sourceVisitor = new CompilationUnitVisitor(consumer);
        SourceFileVisitor fileVisitor = new SourceFileVisitor(sourceVisitor, fileFilter);

        // Traverse the class files of the given directory and find source-accessible references to relevant classes
        Files.walkFileTree(directory, fileVisitor);

        return consumer;
    }

}

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
package org.starchartlabs.helsing.cli.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.kohsuke.args4j.Option;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.helsing.cli.impl.AvailableSourceVisitor;
import org.starchartlabs.helsing.cli.impl.BulkClassFileVisitor;
import org.starchartlabs.helsing.cli.impl.ClassUseTracer;
import org.starchartlabs.helsing.cli.impl.ReferencedClassVisitor;

//TODO romeara
public class DeadClassesCommand implements Runnable {

    public static final String COMMAND_NAME = "dead-classes";

    private static final int ASM_API = Opcodes.ASM7;

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Option(name = "-d", aliases = { "--directory" }, required = true,
            usage = "Specifies the directory containing the classes to evaluate. Required")
    private File directory;

    @Option(name = "-t", aliases = { "--trace" }, required = false,
            usage = "Specifies a specific class name to output tracing information for determination of dead/alive for. Optional")
    private String traceClassName;

    @Override
    public void run() {
        Tracer tracer = new Tracer(traceClassName, traceClassName);

        try {
            // Walk class files and compile a full list of available source
            AvailableSourceVisitor sourceVisitor = new AvailableSourceVisitor(ASM_API);
            Files.walkFileTree(directory.toPath(), new BulkClassFileVisitor(sourceVisitor));

            Set<String> sourceClassNames = sourceVisitor.getSourceClassNames();

            sourceClassNames.stream()
            .forEach(name -> logger.debug("Found source class {}", name));

            sourceClassNames.stream()
                    .forEach(name -> tracer.traceClassFeature(name, "(source file found)"));

            // Find references to known source files
            ReferencedClassVisitor referenceVisitor = new ReferencedClassVisitor(ASM_API, sourceClassNames, tracer);
            Files.walkFileTree(directory.toPath(), new BulkClassFileVisitor(referenceVisitor));

            sourceClassNames.removeAll(referenceVisitor.getReferencedClasses());

            sourceClassNames.forEach(name -> logger.info("No references found to class {}", name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Tracer implements ClassUseTracer {

        /** Logger reference to output information to the application log files */
        private final Logger logger = LoggerFactory.getLogger(getClass());

        // Class to trace when found, and what it contains
        @Nullable
        private final String structureTraceClass;

        // Class to trace discovered uses of
        @Nullable
        private final String useTraceClass;

        public Tracer(String structureTraceClass, String useTraceClass) {
            this.structureTraceClass = Optional.ofNullable(structureTraceClass)
                    .map(trace -> trace.replace('.', '/'))
                    .orElse(null);
            ;
                    this.useTraceClass = Optional.ofNullable(useTraceClass)
                            .map(trace -> trace.replace('.', '/'))
                            .orElse(null);
            ;
        }

        @Override
        public void traceClassFeature(String className, String feature) {
            if (Objects.equals(structureTraceClass, className)) {
                logger.info("[SOURCE] {}: {}", className, feature);
            }
        }

        @Override
        public void traceClassUse(String className, String usedIn) {
            if (Objects.equals(useTraceClass, className)) {
                logger.info("[USE] {}:{}", className, usedIn);
            }
        }

    }

}

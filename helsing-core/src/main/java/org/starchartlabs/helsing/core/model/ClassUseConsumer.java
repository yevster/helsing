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
package org.starchartlabs.helsing.core.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO romeara
public class ClassUseConsumer {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Set<String> qualifiedClassNames;

    private final Optional<String> traceLoggingClass;

    public ClassUseConsumer(Set<String> qualifiedClassNames, @Nullable String traceLoggingClass) {
        this.qualifiedClassNames = Objects.requireNonNull(qualifiedClassNames);
        this.traceLoggingClass = Optional.ofNullable(traceLoggingClass);
    }

    public void recordUsedClass(String usedClassName, String usingClass, String useContext) {
        Objects.requireNonNull(usedClassName);
        Objects.requireNonNull(usingClass);
        Objects.requireNonNull(useContext);

        qualifiedClassNames.remove(usedClassName);

        traceLoggingClass
        .filter(Predicate.isEqual(usedClassName))
        .ifPresent(a -> logger.info("{} used in: {} ({})", a, usingClass, useContext));

        traceLoggingClass
        .filter(Predicate.isEqual(usingClass))
        .ifPresent(a -> logger.info("{} uses: {} ({})", a, usedClassName, useContext));
    }

    public void recordClassTracing(String className, Supplier<String> traceSupplier) {
        Objects.requireNonNull(className);
        Objects.requireNonNull(traceSupplier);

        traceLoggingClass
        .filter(Predicate.isEqual(className))
        .ifPresent(a -> logger.info("Tracing for {}: {}", a, traceSupplier.get()));
    }

    public void recordInvalidFile(Path file, String reasonInvalid) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(reasonInvalid);

        logger.warn("Skipping processing of invalid file {} ({})", file.toString(), reasonInvalid);
    }

    public Set<String> getUnusedClasses() {
        return qualifiedClassNames;
    }

}

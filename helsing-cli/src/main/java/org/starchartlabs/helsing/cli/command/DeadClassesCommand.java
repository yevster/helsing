/*
 * Copyright (c) Nov 12, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.cli.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.helsing.core.DeadClassAnalyzer;

// TODO romeara
public class DeadClassesCommand implements Runnable {

    public static final String COMMAND_NAME = "dead-classes";

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Option(name = "-d", aliases = { "--directory" }, required = true,
            usage = "Specifies the directory containing the classes to evaluate. Required")
    private File directory;

    @Option(name = "-x", aliases = { "--external" }, handler = StringArrayOptionHandler.class, required = false,
            usage = "Specifies any classes which are intended for use outside the current context and should not be marked as 'dead'")
    private String[] externalApiPatterns;

    @Option(name = "-e", aliases = { "--exclude" }, handler = StringArrayOptionHandler.class, required = false,
            usage = "Specifies any classes which should be completely ignored for analysis")
    private String[] excludePatterns;

    @Option(name = "-t", aliases = {
    "--trace" }, required = false,
            usage = "Specifies a specific class name to output tracing information for determination of dead/alive for. Optional")
    private String traceClassName;

    @Override
    public void run() {
        DeadClassAnalyzer analyzer = new DeadClassAnalyzer(getExclusionFilter(), traceClassName);

        try {
            Set<String> deadClassNames = analyzer.findDeadClasses(directory.toPath(), getExcludeFilter());

            logger.info("Found {} classes with no detected references", deadClassNames.size());

            deadClassNames.stream()
            .sorted()
            .forEach(name -> logger.info("No references found to class {}", name));
        } catch (IOException e) {
            throw new RuntimeException("Error accessing files for analysis", e);
        }
    }

    private Predicate<Path> getExclusionFilter() {
        Set<String> externalApiClasses = Stream.of(Optional.ofNullable(externalApiPatterns).orElse(new String[0]))
                .map(className -> className.replace('.', '/'))
                .map(className -> className.startsWith("**/") || className.startsWith("/") ? className
                        : "**/" + className)
                .map(className -> className.endsWith("/*") ? className + "*" : className)
                .collect(Collectors.toSet());

        externalApiClasses.forEach(pattern -> logger.info("External API match pattern provided: '{}'", pattern));

        Collection<PathMatcher> matchers = externalApiClasses.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());

        return (filePath -> !matchers.stream().anyMatch(matcher -> matcher.matches(filePath)));
    }

    private Predicate<Path> getExcludeFilter() {
        Set<String> excludedClasses = Stream.of(Optional.ofNullable(excludePatterns).orElse(new String[0]))
                .map(className -> className.replace('.', '/'))
                .map(className -> className.startsWith("**/") || className.startsWith("/") ? className
                        : "**/" + className)
                .map(className -> className.endsWith("/*") ? className + "*" : className)
                .collect(Collectors.toSet());

        excludedClasses.forEach(pattern -> logger.info("Exclusion match pattern provided: '{}'", pattern));

        Collection<PathMatcher> matchers = excludedClasses.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());

        return (filePath -> !matchers.stream().anyMatch(matcher -> matcher.matches(filePath)));
    }

}

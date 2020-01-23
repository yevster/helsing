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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.helsing.core.DeadClassAnalyzer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// TODO romeara
@Command(
        description = "Analyzes files within an application and determines which, if any, may not be currently referenced within the available source",
        name = DeadClassCandidatesCommand.COMMAND_NAME, mixinStandardHelpOptions = true)
public class DeadClassCandidatesCommand implements Runnable {

    public static final String COMMAND_NAME = "dead-class-candidates";

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Option(names = { "-d", "--directory" }, required = true,
            description = "Specifies the directory containing the classes to evaluate. Required")
    private File directory;

    @Option(names = { "-x", "--external" }, required = false,
            description = "Specifies any classes which are intended for use outside the current context and should not be marked as 'dead'")
    private String[] externalApiPatterns;

    @Option(names = { "-i", "--include" }, required = false,
            description = "Specifies any class names to limit analysis to. Applied before any exclude patterns")
    private String[] includePatterns;

    @Option(names = { "-e", "--exclude" }, required = false,
            description = "Specifies any classes which should be completely ignored for analysis. Applied after any include patterns")
    private String[] excludePatterns;

    @Option(names = { "-t", "--trace" }, required = false,
            description = "Specifies a specific class name to output tracing information for determination of dead/alive for. Optional")
    private String traceClassName;

    @Override
    public void run() {
        DeadClassAnalyzer analyzer = new DeadClassAnalyzer(getExclusionFilter(), traceClassName);

        try {
            Predicate<Path> analyzedClassesFilter = getIncludeFilter().and(getExcludeFilter());

            Set<String> deadClassNames = analyzer.findDeadClasses(directory.toPath(), analyzedClassesFilter);

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
                .map(className -> className.endsWith("/*") ? className + "*" : className + ".class")
                .collect(Collectors.toSet());

        externalApiClasses.forEach(pattern -> logger.info("External API match pattern provided: '{}'", pattern));

        Collection<PathMatcher> matchers = externalApiClasses.stream()
                .map(String::trim)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());

        return (filePath -> !matchers.stream().anyMatch(matcher -> matcher.matches(filePath)));
    }

    private Predicate<Path> getIncludeFilter() {
        Set<String> classFileNamePatterns = toClassPatterns(includePatterns);

        classFileNamePatterns.forEach(pattern -> logger.info("Inclusion match pattern provided: '{}'", pattern));

        Collection<PathMatcher> matchers = classFileNamePatterns.stream()
                .map(String::trim)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());

        return (filePath -> matchers.stream().anyMatch(matcher -> matcher.matches(filePath)));
    }

    private Predicate<Path> getExcludeFilter() {
        Set<String> classFileNamePatterns = toClassPatterns(excludePatterns);

        classFileNamePatterns.forEach(pattern -> logger.info("Exclusion match pattern provided: '{}'", pattern));

        Collection<PathMatcher> matchers = classFileNamePatterns.stream()
                .map(String::trim)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());

        return (filePath -> !matchers.stream().anyMatch(matcher -> matcher.matches(filePath)));
    }

    private Set<String> toClassPatterns(String[] patterns) {
        return Stream.of(Optional.ofNullable(patterns).orElse(new String[0]))
                .map(className -> className.replace('.', '/'))
                .map(className -> className.startsWith("**/") || className.startsWith("/") ? className
                        : "**/" + className)
                .map(className -> className.endsWith("/*") ? className + "*" : className + ".class")
                .collect(Collectors.toSet());
    }

}

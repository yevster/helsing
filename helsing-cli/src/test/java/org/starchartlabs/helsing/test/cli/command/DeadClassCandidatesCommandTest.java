/*
 * Copyright (c) Feb 11, 2020 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.test.cli.command;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.starchartlabs.helsing.cli.CommandLineInterface;
import org.starchartlabs.helsing.cli.command.DeadClassCandidatesCommand;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import uk.org.lidalia.slf4jext.Level;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

public class DeadClassCandidatesCommandTest {

    private static final Path TEST_PROJECT_DIRECTORY = Paths
            .get(System.getProperty("org.starchartlabs.helsing.test.project.dir"));

    private static final List<String> CLASS_NAMES = new ArrayList<>();

    static {
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.ClassAnnotation");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.DeadClass");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.FieldAnnotation");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.MethodAnnotation");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedByAnnotationConstantFullName");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedByAnnotationConstantSimpleName");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedByConstantFullName");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedByConstantSimpleName");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedViaExtension");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedViaSimpleMethod");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsedViaStaticMethod");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.UsesOtherClasses");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.other.UsedByConstantFullName");
        CLASS_NAMES.add("org.starchartlabs.helsing.test.project.other.UsedByConstantImported");
    }

    private final TestLogger testLogger = TestLoggerFactory.getTestLogger(DeadClassCandidatesCommand.class);

    @BeforeMethod
    public void resetTestLogger() {
        testLogger.clear();
    }

    @Test
    public void basicAnalysis() throws Exception {
        List<String> expectedDeadClasses = Arrays.asList(
                "org.starchartlabs.helsing.test.project.DeadClass",
                "org.starchartlabs.helsing.test.project.UsesOtherClasses");

        String[] args = new String[] { DeadClassCandidatesCommand.COMMAND_NAME, "--directory=" + TEST_PROJECT_DIRECTORY };

        try {
            CommandLineInterface.main(args);
        } finally {
            // Expect the known dead class, and external API class (not marked as such), to be marked as dead
            List<String> events = testLogger.getLoggingEvents().stream()
                    .filter(event -> Level.INFO.equals(event.getLevel()))
                    .map(event -> MessageFormatter.arrayFormat(event.getMessage(), event.getArguments().toArray()))
                    .map(FormattingTuple::getMessage)
                    .collect(Collectors.toList());

            Assert.assertTrue(events.contains("Found 2 classes with no detected references"),
                    "Expected 2 classes marked as dead " + events);

            CLASS_NAMES.stream()
            .filter(a -> !expectedDeadClasses.contains(a))
            .forEach(className -> {
                Assert.assertFalse(events.contains("No references found to class " + className),
                        "Did not expext class " + className + " to be marked as dead " + events);
            });

            CLASS_NAMES.stream()
            .filter(expectedDeadClasses::contains)
            .forEach(className -> {
                Assert.assertTrue(events.contains("No references found to class " + className),
                        "Expect class " + className + " to be marked as dead " + events);
            });
        }
    }

    @Test
    public void withIncludeFilter() throws Exception {
        List<String> expectedDeadClasses = Arrays.asList(
                "org.starchartlabs.helsing.test.project.other.UsedByConstantFullName",
                "org.starchartlabs.helsing.test.project.other.UsedByConstantImported");

        String[] args = new String[] { DeadClassCandidatesCommand.COMMAND_NAME, "--directory=" + TEST_PROJECT_DIRECTORY,
        "--include=org.starchartlabs.helsing.test.project.other.*" };

        try {
            CommandLineInterface.main(args);
        } finally {
            // Expect both classes, as the class that uses them is not included in the analysis
            List<String> events = testLogger.getLoggingEvents().stream()
                    .filter(event -> Level.INFO.equals(event.getLevel()))
                    .map(event -> MessageFormatter.arrayFormat(event.getMessage(), event.getArguments().toArray()))
                    .map(FormattingTuple::getMessage)
                    .collect(Collectors.toList());

            Assert.assertTrue(events.contains("Found 2 classes with no detected references"),
                    "Expected 2 classes marked as dead " + events);

            CLASS_NAMES.stream()
            .filter(a -> !expectedDeadClasses.contains(a))
            .forEach(className -> {
                Assert.assertFalse(events.contains("No references found to class " + className),
                        "Did not expext class " + className + " to be marked as dead " + events);
            });

            CLASS_NAMES.stream()
            .filter(expectedDeadClasses::contains)
            .forEach(className -> {
                Assert.assertTrue(events.contains("No references found to class " + className),
                        "Expect class " + className + " to be marked as dead " + events);
            });
        }
    }

    @Test
    public void withExcludeFilter() throws Exception {
        List<String> expectedDeadClasses = Arrays.asList(
                "org.starchartlabs.helsing.test.project.UsesOtherClasses");

        String[] args = new String[] { DeadClassCandidatesCommand.COMMAND_NAME, "--directory=" + TEST_PROJECT_DIRECTORY,
        "--exclude=**.DeadClass" };

        try {
            CommandLineInterface.main(args);
        } finally {
            // Expect the external API class (not marked as such), to be marked as dead - known dead class should not
            // be, as it was excluded
            List<String> events = testLogger.getLoggingEvents().stream()
                    .filter(event -> Level.INFO.equals(event.getLevel()))
                    .map(event -> MessageFormatter.arrayFormat(event.getMessage(), event.getArguments().toArray()))
                    .map(FormattingTuple::getMessage)
                    .collect(Collectors.toList());

            Assert.assertTrue(events.contains("Found 1 classes with no detected references"),
                    "Expected 1 classes marked as dead " + events);

            CLASS_NAMES.stream()
            .filter(a -> !expectedDeadClasses.contains(a))
            .forEach(className -> {
                Assert.assertFalse(events.contains("No references found to class " + className),
                        "Did not expext class " + className + " to be marked as dead " + events);
            });

            CLASS_NAMES.stream()
            .filter(expectedDeadClasses::contains)
            .forEach(className -> {
                Assert.assertTrue(events.contains("No references found to class " + className),
                        "Expect class " + className + " to be marked as dead " + events);
            });
        }
    }

    @Test
    public void withExternalApi() throws Exception {
        List<String> expectedDeadClasses = Arrays.asList("org.starchartlabs.helsing.test.project.DeadClass");

        String[] args = new String[] { DeadClassCandidatesCommand.COMMAND_NAME, "--directory=" + TEST_PROJECT_DIRECTORY,
        "--external=org.starchartlabs.helsing.test.project.UsesOtherClasses" };

        try {
            CommandLineInterface.main(args);
        } finally {
            // Expect the known dead class - external API class is marked as such
            List<String> events = testLogger.getLoggingEvents().stream()
                    .filter(event -> Level.INFO.equals(event.getLevel()))
                    .map(event -> MessageFormatter.arrayFormat(event.getMessage(), event.getArguments().toArray()))
                    .map(FormattingTuple::getMessage)
                    .collect(Collectors.toList());

            Assert.assertTrue(events.contains("Found 1 classes with no detected references"),
                    "Expected 1 classes marked as dead " + events);

            CLASS_NAMES.stream()
            .filter(a -> !expectedDeadClasses.contains(a))
            .forEach(className -> {
                Assert.assertFalse(events.contains("No references found to class " + className),
                        "Did not expext class " + className + " to be marked as dead " + events);
            });

            CLASS_NAMES.stream()
            .filter(expectedDeadClasses::contains)
            .forEach(className -> {
                Assert.assertTrue(events.contains("No references found to class " + className),
                        "Expect class " + className + " to be marked as dead " + events);
            });
        }
    }

}

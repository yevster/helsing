/*
 * Copyright (c) Feb 3, 2020 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.test.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.starchartlabs.helsing.core.DeadClassAnalyzer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests detection of dead classes and used classes via byte-code analysis
 *
 * <p>
 * Utilizes the test project helsing-test-project within the code base as a target project-to-scan. It is expected that
 * dependency resolution is configured such that that project is built prior to this test being run
 *
 * <p>
 * This test also requires that the property {@code org.starchartlabs.helsing.test.project.dir} is set to the local path
 * for the helsing-test-project. Within Gradle this is done automatically - within IDE configuration, TestNG's run/debug
 * options must be set to provide it as a system property
 *
 * @author romeara
 */
public class DeadClassAnalyzerTest {

    private static final Path TEST_PROJECT_DIRECTORY = Paths
            .get(System.getProperty("org.starchartlabs.helsing.test.project.dir"));

    private Set<String> analysisResult;

    @BeforeClass
    public void performAnalysis() throws Exception {
        // Analyzes the helsing-test-project compiled/source code to determine dead class results
        // The results of analysis are they validated for individual expected properties by each individual test
        DeadClassAnalyzer analyzer = new DeadClassAnalyzer(a -> true, null);

        analysisResult = analyzer.findDeadClasses(TEST_PROJECT_DIRECTORY, a -> true);

        Assert.assertNotNull(analysisResult);
    }

    // There is a fully-unused class in the test set which should be detected
    @Test
    public void foundDeadClass() throws Exception {
        Assert.assertTrue(analysisResult.contains("org.starchartlabs.helsing.test.project.DeadClass"),
                "Expected known dead class to be detected");
    }

    // There is a class which uses others, but is not used itself - without any setup to mark this as "external", it
    // should be marked
    @Test
    public void foundExternalApi() throws Exception {
        Assert.assertTrue(analysisResult.contains("org.starchartlabs.helsing.test.project.UsesOtherClasses"),
                "Expected consuming class to be detected as dead in the abscence of external API configuration");
    }

    @Test
    public void foundUseViaSimpleMethods() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.UsedViaSimpleMethod"),
                "Expected class used via simple method calls to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaStaticMethods() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.UsedViaStaticMethod"),
                "Expected class used via static method calls to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaExtension() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.UsedViaExtension"),
                "Expected class used via extension to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseByClassName() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.UsedByClassName"),
                "Expected class used via class object reference to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaClassAnnotation() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.ClassAnnotation"),
                "Expected class used via annotation on class to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaMethodAnnotation() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.MethodAnnotation"),
                "Expected class used via annotation on method to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaFieldAnnotation() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.FieldAnnotation"),
                "Expected class used via annotation on field to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaConstantSamePackageSimpleName() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.UsedByConstantSimpleName"),
                "Expected class used via constant in same package to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaConstantSamePackageQualifiedName() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.UsedByConstantFullName"),
                "Expected class used via constant in same package with qualified name to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaConstantSamePackageQualifiedNameInMethod() throws Exception {
        Assert.assertFalse(
                analysisResult.contains("org.starchartlabs.helsing.test.project.UsedByConstantFullNameInMethod"),
                "Expected class used via constant in same package with qualified name in method to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaAnnotationConstantSamePackageSimpleName() throws Exception {
        Assert.assertFalse(
                analysisResult.contains("org.starchartlabs.helsing.test.project.UsedByAnnotationConstantSimpleName"),
                "Expected class used via constant in annoation in same package to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaAnnotationConstantSamePackageQualifiedName() throws Exception {
        Assert.assertFalse(
                analysisResult.contains("org.starchartlabs.helsing.test.project.UsedByAnnotationConstantFullName"),
                "Expected class used via constant in annoation in same package to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaConstantDifferentPackageImported() throws Exception {
        Assert.assertFalse(
                analysisResult.contains("org.starchartlabs.helsing.test.project.other.UsedByConstantImported"),
                "Expected class used via constant and imported to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseViaConstantDifferentPackageQualifiedName() throws Exception {
        Assert.assertFalse(
                analysisResult.contains("org.starchartlabs.helsing.test.project.other.UsedByConstantFullName"),
                "Expected class used via constant in different package with qualified name to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

    @Test
    public void foundUseByClassNameDifferentPackageQualifiedName() throws Exception {
        Assert.assertFalse(analysisResult.contains("org.starchartlabs.helsing.test.project.other.UsedByClassName"),
                "Expected class used via class object reference in different package to not be marked as dead (should be used by test class UsesOtherClasses)");
    }

}

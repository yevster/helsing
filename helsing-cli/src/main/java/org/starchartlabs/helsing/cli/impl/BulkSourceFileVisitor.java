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
package org.starchartlabs.helsing.cli.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

//TODO romeara
public class BulkSourceFileVisitor extends SimpleFileVisitor<Path> {

    private final Set<String> sourceClasses;

    private final Consumer<String> foundClassConsumer;

    public BulkSourceFileVisitor(Set<String> sourceClasses, Consumer<String> foundClassConsumer) {
        this.sourceClasses = new HashSet<>(Objects.requireNonNull(sourceClasses));
        this.foundClassConsumer = Objects.requireNonNull(foundClassConsumer);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);

        if (!sourceClasses.isEmpty()) {
            if (file.toString().matches(".*\\.java")) {
                String contents = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                        .collect(Collectors.joining("\n"));

                processSourceFile(contents);
            }
        }

        return FileVisitResult.CONTINUE;
    }

    private void processSourceFile(String contents) {
        CompilationUnit compilationUnit = StaticJavaParser.parse(contents);

        Collection<String> directImports = compilationUnit.getImports().stream()
                .map(this::getDirectlyImported)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        sourceClasses.removeAll(directImports);
        directImports.forEach(foundClassConsumer);

        Collection<String> allowedGenerics = getAllowedGeneralReferences(compilationUnit);

        if (!allowedGenerics.isEmpty()) {
            for (String allowedGeneric : allowedGenerics) {
                Pattern pattern = Pattern.compile(".*[^a-zA-Z0-9]" + getSimpleName(allowedGeneric) + "[^a-zA-Z0-9].*",
                        Pattern.DOTALL);

                if (pattern.matcher(contents).matches()) {
                    sourceClasses.remove(allowedGeneric);
                    foundClassConsumer.accept(allowedGeneric);
                }
            }
        }
    }

    private Collection<String> getDirectlyImported(ImportDeclaration importStatement) {
        Collection<String> directlyImportedSource = new HashSet<>();

        String statement = importStatement.getNameAsString();

        if (importStatement.isStatic()) {
            directlyImportedSource = sourceClasses.stream()
                    .filter(sourceClass -> statement.startsWith(toDot(sourceClass)))
                    .collect(Collectors.toSet());
        } else {
            directlyImportedSource = sourceClasses.stream()
                    .filter(sourceClass -> statement.equals(toDot(sourceClass)))
                    .collect(Collectors.toSet());
        }

        return directlyImportedSource;
    }

    private Collection<String> getAllowedGeneralReferences(CompilationUnit compilationUnit) {
        Set<String> definedTypes = compilationUnit.getTypes().stream()
                .map(TypeDeclaration::getFullyQualifiedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        String packageName = compilationUnit.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");

        Collection<String> generalImports = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .map(statement -> statement.endsWith(".*") ? statement.substring(0, statement.length() - 2) : statement)
                .collect(Collectors.toSet());

        return sourceClasses.stream()
                .filter(sourceClass -> !definedTypes.stream().anyMatch(t -> toDot(sourceClass).startsWith(t)))
                .filter(sourceClass -> toDot(sourceClass).startsWith(packageName)
                        || generalImports.stream().anyMatch(i -> toDot(sourceClass).startsWith(i)))
                .collect(Collectors.toSet());
    }

    private String getSimpleName(String sourceClass) {
        String[] elements = toDot(sourceClass).split("\\.");

        return elements[elements.length - 1];
    }

    // TODO standardize higher-up on dot notation
    private String toDot(String sourceClass) {
        return sourceClass.replace('/', '.').replace('$', '.');
    }

}

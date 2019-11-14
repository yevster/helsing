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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.starchartlabs.alloy.core.Strings;

// TODO romeara
public class ReferencedClassVisitor extends ClassVisitor {

    private static final Pattern ARRAY_PATTERN = Pattern.compile("^(\\[)*L(.*);");

    private final Set<String> sourceClassNames;

    private final Set<String> referencedClasses;

    private final ClassUseTracer classUseTracer;

    private String currentClassName;

    public ReferencedClassVisitor(int api, Set<String> sourceClassNames, ClassUseTracer classUseTracer) {
        super(api);

        this.sourceClassNames = Objects.requireNonNull(sourceClassNames);
        this.classUseTracer = Objects.requireNonNull(classUseTracer);

        referencedClasses = new HashSet<>();
    }

    public int getAsmApi() {
        return api;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        currentClassName = name;

        registerCalledClass(superName, "Super of " + name);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerCalledClass(annotationType.getInternalName(), "type annotation");

        // TODO visitor to annotation values
        return null;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerCalledClass(annotationType.getInternalName(), "annotation");

        // TODO visitor to annotation values
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        Type fieldType = Type.getType(descriptor);

        registerCalledClass(fieldType.getInternalName(), "class field");

        // TODO visitor to annotations
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Register types for returns and arguments
        Type methodType = Type.getMethodType(desc);

        registerCalledClass(methodType.getReturnType().getInternalName(), name + "(declared method return)");

        Stream.of(methodType.getArgumentTypes())
        .map(Type::getInternalName)
        .forEach(argumentType -> registerCalledClass(argumentType, name + "(declared method argument)"));

        // Submit for tracing
        String argumentTypes = Stream.of(methodType.getArgumentTypes())
                .map(Type::getInternalName)
                .collect(Collectors.joining(","));

        String feature = Strings.format("(%s:%s:[%s])", name, methodType.getReturnType().getInternalName(),
                argumentTypes);

        classUseTracer.traceClassFeature(currentClassName, feature);

        return new ReferencedMethodVisitor(
                getAsmApi(),
                currentClassName,
                (className, methodContext) -> registerCalledClass(className, methodContext));
    }

    @Override
    public void visitEnd() {
        currentClassName = null;
    }

    public Set<String> getReferencedClasses() {
        return referencedClasses;
    }

    private void registerCalledClass(String className, String traceContext) {
        String effectiveClassName = getEffectiveClassName(className);

        // Note: references within the class do not count, as a class referencing itself does not mean it is externally
        // consumed
        if (!Objects.equals(currentClassName, effectiveClassName) && sourceClassNames.contains(effectiveClassName)) {
            referencedClasses.add(effectiveClassName);

            classUseTracer.traceClassUse(effectiveClassName, String.format("(%s)", traceContext));
        }
    }

    private String getEffectiveClassName(String className) {
        Matcher matcher = ARRAY_PATTERN.matcher(className);

        String effectiveClassName = className;

        // Handle arrays of class names - an array is still a reference to that class
        if (matcher.matches()) {
            effectiveClassName = matcher.group(2);
        }

        return effectiveClassName;
    }

    private class ReferencedMethodVisitor extends MethodVisitor {

        private final String currentClassName;

        private final BiConsumer<String, String> classNameConsumer;

        private int currentLine;

        public ReferencedMethodVisitor(int api, String currentClassName, BiConsumer<String, String> classNameConsumer) {
            super(api);

            this.currentClassName = Objects.requireNonNull(currentClassName);
            this.classNameConsumer = Objects.requireNonNull(classNameConsumer);
            currentLine = 0;
        }

        @Override
        public void visitLineNumber(final int line, final Label start) {
            currentLine = line;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            registerMethod(owner, name, descriptor);
        }

        @Override
        public void visitInvokeDynamicInsn(
                final String name,
                final String descriptor,
                final Handle bootstrapMethodHandle,
                final Object... bootstrapMethodArguments) {
            classNameConsumer.accept(bootstrapMethodHandle.getOwner(),
                    getFullContext(bootstrapMethodHandle.getName(), "dynamic method handle"));

            for (Object methodArgument : bootstrapMethodArguments) {
                registerMethodArgument(methodArgument);
            }
        }

        @Override
        public void visitTypeInsn(final int opcode, final String type) {
            classNameConsumer.accept(type, getFullContext(null, "type instruction"));
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
            Type type = Type.getType(descriptor);

            classNameConsumer.accept(type.getInternalName(), getFullContext(null, "field instruction"));
        }

        private void registerMethod(String owner, String name, String descriptor) {
            Type methodType = Type.getMethodType(descriptor);

            classNameConsumer.accept(methodType.getReturnType().getInternalName(),
                    getFullContext(name, "method return"));

            for (Type methodArgument : methodType.getArgumentTypes()) {
                classNameConsumer.accept(methodArgument.getInternalName(), getFullContext(name, "method argument"));
            }

            classNameConsumer.accept(owner, getFullContext(name, "method call"));
        }

        private void registerMethodArgument(Object methodArgument) {
            if (methodArgument instanceof Type) {
                Type typeArgument = (Type) methodArgument;

                if (typeArgument.getSort() != Type.METHOD) {
                    classNameConsumer.accept(typeArgument.getInternalName(),
                            getFullContext(null, "dynamic method argument type"));
                }
            } else if (methodArgument instanceof Handle) {
                Handle handleArgument = (Handle) methodArgument;

                registerMethod(handleArgument.getOwner(), handleArgument.getName(), handleArgument.getDesc());
            } else if (methodArgument instanceof ConstantDynamic) {
                ConstantDynamic constantDyanmicArgument = (ConstantDynamic) methodArgument;

                registerMethod(constantDyanmicArgument.getBootstrapMethod().getOwner(),
                        constantDyanmicArgument.getBootstrapMethod().getName(),
                        constantDyanmicArgument.getBootstrapMethod().getDesc());

                for (int i = 0; i < constantDyanmicArgument.getBootstrapMethodArgumentCount(); i++) {
                    registerMethodArgument(constantDyanmicArgument.getBootstrapMethodArgument(i));
                }
            }
        }

        private String getFullContext(@Nullable String methodName, String context) {
            String method = (methodName != null ? ":" + methodName : "");

            return Strings.format("%s%s (%s)[%s]", currentClassName, method, context, currentLine);
        }

    }

}

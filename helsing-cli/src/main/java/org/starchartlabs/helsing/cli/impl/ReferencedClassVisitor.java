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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO romeara
public class ReferencedClassVisitor extends ClassVisitor {

    private static final Pattern ARRAY_PATTERN = Pattern.compile("^(\\[)*L(.*);");

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Set<String> sourceClassNames;

    private final Set<String> referencedClasses;

    @Nullable
    private final String directoryStyleTrace;

    private String currentClassName;

    public ReferencedClassVisitor(int api, Set<String> sourceClassNames, String directoryStyleTrace) {
        super(api);

        this.sourceClassNames = Objects.requireNonNull(sourceClassNames);
        this.directoryStyleTrace = directoryStyleTrace;

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
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Register types for returns and arguments
        Type methodType = Type.getMethodType(desc);

        registerCalledClass(methodType.getReturnType().getInternalName(), name + "(declared method return)");

        Stream.of(methodType.getArgumentTypes())
        .map(Type::getInternalName)
        .forEach(argumentType -> registerCalledClass(argumentType, name + "(declared method argument)"));

        if (Objects.equals(currentClassName, directoryStyleTrace)) {
            String argumentTypes = Stream.of(methodType.getArgumentTypes())
                    .map(Type::getInternalName)
                    .collect(Collectors.joining(","));

            logger.info("[CLASS TRACE] Found method in class {} ({}:{}:[{}])", currentClassName, name,
                    methodType.getReturnType().getInternalName(), argumentTypes);
        }

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

        if (!Objects.equals(currentClassName, effectiveClassName) && sourceClassNames.contains(effectiveClassName)) {
            referencedClasses.add(effectiveClassName);

            if (Objects.equals(effectiveClassName, directoryStyleTrace)) {
                logger.info("[CLASS TRACE] Found use of class {} ({})", effectiveClassName, traceContext);
            }
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
            // TODO romeara generalize with above?
            Type methodType = Type.getMethodType(descriptor);

            registerUse(methodType.getReturnType().getInternalName(), name, "method return");

            for (Type methodArgument : methodType.getArgumentTypes()) {
                registerUse(methodArgument.getInternalName(), name, "method argument");
            }

            registerUse(owner, name, "method call");
        }

        @Override
        public void visitInvokeDynamicInsn(
                final String name,
                final String descriptor,
                final Handle bootstrapMethodHandle,
                final Object... bootstrapMethodArguments) {
            registerUse(bootstrapMethodHandle.getOwner(), name, "dynamic method handle");

            for (Object methodArgument : bootstrapMethodArguments) {
                if (methodArgument instanceof Type) {
                    Type typeArgument = (Type) methodArgument;

                    if (typeArgument.getSort() != Type.METHOD) {
                        registerUse(typeArgument.getInternalName(), null, "dynamic method argument type");
                    }
                } else if (methodArgument instanceof Handle) {
                    Handle handleArgument = (Handle) methodArgument;

                    registerUse(handleArgument.getOwner(), handleArgument.getName(), "dynamic method argument handle");

                    // TODO arguments and such of method handle?
                } else if (methodArgument instanceof ConstantDynamic) {
                    ConstantDynamic constantDyanmicArgument = (ConstantDynamic) methodArgument;

                    registerUse(constantDyanmicArgument.getBootstrapMethod().getOwner(), name,
                            "dynamic argument constant");

                    // TODO arguments of the constant-dynamic?
                }
            }
        }

        @Override
        public void visitTypeInsn(final int opcode, final String type) {
            registerUse(type, null, "type instruction");
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
            Type type = Type.getType(descriptor);

            registerUse(type.getInternalName(), null, "field instruction");
        }

        private void registerUse(String className, @Nullable String methodName, String context) {
            // Note: references within the class do not count, as a class referencing itself does not mean it is
            // externally consumed
            classNameConsumer.accept(className, currentClassName + (methodName != null ? ":" + methodName : "") + " ("
                    + context + ")[" + currentLine + "]");
        }
    }

}

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
//TODO romeara why doesn't this find classes exclusively used as field-references (enum values), or
// return/arguments (no method invokes on the class)
public class ReferencedClassVisitor extends ClassVisitor {

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

        registerCalledClass(methodType.getReturnType().getClassName(), name + "(declared method return)");

        for (Type methodArgument : methodType.getArgumentTypes()) {
            registerCalledClass(methodArgument.getClassName(), name + "(declared method argument)");
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
        if (!Objects.equals(currentClassName, className) && sourceClassNames.contains(className)) {
            referencedClasses.add(className);

            if (Objects.equals(className, directoryStyleTrace)) {
                logger.info("[CLASS TRACE] Found use of class {} ({})", className, traceContext);
            }
        }
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

            registerUse(methodType.getReturnType().getClassName(), name, "method return");

            for (Type methodArgument : methodType.getArgumentTypes()) {
                registerUse(methodArgument.getClassName(), name, "method argument");
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
                        registerUse(typeArgument.getClassName(), name, "dynamic method argument type");
                    }
                } else if (methodArgument instanceof Handle) {
                    Handle handleArgument = (Handle) methodArgument;

                    registerUse(handleArgument.getOwner(), name, "dynamic method argument handle");
                } else if (methodArgument instanceof ConstantDynamic) {
                    ConstantDynamic constantDyanmicArgument = (ConstantDynamic) methodArgument;

                    registerUse(constantDyanmicArgument.getBootstrapMethod().getOwner(), name,
                            "dynamic argument constant");

                    // TODO arguments of the constant-dynamic?
                }
            }
        }

        private void registerUse(String className, String methodName, String context) {
            // Note: references within the class do not count, as a class referencing itself does not mean it is
            // externally consumed
            if (!Objects.equals(currentClassName, className)) {
                classNameConsumer.accept(className,
                        currentClassName + ":" + methodName + " (" + context + ")[" + currentLine + "]");
            }
        }
    }

}

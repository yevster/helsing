/*
 * Copyright (c) Dec 10, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.asm;

import java.util.Objects;

import javax.annotation.Nullable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.starchartlabs.alloy.core.Strings;
import org.starchartlabs.helsing.core.model.ClassUseConsumer;

//TODO romeara
public class ReferenceMethodVisitor extends MethodVisitor {

    private final String currentClassName;

    // classname/how-used
    private final ClassUseConsumer referenceConsumer;

    private Integer currentLine;

    public ReferenceMethodVisitor(int api, String currentClassName, ClassUseConsumer referenceConsumer) {
        this(api, currentClassName, referenceConsumer, null);
    }

    public ReferenceMethodVisitor(int api, String currentClassName, ClassUseConsumer referenceConsumer,
            @Nullable MethodVisitor methodVisitor) {
        super(api, methodVisitor);

        this.currentClassName = Objects.requireNonNull(currentClassName);
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);

        currentLine = null;
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
        registerUsedClass(bootstrapMethodHandle.getOwner(),
                getFullContext(bootstrapMethodHandle.getName(), "dynamic method handle"));

        for (Object methodArgument : bootstrapMethodArguments) {
            registerMethodArgument(methodArgument);
        }
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        registerUsedClass(type, getFullContext(null, "type instruction"));
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
        Type type = Type.getType(descriptor);

        registerUsedClass(type.getInternalName(), getFullContext(null, "field instruction"));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), "annotation");

        AnnotationVisitor superVisitor = super.visitAnnotation(descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor,
            boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), "type annotation");

        AnnotationVisitor superVisitor = super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), "parameter annotation");

        AnnotationVisitor superVisitor = super.visitParameterAnnotation(parameter, descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start,
            Label[] end, int[] index, String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), "local variable annotation");

        AnnotationVisitor superVisitor = super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index,
                descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    private int getAsmApi() {
        return api;
    }

    private void registerUsedClass(String internalClassName, String whereUsed) {
        Objects.requireNonNull(internalClassName);
        Objects.requireNonNull(whereUsed);

        // TODO log ignored self uses?
        // Referencing yourself doesn't count as a use
        String usedClassName = AsmUtils.toExternalName(internalClassName);

        if (!Objects.equals(currentClassName, usedClassName)) {
            referenceConsumer.recordUsedClass(AsmUtils.toExternalName(internalClassName), currentClassName, whereUsed);
        }
    }

    private void registerMethod(String owner, String name, String descriptor) {
        Type methodType = Type.getMethodType(descriptor);

        registerUsedClass(methodType.getReturnType().getInternalName(), getFullContext(name, "method return"));

        for (Type methodArgument : methodType.getArgumentTypes()) {
            registerUsedClass(methodArgument.getInternalName(), getFullContext(name, "method argument"));
        }

        registerUsedClass(owner, getFullContext(name, "method call"));
    }

    private void registerMethodArgument(Object methodArgument) {
        if (methodArgument instanceof Type) {
            Type typeArgument = (Type) methodArgument;

            if (typeArgument.getSort() != Type.METHOD) {
                registerUsedClass(typeArgument.getInternalName(), getFullContext(null, "dynamic method argument type"));
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
        String lineNumber = currentLine != null ? currentLine.toString() : "";

        return Strings.format("%s%s (%s)[%s]", currentClassName, method, context, lineNumber);
    }

}

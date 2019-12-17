/*
 * Copyright (c) Dec 9, 2019 StarChart Labs Authors.
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

import org.objectweb.asm.Opcodes;

//TODO romeara
public final class AsmUtils {

    public static final int ASM_API = Opcodes.ASM7;

    /**
     * Prevent instantiation of utility class
     */
    private AsmUtils() throws InstantiationException {
        throw new InstantiationException("Cannot instantiate instance of utility class '" + getClass().getName() + "'");
    }

    public static String toExternalName(String internalClassName) {
        Objects.requireNonNull(internalClassName);

        return internalClassName
                .replace('/', '.')
                .replace('$', '.');
    }

}

/*
 * Copyright (c) Nov 14, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.cli.impl;

//TODO romeara
public interface ClassUseTracer {

    // Thing found in class in question
    void traceClassFeature(String className, String feature);

    void traceClassUse(String className, String usedIn);

}

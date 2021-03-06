/*
 * Copyright (c) Feb 18, 2020 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.io;

import java.io.IOException;
import java.nio.file.Path;

//TODO romeara
@FunctionalInterface
public interface FileConsumer {

    void acceptFile(Path file) throws IOException;

}

/*
 * Copyright (c) Nov 8, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.necromancer.cli;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.spi.SubCommand;
import org.kohsuke.args4j.spi.SubCommandHandler;
import org.kohsuke.args4j.spi.SubCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.necromancer.cli.command.ClassesCommand;

public class CommandLineInterface {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Argument(handler = SubCommandHandler.class)
    @SubCommands({
            @SubCommand(name = "classes", impl = ClassesCommand.class),
    })
    private Runnable command;

    public static void main(String[] args) {
        new CommandLineInterface().run(args);
    }

    public void run(String[] args) {
        CmdLineParser parser = new CmdLineParser(this);

        try {
            // parse the arguments.
            parser.parseArgument(args);

            if (command != null) {
                command.run();
            } else {
                logger.error("Invalid command line arguments");
            }
        } catch (CmdLineException e) {
            logger.error("Invalid command line arguments", e);
        }
    }

}

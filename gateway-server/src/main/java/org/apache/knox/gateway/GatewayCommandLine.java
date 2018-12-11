/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.cli.HelpFormatter.DEFAULT_DESC_PAD;
import static org.apache.commons.cli.HelpFormatter.DEFAULT_LEFT_PAD;

public class GatewayCommandLine {

  public static CommandLine parse( String[] args ) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    return parser.parse( createCommandLine(), args );
  }

  public static void printUsage() {
    PrintWriter printer = new PrintWriter(new OutputStreamWriter( System.err, StandardCharsets.UTF_8), true);
    new HelpFormatter().printUsage( printer, LINE_WIDTH, COMMAND_NAME, createCommandLine() );
    printer.flush();
  }

  public static void printHelp() {
    PrintWriter printer = new PrintWriter(new OutputStreamWriter( System.err, StandardCharsets.UTF_8), true);
    new HelpFormatter().printHelp(printer, LINE_WIDTH, COMMAND_NAME, null, createCommandLine(), DEFAULT_LEFT_PAD, DEFAULT_DESC_PAD, null);
    printer.flush();
  }

  /** default number of characters per line */
  public static final int LINE_WIDTH = 80;
  /** Name of the command to use in the command line */
  public static final String COMMAND_NAME= "knox";

  public static final String HELP_LONG = "help";
  public static final String HELP_SHORT = "h";

  public static final String VERSION_LONG = "version";
  public static final String VERSION_SHORT = "v";

  public static final String PERSIST_LONG = "persist-master";
  public static final String PERSIST_SHORT = "pm";

  public static final String NOSTART_LONG = "nostart";
  public static final String NOSTART_SHORT = "ns";

  public static final String REDEPLOY_LONG = "redeploy";
  public static final String REDEPLOY_SHORT = "rd";

  private static Options createCommandLine() {
    Options options = new Options();
    options.addOption( HELP_SHORT, HELP_LONG, false, res.helpMessage() );
    options.addOption( VERSION_SHORT, VERSION_LONG, false, res.versionHelpMessage() );
    Option redeploy = new Option( REDEPLOY_SHORT, REDEPLOY_LONG, true, res.redeployHelpMessage() );
    redeploy.setOptionalArg( true );
    options.addOption( redeploy );
    options.addOption( PERSIST_SHORT, PERSIST_LONG, false, res.persistMasterHelpMessage() );
    options.addOption( NOSTART_SHORT, NOSTART_LONG, false, res.nostartHelpMessage() );
    return options;
  }

  private static GatewayResources res = ResourcesFactory.get( GatewayResources.class );
}

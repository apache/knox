/**
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
package org.apache.hadoop.gateway;

import org.apache.commons.cli.*;

import java.io.PrintWriter;

public class GatewayCommandLine {

  public static Options createCommandLine() {
    Options options = new Options();
    //Option option = new Option( "short-flag", "long-flag", true, "desc" );
    //option.setRequired( true );
    //options.addOption( options );
    options.addOption( "h", "help", false, "Help" );
    options.addOption( "v", "version", false, "Version" );
    options.addOption( "i", "install", false, "Version" );
    return options;
  }

  public static CommandLine parse( String[] args ) throws ParseException {
    CommandLineParser parser = new PosixParser();
    CommandLine commandLine = parser.parse( createCommandLine(), args );
    return commandLine;
  }

  public static void printUsage() {
    PrintWriter printer = new PrintWriter( System.err );
    new HelpFormatter().printUsage( printer, 80, "gateway", createCommandLine() );
    printer.flush();
  }

  public static void printHelp() {
    PrintWriter printer = new PrintWriter( System.err );
    new HelpFormatter().printUsage( printer, 80, "gateway", createCommandLine() );
    printer.flush();
  }

}

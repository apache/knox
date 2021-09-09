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
package org.apache.knox.gateway.shell;

import groovy.ui.GroovyMain;

import org.apache.knox.gateway.shell.commands.AbstractSQLCommandSupport;
import org.apache.knox.gateway.shell.commands.CSVCommand;
import org.apache.knox.gateway.shell.commands.DataSourceCommand;
import org.apache.knox.gateway.shell.commands.SelectCommand;
import org.apache.knox.gateway.shell.commands.WebHDFSCommand;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.knox.gateway.shell.hdfs.Hdfs;
import org.apache.knox.gateway.shell.job.Job;
import org.apache.knox.gateway.shell.manager.Manager;
import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.apache.knox.gateway.shell.workflow.Workflow;
import org.apache.knox.gateway.shell.yarn.Yarn;
import org.codehaus.groovy.tools.shell.AnsiDetector;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Shell {

  private static final List<String> NON_INTERACTIVE_COMMANDS = Arrays.asList("buildTrustStore", "init", "list", "destroy", "knoxline");

  private static final String[] IMPORTS = new String[] {
      KnoxSession.class.getName(),
      HBase.class.getName(),
      Hdfs.class.getName(),
      Job.class.getName(),
      Workflow.class.getName(),
      Yarn.class.getName(),
      TimeUnit.class.getName(),
      Manager.class.getName(),
      KnoxShellTable.class.getName()
  };

  static {
    AnsiConsole.systemInstall();
    Ansi.setDetector( new AnsiDetector() );
    System.setProperty( "groovysh.prompt", "knox" );
  }

  @SuppressWarnings("PMD.DoNotUseThreads") // we need to define a Thread to be able to register a shutdown hook
  public static void main( String... args ) throws Exception {
    if( args.length > 0 ) {
      if (NON_INTERACTIVE_COMMANDS.contains(args[0])) {
          final String[] arguments = new String[args.length == 1 ? 1:3];
          arguments[0] = args[0];
          if (args.length > 1) {
            arguments[1] = "--gateway";
            arguments[2] = args[1];
          }
          KnoxSh.main(arguments);
      } else {
          GroovyMain.main( args );
      }
    } else {
      Groovysh shell = new Groovysh();
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          System.out.println("Closing any open connections ...");
          AbstractSQLCommandSupport sqlcmd = (AbstractSQLCommandSupport) shell.getRegistry().getProperty(":ds");
          sqlcmd.closeConnections();
          sqlcmd = (AbstractSQLCommandSupport) shell.getRegistry().getProperty(":sql");
          sqlcmd.closeConnections();
        }
      });
      for( String name : IMPORTS ) {
        shell.execute( "import " + name );
      }
      // register custom groovysh commands
      shell.register(new SelectCommand(shell));
      shell.register(new DataSourceCommand(shell));
      shell.register(new CSVCommand(shell));
      shell.register(new WebHDFSCommand(shell));
      shell.run( null );
    }
  }

}

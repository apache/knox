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
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.knox.gateway.shell.hdfs.Hdfs;
import org.apache.knox.gateway.shell.job.Job;
import org.apache.knox.gateway.shell.manager.Manager;
import org.apache.knox.gateway.shell.workflow.Workflow;
import org.apache.knox.gateway.shell.yarn.Yarn;
import org.apache.log4j.PropertyConfigurator;
import org.codehaus.groovy.tools.shell.AnsiDetector;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Shell {

  private static final String[] IMPORTS = new String[] {
      Hadoop.class.getName(),
      HBase.class.getName(),
      Hdfs.class.getName(),
      Job.class.getName(),
      Workflow.class.getName(),
      Yarn.class.getName(),
      TimeUnit.class.getName(),
      Manager.class.getName()
  };

  static {
    AnsiConsole.systemInstall();
    Ansi.setDetector( new AnsiDetector() );
    System.setProperty( "groovysh.prompt", "knox" );
  }

  public static void main( String... args ) throws IOException {
    PropertyConfigurator.configure( System.getProperty( "log4j.configuration" ) );
    if( args.length > 0 ) {
      GroovyMain.main( args );
    } else {
      Groovysh shell = new Groovysh();
      for( String name : IMPORTS ) {
        shell.execute( "import " + name );
      }
      shell.run( null );
    }
  }

}

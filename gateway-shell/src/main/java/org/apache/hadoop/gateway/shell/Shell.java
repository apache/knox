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
package org.apache.hadoop.gateway.shell;

import groovy.ui.GroovyMain;
import org.codehaus.groovy.tools.shell.AnsiDetector;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Shell {

  static {
    AnsiConsole.systemInstall();
    Ansi.setDetector( new AnsiDetector() );
    System.setProperty( "groovysh.prompt", "knox" );
  }

  public static void main( String... args ) throws IOException {
    if( args.length > 0 ) {
      GroovyMain.main( args );
    } else {
      StringWriter buffer = new StringWriter();
      PrintWriter setup = new PrintWriter( buffer );
      setup.println( "import org.apache.hadoop.gateway.shell.Hadoop;" );
      setup.println( "import org.apache.hadoop.gateway.shell.hdfs.Hdfs;" );
      setup.println( "import org.apache.hadoop.gateway.shell.job.Job;" );
      setup.println( "import org.apache.hadoop.gateway.shell.workflow.Workflow;" );
      setup.println( "import org.apache.hadoop.gateway.shell.yarn.Yarn;" );
      setup.println( "import java.util.concurrent.TimeUnit;" );
      //setup.println( "set verbosity QUIET;" );
      //setup.println( "set show-last-result false;" );
      Groovysh shell = new Groovysh();
      shell.execute( buffer.toString() );
      shell.run();
    }
  }

}

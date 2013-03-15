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

import org.codehaus.groovy.tools.shell.AnsiDetector;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Shell {

  static {
    AnsiConsole.systemInstall();
    Ansi.setDetector( new AnsiDetector() );
    System.setProperty( "groovysh.prompt", "knox" );
  }

  public static void main( String... args ) {
    StringWriter buffer = new StringWriter();
    PrintWriter imports = new PrintWriter( buffer );
    imports.println( "import org.apache.hadoop.gateway.shell.Hadoop;" );
    imports.println( "import org.apache.hadoop.gateway.shell.hdfs.Hdfs as hdfs;" );
    imports.println( "import org.apache.hadoop.gateway.shell.job.Job as job;" );
    imports.println( "import org.apache.hadoop.gateway.shell.workflow.Workflow as workflow;" );
    Groovysh shell = new Groovysh();
    shell.execute( buffer.toString() );
    shell.run( args );
  }

}

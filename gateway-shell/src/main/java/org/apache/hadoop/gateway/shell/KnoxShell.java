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
import org.codehaus.groovy.tools.shell.IO;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

public class KnoxShell {

  static {
    AnsiConsole.systemInstall();
    Ansi.setDetector( new AnsiDetector() );
  }

  public static void main( String... args ) {
    Groovysh shell = new Groovysh();
    shell.execute( "import org.apache.commons.io.FileUtils" );
    shell.execute( "import static org.apache.commons.io.FileUtils.*" );
    shell.execute( "import com.jayway.restassured.RestAssured" );
    shell.execute( "import static com.jayway.restassured.RestAssured.*" );
    shell.execute( "import com.jayway.restassured.path.json.JsonPath" );
    shell.execute( "import static com.jayway.restassured.path.json.JsonPath.*" );
    shell.execute( "import org.apache.hadoop.gateway.shell.hadoop.Hadoop" );
    shell.execute( "import org.apache.hadoop.gateway.shell.hdfs.Hdfs" );
    shell.execute( "import static org.apache.hadoop.gateway.shell.hdfs.Hdfs.*" );
    shell.execute( "import org.apache.hadoop.gateway.shell.job.Job" );
    shell.execute( "import static org.apache.hadoop.gateway.shell.job.Job.*" );
    shell.execute( "import org.apache.hadoop.gateway.shell.workflow.Workflow" );
    shell.execute( "import static org.apache.hadoop.gateway.shell.workflow.Workflow.*" );
    shell.run( args );
  }

}

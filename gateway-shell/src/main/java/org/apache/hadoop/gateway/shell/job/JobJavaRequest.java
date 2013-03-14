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
package org.apache.hadoop.gateway.shell.job;

import org.apache.hadoop.gateway.shell.AbstractRequest;
import org.apache.hadoop.gateway.shell.hadoop.Hadoop;

public class JobJavaRequest extends AbstractRequest {

  String input;
  String output;

  public JobJavaRequest( Hadoop hadoop ) {
    super( hadoop );
  }

  public JobJavaRequest jar( String jar ) {
    request().formParam( "jar", jar );
    return this;
  }

  public JobJavaRequest app( String app ) {
    request().formParam( "class", app );
    return this;
  }

  public JobJavaRequest input( String dir ) {
    input = dir;
    return this;
  }

  public JobJavaRequest output( String dir ) {
    output = dir;
    return this;
  }

  public JobJavaResponse go() {
    request().formParam( "arg", input );
    request().formParam( "arg", output );
    return new JobJavaResponse( request().post( Job.SERVICE_PATH + "/mapreduce/jar" ) );
  }

}

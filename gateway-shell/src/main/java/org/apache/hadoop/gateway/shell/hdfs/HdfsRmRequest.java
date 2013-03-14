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
package org.apache.hadoop.gateway.shell.hdfs;

import org.apache.hadoop.gateway.shell.AbstractRequest;
import org.apache.hadoop.gateway.shell.hadoop.Hadoop;

public class HdfsRmRequest extends AbstractRequest {

  String file;

  HdfsRmRequest( Hadoop hadoop ) {
    super( hadoop );
    request().queryParam( "op", "DELETE" );
  }

  public HdfsRmRequest file( String file ) {
    this.file = file;
    return this;
  }

  public HdfsRmRequest recursive( boolean recursive ) {
    request().queryParam( "recursive", Boolean.toString( recursive ) );
    return this;
  }

  public HdfsRmRequest recursive() {
    return recursive( true );
  }

  public HdfsRmResponse go() {
    return new HdfsRmResponse( request().delete( Hdfs.SERVICE_PATH + file ) );
  }

}

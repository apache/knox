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

import org.apache.hadoop.gateway.shell.Hadoop;

public class Hdfs {

  static String SERVICE_PATH = "/namenode/api/v1";

  public static Ls.Request ls( Hadoop hadoop ) {
    return new Ls.Request( hadoop );
  }

  public static Rm.Request rm( Hadoop hadoop ) {
    return new Rm.Request( hadoop );
  }

  public static Put.Request put( Hadoop hadoop ) {
    return new Put.Request( hadoop );
  }

  public static Get.Request get( Hadoop hadoop ) {
    return new Get.Request( hadoop );
  }

  public static Mkdir.Request mkdir( Hadoop hadoop ) {
    return new Mkdir.Request( hadoop );
  }

}

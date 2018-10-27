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
package org.apache.knox.gateway.shell.hdfs;

import org.apache.knox.gateway.shell.KnoxSession;

public class Hdfs {

  static final String SERVICE_PATH = "/webhdfs/v1";

  public static Rename.Request rename( KnoxSession session ) {
    return new Rename.Request( session );
  }

  public static Status.Request status(KnoxSession session ) {
    return new Status.Request( session );
  }

  public static Ls.Request ls( KnoxSession session ) {
    return new Ls.Request( session );
  }

  public static Rm.Request rm( KnoxSession session ) {
    return new Rm.Request( session );
  }

  public static Put.Request put( KnoxSession session ) {
    return new Put.Request( session );
  }

  public static Get.Request get( KnoxSession session ) {
    return new Get.Request( session );
  }

  public static Mkdir.Request mkdir( KnoxSession session ) {
    return new Mkdir.Request( session );
  }

}

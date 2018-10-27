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
package org.apache.knox.gateway.shell.job;

import org.apache.knox.gateway.shell.KnoxSession;

public class Job {

  static final String SERVICE_PATH = "/templeton/v1";

  public static Java.Request submitJava( KnoxSession session ) {
    return new Java.Request( session );
  }

  public static Sqoop.Request submitSqoop( KnoxSession session ) {
    return new Sqoop.Request( session );
  }

  public static Pig.Request submitPig( KnoxSession session ) {
    return new Pig.Request( session );
  }

  public static Hive.Request submitHive( KnoxSession session ) {
    return new Hive.Request( session );
  }

  public static Queue.Request queryQueue( KnoxSession session ) {
    return new Queue.Request( session );
  }

  public static Status.Request queryStatus( KnoxSession session ) {
    return new Status.Request( session );
  }

}

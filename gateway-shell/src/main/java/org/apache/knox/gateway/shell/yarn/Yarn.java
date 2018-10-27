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
package org.apache.knox.gateway.shell.yarn;

import org.apache.knox.gateway.shell.KnoxSession;

public class Yarn {

    static final String SERVICE_PATH = "/resourcemanager";

    public static NewApp.Request newApp(KnoxSession session) {
        return new NewApp.Request(session);
    }

    public static SubmitApp.Request submitApp(KnoxSession session) {
        return new SubmitApp.Request(session);
    }

    public static KillApp.Request killApp(KnoxSession session) {
        return new KillApp.Request(session);
    }

    public static AppState.Request appState(KnoxSession session) {
        return new AppState.Request(session);
    }

}

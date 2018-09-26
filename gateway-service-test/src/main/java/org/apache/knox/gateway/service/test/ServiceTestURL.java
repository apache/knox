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

package org.apache.knox.gateway.service.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceTestURL {

  private static final Map<String, List<String>> urls;
  static final String WEBHDFS = "WEBHDFS";
  static final String OOZIE = "OOZIE";
  static final String YARN = "RESOURCEMANAGER";
  static final String WEBHCAT = "WEBHCAT";
  static final String HBASE = "WEBHBASE";
  static final String HIVE = "HIVE";
  static final String STORM = "STORM";
  enum GATEWAY_SERVICES {
    WEBHDFS, OOZIE, YARN, WEBHCAT, HBASE, HIVE, STORM
  }

  static {
    Map<String, List<String>> urlMap = new HashMap<>();
//    WEBHDFS
    List<String> webhdfs = new ArrayList<>();
    webhdfs.add("/webhdfs/v1/?op=LISTSTATUS");
    urlMap.put(WEBHDFS, webhdfs);

//    OOZIE
    List<String> oozie = new ArrayList<>();
    oozie.add("/oozie/v1/admin/build-version");
    oozie.add("/oozie/versions");
    oozie.add("/oozie/v1/admin/status");
    urlMap.put(OOZIE, oozie);

//    RESOURCEMANAGER
    List<String> resourceManager = new ArrayList<>();
    resourceManager.add("/resourcemanager/v1/cluster/info");
    resourceManager.add("/resourcemanager/v1/cluster/metrics");
    resourceManager.add("/resourcemanager/v1/cluster/apps");
    urlMap.put(YARN, resourceManager);

//    WEBHCAT
    List<String> templeton = new ArrayList<>();
    templeton.add("/templeton/v1/status");
    templeton.add("/templeton/v1/version");
    templeton.add("/templeton/v1/version/hive");
    templeton.add("/templeton/v1/version/hadoop");
    urlMap.put(WEBHCAT, templeton);

//    WEBHBASE
    List<String> hbase = new ArrayList<>();
    hbase.add("/hbase/version");
    hbase.add("/hbase/version/cluster");
    hbase.add("/hbase/status/cluster");
    hbase.add("/hbase");
    urlMap.put(HBASE, hbase);

//    HIVE
//    Not yet implemented
//    ??????

    urls = urlMap;
  }


  public static List<String> get(String url){
    return urls.get(url);
  }

}

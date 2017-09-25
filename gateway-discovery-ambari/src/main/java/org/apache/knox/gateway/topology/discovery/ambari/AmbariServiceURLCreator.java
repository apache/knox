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
package org.apache.knox.gateway.topology.discovery.ambari;


import java.util.ArrayList;
import java.util.List;

class AmbariServiceURLCreator {

    private static final String NAMENODE_SERVICE        = "NAMENODE";
    private static final String JOBTRACKER_SERVICE      = "JOBTRACKER";
    private static final String WEBHDFS_SERVICE         = "WEBHDFS";
    private static final String WEBHCAT_SERVICE         = "WEBHCAT";
    private static final String OOZIE_SERVICE           = "OOZIE";
    private static final String WEBHBASE_SERVICE        = "WEBHBASE";
    private static final String HIVE_SERVICE            = "HIVE";
    private static final String RESOURCEMANAGER_SERVICE = "RESOURCEMANAGER";


    /**
     * Derive the endpoint URL(s) for the specified service, based on the info from the specified Cluster.
     *
     * @param cluster The cluster discovery results
     * @param serviceName The name of a Hadoop service
     *
     * @return One or more endpoint URLs for the specified service.
     */
    public List<String> create(AmbariCluster cluster, String serviceName) {
        List<String> result = null;

        if (NAMENODE_SERVICE.equals(serviceName)) {
            result = createNameNodeURL(cluster);
        } else if (JOBTRACKER_SERVICE.equals(serviceName)) {
            result = createJobTrackerURL(cluster);
        } else if (WEBHDFS_SERVICE.equals(serviceName)) {
            result = createWebHDFSURL(cluster);
        } else if (WEBHCAT_SERVICE.equals(serviceName)) {
            result = createWebHCatURL(cluster);
        } else if (OOZIE_SERVICE.equals(serviceName)) {
            result = createOozieURL(cluster);
        } else if (WEBHBASE_SERVICE.equals(serviceName)) {
            result = createWebHBaseURL(cluster);
        } else if (HIVE_SERVICE.equals(serviceName)) {
            result = createHiveURL(cluster);
        } else if (RESOURCEMANAGER_SERVICE.equals(serviceName)) {
            result = createResourceManagerURL(cluster);
        }

        return result;
    }


    private List<String> createNameNodeURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent comp = cluster.getComponent("NAMENODE");
        if (comp != null) {
            result.add("hdfs://" + comp.getConfigProperty("dfs.namenode.rpc-address"));
        }

        return result;
    }


    private List<String> createJobTrackerURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent comp = cluster.getComponent("RESOURCEMANAGER");
        if (comp != null) {
            result.add("rpc://" + comp.getConfigProperty("yarn.resourcemanager.address"));
        }

        return result;
    }


    private List<String> createWebHDFSURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration("HDFS", "hdfs-site");
        if (sc != null) {
            String address = sc.getProperties().get("dfs.namenode.http-address");
            result.add("http://" + address + "/webhdfs");
        }

        return result;
    }


    private List<String> createWebHCatURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent webhcat = cluster.getComponent("WEBHCAT_SERVER");
        if (webhcat != null) {
            String port = webhcat.getConfigProperty("templeton.port");
            String host = webhcat.getHostNames().get(0);

            result.add("http://" + host + ":" + port + "/templeton");
        }
        return result;
    }


    private List<String> createOozieURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent comp = cluster.getComponent("OOZIE_SERVER");
        if (comp != null) {
            result.add(comp.getConfigProperty("oozie.base.url"));
        }

        return result;
    }


    private List<String> createWebHBaseURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent comp = cluster.getComponent("HBASE_MASTER");
        if (comp != null) {
            for (String host : comp.getHostNames()) {
                result.add("http://" + host + ":60080");
            }
        }

        return result;
    }


    private List<String> createHiveURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent hive = cluster.getComponent("HIVE_SERVER");
        if (hive != null) {
            String path = hive.getConfigProperty("hive.server2.thrift.http.path");
            String port = hive.getConfigProperty("hive.server2.thrift.http.port");
            String transport = hive.getConfigProperty("hive.server2.transport.mode");
            String useSSL = hive.getConfigProperty("hive.server2.use.SSL");
            String host = hive.getHostNames().get(0);

            String scheme = null; // What is the scheme for the binary transport mode?
            if ("http".equals(transport)) {
                scheme = Boolean.valueOf(useSSL) ? "https" : "http";
            }

            result.add(scheme + "://" + host + ":" + port + "/" + path);
        }
        return result;
    }


    private List<String> createResourceManagerURL(AmbariCluster cluster) {
        List<String> result = new ArrayList<>();

        AmbariComponent resMan = cluster.getComponent("RESOURCEMANAGER");
        if (resMan != null) {
            String webappAddress = resMan.getConfigProperty("yarn.resourcemanager.webapp.address");
            String httpPolicy = resMan.getConfigProperty("yarn.http.policy");
            String scheme = ("HTTPS_ONLY".equalsIgnoreCase(httpPolicy)) ? "https" : "http";

            result.add(scheme + "://" + webappAddress + "/ws");
        }

        return result;
    }


}

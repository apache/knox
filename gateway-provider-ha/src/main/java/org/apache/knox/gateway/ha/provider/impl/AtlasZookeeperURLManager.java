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

package org.apache.knox.gateway.ha.provider.impl;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.nio.charset.Charset;
import java.util.*;

public class AtlasZookeeperURLManager extends DefaultURLManager {
    private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

    private String zooKeeperEnsemble;

    private String zooKeeperNamespace;

    public static final String APACHE_ATLAS_ACTIVE_SERVER_INFO = "/active_server_info";


    @Override
    public boolean supportsConfig(HaServiceConfig config) {
        if (!( config.getServiceName().equalsIgnoreCase("ATLAS") || config.getServiceName().equalsIgnoreCase("ATLAS-API"))) {
            return false;
        }
        String zookeeperEnsemble = config.getZookeeperEnsemble();
        String zookeeperNamespace = config.getZookeeperNamespace();
        return zookeeperEnsemble != null && zookeeperNamespace != null && !zookeeperEnsemble.trim().isEmpty() && !zookeeperNamespace.trim().isEmpty();
    }

    @Override
    public void setConfig(HaServiceConfig config) {
        zooKeeperEnsemble = config.getZookeeperEnsemble();
        zooKeeperNamespace = config.getZookeeperNamespace();
        if (!zooKeeperNamespace.startsWith("/")) {
            zooKeeperNamespace = "/" + zooKeeperNamespace;
        }
        setURLs(lookupURLs());
    }

    public List<String> lookupURLs() {

        List<String> serverHosts = new ArrayList<>();
        CuratorFramework zooKeeperClient =
                CuratorFrameworkFactory.builder().connectString(zooKeeperEnsemble)
                                                 .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                                                 .build();
        try {
            zooKeeperClient.start();

            byte[] bytes = zooKeeperClient.getData().forPath(zooKeeperNamespace + APACHE_ATLAS_ACTIVE_SERVER_INFO);

            String activeURL = new String(bytes, Charset.forName("UTF-8"));

            serverHosts.add(activeURL);

        } catch (Exception e) {

            LOG.failedToGetZookeeperUrls(e);
            throw new RuntimeException(e);
        } finally {
            // Close the client connection with ZooKeeper
            if (zooKeeperClient != null) {
                zooKeeperClient.close();
            }
        }
        return serverHosts;
    }



    @Override
    public synchronized void markFailed(String url) {
        setURLs(lookupURLs());
        super.markFailed(url);
    }
}

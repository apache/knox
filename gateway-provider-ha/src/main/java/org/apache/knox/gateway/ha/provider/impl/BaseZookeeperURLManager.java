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
package org.apache.knox.gateway.ha.provider.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.URLManager;
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.google.common.collect.Lists;

/**
 * Base implementation of URLManager intended for query of Zookeeper active hosts. In
 * the event of a failure via markFailed, Zookeeper is queried again for active
 * host information.
 * 
 * When configuring the HAProvider in the topology, the zookeeperEnsemble attribute must be set to a
 * comma delimited list of the host and port number, i.e. host1:2181,host2:2181. 
 */
public abstract class BaseZookeeperURLManager implements URLManager {
	protected static final HaMessages LOG = MessagesFactory.get(HaMessages.class);
	/**
	 * Host Ping Timeout
	 */
	private static final int TIMEOUT = 2000;

	private String zooKeeperEnsemble;
	private String zooKeeperNamespace;
	private ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<String>();

	// -------------------------------------------------------------------------------------
	// URLManager interface methods
	// -------------------------------------------------------------------------------------

	@Override
	public boolean supportsConfig(HaServiceConfig config) {
		if (!config.getServiceName().equalsIgnoreCase(getServiceName())) {
			return false;
		}
		
		String zookeeperEnsemble = config.getZookeeperEnsemble();
		if (zookeeperEnsemble != null && zookeeperEnsemble.trim().length() > 0) {
			return true;
		}

		return false;
	}

	@Override
	public void setConfig(HaServiceConfig config) {
		zooKeeperEnsemble  = config.getZookeeperEnsemble();
		zooKeeperNamespace = config.getZookeeperNamespace();
		setURLs(lookupURLs());
	}

	@Override
	public synchronized String getActiveURL() {
		// None available so refresh
		if (urls.isEmpty()) {
			setURLs(lookupURLs());
		}

		return this.urls.peek();
	}

	@Override
	public synchronized void setActiveURL(String url) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized List<String> getURLs() {
		return Lists.newArrayList(this.urls.iterator());
	}

	@Override
	public synchronized void markFailed(String url) {
		// Capture complete URL of active host
		String topURL = getActiveURL();

		// Refresh URLs from ZooKeeper
		setURLs(lookupURLs());

		// Show failed URL and new URL
		LOG.markedFailedUrl(topURL, getActiveURL());
	}

	@Override
	public synchronized void setURLs(List<String> urls) {
		if ((urls != null) && (!(urls.isEmpty()))) {
			this.urls.clear();
			this.urls.addAll(urls);
		}
	}

	// -------------------------------------------------------------------------------------
	// Abstract methods
	// -------------------------------------------------------------------------------------

	/**
	 * Look within Zookeeper under the /live_nodes branch for active hosts
	 * 
	 * @return A List of URLs (never null)
	 */
	protected abstract List<String> lookupURLs();

	/**
	 * @return The name of the Knox Topology Service to support
	 */
	protected abstract String getServiceName();

	// -------------------------------------------------------------------------------------
	// Protected methods
	// -------------------------------------------------------------------------------------

	protected String getZookeeperEnsemble() {
		return zooKeeperEnsemble;
	}

	protected String getZookeeperNamespace() {
		return zooKeeperNamespace;
	}

	/**
	 * Validate access to hosts using simple light weight ping style REST call.
	 * 
	 * @param hosts List of hosts to evaluate (required)
	 * @param suffix Text to append to host (required) 
	 * @param acceptHeader Used for Accept header (optional)
	 * 
	 * @return Hosts with successful access
	 */
	protected List<String> validateHosts(List<String> hosts, String suffix, String acceptHeader) {
		List<String> result = new ArrayList<String>();
		
		CloseableHttpClient client = null;
		
		try {
			// Construct a HttpClient with short term timeout
			RequestConfig.Builder requestBuilder = RequestConfig.custom()
					.setConnectTimeout(TIMEOUT)
					.setSocketTimeout(TIMEOUT)
					.setConnectionRequestTimeout(TIMEOUT);

			client = HttpClientBuilder.create()
					.setDefaultRequestConfig(requestBuilder.build())
					.build();
			
			for(String host: hosts) {
				try	{
					HttpGet get = new HttpGet(host + suffix);
					
					if (acceptHeader != null) {
						get.setHeader("Accept", acceptHeader);
					}
					
					String response = client.execute(get, new StringResponseHandler());
					
					if (response != null) {
						result.add(host);
					}
				}
				catch (Exception ex) {
					// ignore host
				}
			}
		}
		catch (Exception ex) {
			// Ignore errors
		}
		finally	{
			IOUtils.closeQuietly(client);
		}
		
		return result;
	}
}

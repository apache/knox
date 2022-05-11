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
package org.apache.knox.gateway.identityasserter.hadoop.groups.filter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.apache.hadoop.security.Groups;

/**
 * A filter that integrates the Hadoop {@link GroupMappingServiceProvider} for
 * looking up group membership of the authenticated (asserted) identity.
 *
 * @since 0.11.0
 */
public class HadoopGroupProviderFilter extends CommonIdentityAssertionFilter {

  /**
   * Logging
   */
  public static final HadoopGroupProviderMessages LOG = MessagesFactory
      .get(HadoopGroupProviderMessages.class);

  /**
   * Configuration object needed by for hadoop classes
   */
  private Configuration hadoopConfig;

  /**
   * Hadoop Groups implementation.
   */
  private Groups hadoopGroups;

  /* create an instance */
  public HadoopGroupProviderFilter() {
    super();
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);

    try {
      hadoopConfig = new Configuration(false);

      if (filterConfig.getInitParameterNames() != null) {

        for (final Enumeration<String> keys = filterConfig
            .getInitParameterNames(); keys.hasMoreElements();) {

          final String key = keys.nextElement();
          hadoopConfig.set(key, filterConfig.getInitParameter(key));

        }

      }
      hadoopGroups = new Groups(hadoopConfig);

    } catch (final Exception e) {
      throw new ServletException(e);
    }

  }

  /**
   * Query the Hadoop implementation of {@link Groups} to retrieve groups for
   * provided user.
   */
  @Override
  public String[] mapGroupPrincipals(final String mappedPrincipalName,
                                     final Subject subject) {
    /* return the groups as seen by Hadoop */
    String[] groups;
    try {
      final List<String> groupList = hadoopGroups(mappedPrincipalName);
      LOG.groupsFound(mappedPrincipalName, groupList.toString());
      groups = groupList.toArray(new String[0]);

    } catch (final IOException e) {
      if (e.toString().contains("No groups found for user")) {
        /* no groups found move on */
        LOG.noGroupsFound(mappedPrincipalName);
      } else {
        /* Log the error and return empty group */
        LOG.errorGettingUserGroups(mappedPrincipalName, e);
      }
      groups = new String[0];
    }
    return groups;
  }

  protected List<String> hadoopGroups(String mappedPrincipalName) throws IOException {
    return hadoopGroups.getGroups(mappedPrincipalName);
  }

  @Override
  public String mapUserPrincipal(final String principalName) {
    /* return the passed principal */
    return principalName;
  }

}

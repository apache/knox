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
package org.apache.knox.gateway.identityasserter.switchcase;

import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.knox.gateway.security.GroupPrincipal;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import java.util.Locale;
import java.util.Set;

public class SwitchCaseIdentityAssertionFilter extends
    CommonIdentityAssertionFilter {

  private static final String USER_INIT_PARAM = "principal.case";
  private static final String GROUP_INIT_PARAM = "group.principal.case";

  private enum SwitchCase { UPPER, LOWER, NONE }

  private SwitchCase userCase = SwitchCase.LOWER;
  private SwitchCase groupCase = SwitchCase.LOWER;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init(filterConfig);

    String s;
    s = filterConfig.getInitParameter( USER_INIT_PARAM );
    if ( s != null ) {
      s = s.trim().toUpperCase(Locale.ROOT);
      try {
        userCase = SwitchCase.valueOf( s );
        groupCase = userCase;
      } catch ( IllegalArgumentException e ) {
        // Ignore it and use the default.
      }
    }
    s = filterConfig.getInitParameter( GROUP_INIT_PARAM );
    if ( s != null ) {
      s = s.trim().toUpperCase(Locale.ROOT);
      try {
        groupCase = SwitchCase.valueOf( s );
      } catch ( IllegalArgumentException e ) {
        // Ignore it and use the default.
      }
    }
  }

  @Override
  public String mapUserPrincipal( String principalName ) {
    return switchCase( principalName, userCase );
  }

  @Override
  public String[] mapGroupPrincipals( String mappedPrincipalName, Subject subject ) {
    String[] groupNames = null;
    if ( groupCase != SwitchCase.NONE ) {
      Set<GroupPrincipal> groups = subject.getPrincipals( GroupPrincipal.class );
      if( groups != null && !groups.isEmpty() ) {
        groupNames = new String[ groups.size() ];
        int i = 0;
        for( GroupPrincipal group : groups ) {
          groupNames[ i++ ] = switchCase( group.getName(), groupCase );
        }
      }
    }
    return groupNames;
  }

  private String switchCase( String name, SwitchCase switchCase ) {
    if ( name != null ) {
      switch( switchCase ) {
        case UPPER:
          return name.toUpperCase(Locale.ROOT);
        case LOWER:
          return name.toLowerCase(Locale.ROOT);
      }
    }
    return name;
  }

}

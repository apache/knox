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
package org.apache.hadoop.gateway.provider.federation.jwt.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.provider.federation.jwt.JWTMessages;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

/**
 *
 */
public abstract class AbstractJWTFilter implements Filter {
  static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  protected List<String> audiences = null;

  public abstract void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException;

  /**
   * 
   */
  public AbstractJWTFilter() {
    super();
  }

  /**
   * @param expectedAudiences
   * @return
   */
  protected List<String> parseExpectedAudiences(String expectedAudiences) {
    ArrayList<String> audList = null;
    // setup the list of valid audiences for token validation
    if (expectedAudiences != null) {
      // parse into the list
      String[] audArray = expectedAudiences.split(",");
      audList = new ArrayList<String>();
      for (String a : audArray) {
        audList.add(a);
      }
    }
    return audList;
  }

  protected boolean tokenIsStillValid(JWTToken jwtToken) {
    // if there is no expiration data then the lifecycle is tied entirely to
    // the cookie validity - otherwise ensure that the current time is before
    // the designated expiration time
    Date expires = jwtToken.getExpiresDate();
    return (expires == null || expires != null && new Date().before(expires));
  }
  
  /**
   * Validate whether any of the accepted audience claims is present in the
   * issued token claims list for audience. Override this method in subclasses
   * in order to customize the audience validation behavior.
   *
   * @param jwtToken
   *          the JWT token where the allowed audiences will be found
   * @return true if an expected audience is present, otherwise false
   */
  protected boolean validateAudiences(JWTToken jwtToken) {
    boolean valid = false;
    
    String[] tokenAudienceList = jwtToken.getAudienceClaims();
    // if there were no expected audiences configured then just
    // consider any audience acceptable
    if (audiences == null) {
      valid = true;
    } else {
      // if any of the configured audiences is found then consider it
      // acceptable
      if (tokenAudienceList != null) {
        for (String aud : tokenAudienceList) {
          if (audiences.contains(aud)) {
            log.jwtAudienceValidated();
            valid = true;
            break;
          }
        }
      }
    }
    return valid;
  }

}
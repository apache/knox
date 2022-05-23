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
package org.apache.knox.gateway.identityasserter.common.filter;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.servlet.SynchronousServletInputStreamAdapter;
import org.apache.knox.gateway.util.HttpUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;

public class IdentityAsserterHttpServletRequestWrapper extends HttpServletRequestWrapper {

  protected static final SpiGatewayMessages log = MessagesFactory.get( SpiGatewayMessages.class );

  private static final String PRINCIPAL_PARAM = "user.name";
  private static final String DOAS_PRINCIPAL_PARAM = "doAs";
  private List<String> impersonationParamsList;

  private String username;

  public IdentityAsserterHttpServletRequestWrapper( HttpServletRequest request, String principal ) {
    this(request, principal, Collections.EMPTY_LIST);
  }

  public IdentityAsserterHttpServletRequestWrapper( HttpServletRequest request, String principal, List impersonationParamsList ) {
    super(request);
    username = principal;
    this.impersonationParamsList = impersonationParamsList;
  }

  @Override
  public Principal getUserPrincipal() {
    return new PrimaryPrincipal(username);
  }

  @Override
  public String getParameter(String name) {
    if (name.equals(PRINCIPAL_PARAM)) {
      return username;
    }
    return super.getParameter(name);
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return convertValuesToStringArrays();
  }

  private Map<String, String[]> convertValuesToStringArrays() {
    Map<String, String[]> arrayMap = new LinkedHashMap<>();
    String name;
    Enumeration<String> names = getParameterNames();
    while (names.hasMoreElements()) {
      name = names.nextElement();
      arrayMap.put(name, getParameterValues(name));
    }
    return arrayMap;
  }

  @Override
  public Enumeration<String> getParameterNames() {
    Enumeration<String> e = null;
    Map<String, List<String>> params;
    try {
      params = getParams();
      if (params == null) {
        params = new LinkedHashMap<>();
      }
      e = Collections.enumeration(params.keySet());
    } catch (UnsupportedEncodingException e1) {
      log.unableToGetParamsFromQueryString(e1);
    }

    return e;
  }

  @Override
  public String[] getParameterValues(String name) {
    String[] p = {};
    Map<String, List<String>> params;
    try {
      params = getParams();
      if (params == null) {
        params = new LinkedHashMap<>();
      }
      p = params.get(name).toArray(p);
    } catch (UnsupportedEncodingException e) {
      log.unableToGetParamsFromQueryString(e);
    }

    return p;
  }

  private Map<String, List<String>> getParams( String qString )
      throws UnsupportedEncodingException {
    Map<String, List<String>> params;
    if (getMethod().equals("GET")) {
      if (qString != null && !qString.isEmpty()) {
        params = HttpUtils.splitQuery( qString );
      }
      else {
        params = new LinkedHashMap<>();
      }
    }
    else {
      if (qString == null || qString.isEmpty()) {
        return null;
      }
      else {
        params = HttpUtils.splitQuery( qString );
      }
    }
    return params;
  }

  protected Map<String, List<String>> getParams()
      throws UnsupportedEncodingException {
    return getParams( super.getQueryString() );
  }

  @Override
  public String getQueryString() {
    String q = null;
    Map<String, List<String>> params;
    try {
      params = getParams();
      if (params == null) {
        params = new LinkedHashMap<>();
      }
      ArrayList<String> al = new ArrayList<>();
      al.add(username);

      List<String> principalParamNames = getImpersonationParamNames();
      params = scrubOfExistingPrincipalParams(params, principalParamNames);

      if (Boolean.parseBoolean(System.getProperty(GatewayConfig.HADOOP_KERBEROS_SECURED))) {
        params.put(DOAS_PRINCIPAL_PARAM, al);
      } else {
        params.put(PRINCIPAL_PARAM, al);
      }

      String encoding = getCharacterEncoding();
      if (encoding == null) {
        encoding = Charset.defaultCharset().name();
      }
      q = urlEncode(params, encoding);
    } catch (UnsupportedEncodingException e) {
      log.unableToGetParamsFromQueryString(e);
    }

    return q;
  }

  protected List<String> getImpersonationParamNames() {
    /**
     *  If for some reason impersonationParamsList is empty e.g. some component using
     *  old api then return the default list. This is for backwards compatibility.
     **/
    if(impersonationParamsList == null || impersonationParamsList.isEmpty()) {
      ArrayList<String> principalParamNames = new ArrayList<>();
      principalParamNames.add(DOAS_PRINCIPAL_PARAM);
      principalParamNames.add(PRINCIPAL_PARAM);
      return principalParamNames;
    } else {
      return impersonationParamsList;
    }
  }

  protected Map<String, List<String>> scrubOfExistingPrincipalParams(
      Map<String, List<String>> params, List<String> principalParamNames) {
    HashSet<String> remove = new HashSet<>();
    for (String paramKey : params.keySet()) {
      for (String p : principalParamNames) {
        if (p.equalsIgnoreCase(paramKey)) {
          remove.add(paramKey);
          log.possibleIdentitySpoofingAttempt(paramKey);
        }
      }
    }
    params.keySet().removeAll(remove);
    return params;
  }

  @Override
  public int getContentLength() {
    int len;
    String contentType = getContentType();
    // If the content type is a form we might rewrite the body so default it to -1.
    if( contentType != null && contentType.startsWith( "application/x-www-form-urlencoded" ) ) {
      len = -1;
    } else {
      len = super.getContentLength();
    }
    return len;
  }

  @Override
  public ServletInputStream getInputStream() throws java.io.IOException {
    String contentType = getContentType();
    if( contentType != null && contentType.startsWith( "application/x-www-form-urlencoded" ) ) {
      String encoding = getCharacterEncoding();
      if( encoding == null ) {
        encoding = Charset.defaultCharset().name();
      }
      String body = IOUtils.toString( super.getInputStream(), encoding );
      Map<String, List<String>> params = getParams( body );
      if (params == null) {
        params = new LinkedHashMap<>();
      }
      body = urlEncode( params, encoding );
      // ASCII is OK here because the urlEncode about should have already escaped
      return new ServletInputStreamWrapper( new ByteArrayInputStream( body.getBytes(StandardCharsets.US_ASCII.name()) ) );
    } else {
      return super.getInputStream();
    }
  }

  static String urlEncode( String string, String encoding ) {
    try {
      return URLEncoder.encode( string, encoding );
    } catch (UnsupportedEncodingException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  public static String urlEncode( Map<String, List<String>> map, String encoding ) {
    StringBuilder sb = new StringBuilder();
    for( Map.Entry<String,List<String>> entry : map.entrySet() ) {
      String name = entry.getKey();
      if( name != null && !name.isEmpty()) {
        List<String> values = entry.getValue();
        if( values == null || values.isEmpty() ) {
          sb.append( entry.getKey() );
        } else {
          for (String value : values) {
            if (sb.length() > 0) {
              sb.append('&');
            }
            try {
              sb.append(urlEncode(name, encoding));
              if (value != null) {
                sb.append('=');
                sb.append(urlEncode(value, encoding));
              }
            } catch (IllegalArgumentException e) {
              log.skippingUnencodableParameter(name, value, encoding, e);
            }
          }
        }
      }
    }
    return sb.toString();
  }

  private static class ServletInputStreamWrapper extends SynchronousServletInputStreamAdapter {

    private InputStream stream;

    ServletInputStreamWrapper( InputStream stream ) {
      this.stream = stream;
    }

    @Override
    public int read() throws IOException {
      return stream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      return stream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return stream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      return stream.skip(n);
    }

    @Override
    public int available() throws IOException {
      return stream.available();
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
      stream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
      stream.reset();
    }

    @Override
    public boolean markSupported() {
      return stream.markSupported();
    }
  }
}

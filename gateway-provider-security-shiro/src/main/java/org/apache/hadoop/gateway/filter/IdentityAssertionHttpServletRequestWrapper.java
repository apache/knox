package org.apache.hadoop.gateway.filter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class IdentityAssertionHttpServletRequestWrapper extends
    HttpServletRequestWrapper {
  
  String username = null;

  public IdentityAssertionHttpServletRequestWrapper(HttpServletRequest request, String principal) {
    super(request);
    username = principal;
  }

  @Override
  public String getParameter(String name) {
    if (name.equals("user.name")) {
      return username;
    }
    return super.getParameter(name);
  }
  
  @SuppressWarnings("rawtypes")
  @Override
  public Map getParameterMap() {
    return getParams();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public Enumeration getParameterNames() {
    Map<String, String[]> params = getParams();
    Enumeration<String> e = Collections.enumeration((Collection<String>) params);

    return e;
  }

  @Override
  public String[] getParameterValues(String name) {
    Map<String, String[]> params = getParams();

    return params.get(name);
  }

  private Map<String, String[]> getParams() {
    String qString = super.getQueryString();
    if (qString == null || qString.length() == 0) return null;
    Map<String, String[]> params = parseQueryString(qString);
    ArrayList<String> al = new ArrayList<String>();
    al.add(username);
    String[] a = {""};
    params.put("user.name", al.toArray(a));
    
    return params;
  }
  
  @Override
  public String getQueryString() {
    String q = null;
    Map<String, String[]> params = getParams();
    if (params != null) {
      q = urlEncodeUTF8(params);
    }
//    System.out.println(q);
    
    return q;
  }

  static String urlEncodeUTF8(String s) {
    try {
        return URLEncoder.encode(s, "UTF-8");
    } catch (UnsupportedEncodingException e) {
        throw new UnsupportedOperationException(e);
    }
  }
  
  static String urlEncodeUTF8(Map<?,?> map) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<?,?> entry : map.entrySet()) {
        if (sb.length() > 0) {
            sb.append("&");
        }
        String[] values = (String[]) entry.getValue();
        for (int i = 0; i < values.length; i++) {
          if (values[i] != null) {
            try {
            sb.append(String.format("%s=%s",
                urlEncodeUTF8(entry.getKey().toString()),
                urlEncodeUTF8(values[i])
            ));
            }
            catch (IllegalArgumentException e) {
              e.printStackTrace();
              System.out.println("SKIPPING PARAM: " + entry.getKey().toString() + " with value: " + values[i]);
            }
          }
        }
    }
    return sb.toString();       
}  
  
  @SuppressWarnings({ "deprecation", "unchecked" })
  private static Map<String,String[]> parseQueryString( String queryString ) {
    return javax.servlet.http.HttpUtils.parseQueryString( queryString );
  }
  

}

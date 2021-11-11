package org.apache.knox.gateway.websockets;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public class WebSocketFilterConfig implements FilterConfig {
    private Map<String,String> params;

    WebSocketFilterConfig(Map<String, String> params){
        this.params = params;
    }
    @Override
    public String getFilterName() {
        return "WebSocket";
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public String getInitParameter(String s) {
        String value = null;
        if (params != null) {
            value = params.get(s);
        }
        return value;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        Enumeration<String> names = null;
        if( params != null ) {
            names = Collections.enumeration( params.keySet() );
        }
        return names;
    }
}

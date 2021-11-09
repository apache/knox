package org.apache.knox.gateway.webshell;

import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public class WebShellFilterConfig implements FilterConfig {
    private Map<String,String> params;

    WebShellFilterConfig(Map<String, String> params){
        this.params = params;
    }
    @Override
    public String getFilterName() {
        return "WebShell";
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

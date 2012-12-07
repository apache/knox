package org.apache.hadoop.gateway.topology;

import java.util.HashMap;
import java.util.Map;

public class Provider {
  private String role;
  private String name;
  private boolean enabled;
  private Map<String, String> params = new HashMap<String, String>();

  public Provider() {
  }
  
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, String> getParams() {
    return params;
  }

  public void setParams(Map<String, String> params) {
    this.params = params;
  }

  public void addParam(ProviderParam param) {
    params.put(param.getName(), param.getValue());
  }

  public String getRole() {
    return role;
  }

  public void setRole( String role ) {
    this.role = role;
  }
}

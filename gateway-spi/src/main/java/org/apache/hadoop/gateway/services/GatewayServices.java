package org.apache.hadoop.gateway.services;

import java.util.Collection;


public interface GatewayServices {

  public abstract Collection<String> getServiceNames();

  public abstract Service getService(String serviceName);

}
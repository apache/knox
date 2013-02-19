package org.apache.hadoop.gateway.services;

import java.util.Map;

import org.apache.hadoop.gateway.config.GatewayConfig;


public interface Service {
  void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException;
  
  void start() throws ServiceLifecycleException;
  
  void stop() throws ServiceLifecycleException;
}

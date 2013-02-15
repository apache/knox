package org.apache.hadoop.gateway.services.security;

import org.apache.hadoop.gateway.services.Service;

public interface MasterService extends Service {

  public abstract char[] getMasterSecret();

}
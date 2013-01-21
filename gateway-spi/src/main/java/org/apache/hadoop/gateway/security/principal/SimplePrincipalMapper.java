package org.apache.hadoop.gateway.security.principal;

import java.util.HashMap;
import java.util.StringTokenizer;


public class SimplePrincipalMapper implements PrincipalMapper {
  public HashMap<String, String> table = new HashMap<String, String>();

  public SimplePrincipalMapper() {
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.filter.PrincipalMapper#loadMappingTable(java.lang.String)
   */
  @Override
  public void loadMappingTable(String principalMapping) throws PrincipalMappingException {
//    System.out.println("+++++++++++++ Loading the Mapping Table");
    if (principalMapping != null) {
      try {
        StringTokenizer t = new StringTokenizer(principalMapping, ";");
        do {
          String mapping = t.nextToken();
  //        System.out.println("+++++++++++++ Mapping: " + mapping);
          String principals = mapping.substring(0, mapping.indexOf('='));
  //        System.out.println("+++++++++++++ Principals: " + principals);
          String value = mapping.substring(mapping.indexOf('=')+1);
          String[] p = principals.split(",");
          for(int i = 0; i < p.length; i++) {
            table.put(p[i], value);
  //          System.out.println("+++++++++++++ Mapping into Table: " + p[i] + "->" + value);
          }
        } while(t.hasMoreTokens());
      }
      catch (Exception e) {
        // do not leave table in an unknown state - clear it instead
        // no principal mapping will occur
        table.clear();
        throw new PrincipalMappingException("Unable to load mappings from provided string - no principal mapping will be provided.");
      }
    }
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.filter.PrincipalMapper#mapPrincipal(java.lang.String)
   */
  @Override
  public String mapPrincipal(String principalName) {
    String p = null;
    
    p = table.get(principalName);
    if (p == null) {
      p = principalName;
    }
    
    return p;
  }
}
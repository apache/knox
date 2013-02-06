package org.apache.hadoop.gateway.security.principal;

public interface PrincipalMapper {

  /**
   * Load the internal principal mapping table from the provided
   * string value which conforms to the following semicolon delimited format:
   * actual[,another-actual]=mapped;...
   * @param principalMapping
   */
  public abstract void loadMappingTable(String principalMapping)
      throws PrincipalMappingException;

  /**
   * Acquire a mapped principal name from the mapping table
   * as appropriate. Otherwise, the provided principalName
   * will be used.
   * @param principalName
   * @return principal name to be used in the assertion
   */
  public abstract String mapPrincipal(String principalName);

}
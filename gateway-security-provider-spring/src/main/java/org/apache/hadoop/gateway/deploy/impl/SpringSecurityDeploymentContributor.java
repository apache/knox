package org.apache.hadoop.gateway.deploy.impl;

import java.util.Map;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.DeploymentContributorBase;
import org.apache.hadoop.gateway.topology.Provider;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.ServletType;

public class SpringSecurityDeploymentContributor extends DeploymentContributorBase {


  @Override
  public void contribute(DeploymentContext context) {

    ServletType<WebAppDescriptor> servlet = findServlet( context, context.getTopology().getName() );
    Provider provider = context.getTopology().getProvider("authentication");
    if (provider != null && provider.isEnabled()) {
      Map<String, String> params = provider.getParams();
      servlet.createInitParam()
          .paramName( "contextConfigLocation" )
          .paramValue( params.get("contextConfigLocation") );
    }
  }

}

package org.apache.hadoop.gateway.services;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.hadoop.gateway.GatewayServer;

public class GatewayServicesContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    GatewayServices gs = GatewayServer.getGatewayServices();
    sce.getServletContext().setAttribute(GatewayServer.GATEWAY_SERVICES_ATTRIBUTE, gs);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
  }

}

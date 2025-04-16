###Introduction

The Admin UI is a work in progress. It has started with viewpoint of being a simple web interface for 
 Admin API functions but will hopefully grow into being able to also provide visibility into the gateway
 in terms of logs and metrics.

###Source and Binaries

The Admin UI application follows the architecture of a hosted application in Knox. To that end it needs to be 
packaged up in the gateway-applications module in the source tree so that in the installation it can wind up here

`<GATEWAY_HOME>/data/applications/admin-ui`

However since the application is built using angular and various node modules the source tree is not something
we want to place into the gateway-applications module. Instead we will place the production 'binaries' in gateway-applications
 and have the source in a module called 'gateway-admin-ui'.
 
To work with the angular application you need to install some prerequisite tools. 
 
The main tool needed is the [angular cli](https://github.com/angular/angular-cli#installation) and while installing that you
 will get its dependencies which should fulfill any other requirements [Prerequisites](https://github.com/angular/angular-cli#prerequisites)
 
###Manager Topology

The Admin UI is deployed to a fixed topology. The topology file can be found under

`<GATEWAY_HOME>/conf/topologies/manager.xml`

The topology hosts an instance of the Admin API for the UI to use. The reason for this is that the existing Admin API needs
 to have a different security model from that used by the Admin UI. The key components of this topology are:
 
```xml
<provider>
    <role>webappsec</role>
    <name>WebAppSec</name>
    <enabled>true</enabled>
    <param><name>csrf.enabled</name><value>true</value></param>
    <param><name>csrf.customHeader</name><value>X-XSRF-Header</value></param>
    <param><name>csrf.methodsToIgnore</name><value>GET,OPTIONS,HEAD</value></param>
    <param><name>xframe-options.enabled</name><value>true</value></param>
    <param><name>strict.transport.enabled</name><value>true</value></param>
</provider>
```
 
and 
 
```xml
<application>
    <role>admin-ui</role>
</application>
```


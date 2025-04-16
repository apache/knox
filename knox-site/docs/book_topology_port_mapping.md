#### Topology Port Mapping #####
This feature allows mapping of a topology to a port, as a result one can have a specific topology listening exclusively on a configured port. This feature 
routes URLs to these port-mapped topologies without the additional context that the gateway uses for differentiating from one Hadoop cluster to another,
just like the #[Default Topology URLs] feature, but on a dedicated port. 

NOTE: Once the topologies are configured to listen on a dedicated port they will not be available on the default gateway port.

The configuration for Topology Port Mapping goes in `gateway-site.xml` file. The configuration uses the property name and value model.
The format for the property name is `gateway.port.mapping.{topologyName}` and value is the port number that this
topology will listen on. 

In the following example, the topology `development` will listen on 9443 (if the port is not already taken).

      <property>
          <name>gateway.port.mapping.development</name>
          <value>9443</value>
          <description>Topology and Port mapping</description>
      </property>

An example of how one can access WebHDFS URL using the above configuration is

     https://{gateway-host}:9443/webhdfs
     https://{gateway-host}:9443/{gateway-path}/development/webhdfs


All of the above URL will be valid URLs for the above described configuration.

This feature is turned on by default. Use the property `gateway.port.mapping.enabled` to turn it on/off.
e.g.

     <property>
         <name>gateway.port.mapping.enabled</name>
         <value>true</value>
         <description>Enable/Disable port mapping feature.</description>
     </property>

If a topology mapped port is in use by another topology or a process, an ERROR message is logged and gateway startup continues as normal. 
Default gateway port cannot be used for port mapping, use #[Default Topology URLs] feature instead.

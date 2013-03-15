package org.apache.hadoop.gateway.provider.federation.jwt.filter;

import org.apache.commons.cli.ParseException;
import org.apache.hadoop.gateway.i18n.messages.Message;
import org.apache.hadoop.gateway.i18n.messages.MessageLevel;
import org.apache.hadoop.gateway.i18n.messages.Messages;
import org.apache.hadoop.gateway.i18n.messages.StackTrace;

import java.io.File;
import java.net.URI;

/**
 *
 */
@Messages(logger="org.apache.hadoop.gateway")
public interface JWTProviderMessages {

  @Message( level = MessageLevel.DEBUG, text = "Rendering JWT Token for the wire: {0}" )
  void renderingJWTTokenForTheWire(String string);

  @Message( level = MessageLevel.DEBUG, text = "Parsing JWT Token from the wire: {0}" )
  void parsingToken(String wireToken);

}

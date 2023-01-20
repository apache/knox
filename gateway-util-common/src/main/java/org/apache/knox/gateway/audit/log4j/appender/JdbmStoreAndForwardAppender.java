/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.audit.log4j.appender;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(
    name = "JdbmStoreAndForwardAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE)
public class JdbmStoreAndForwardAppender extends AbstractAppender {
  private Thread forwarder; //NOPMD - Expected use of threading
  private JdbmQueue<LogEvent> queue;
  private Logger forward;

  private JdbmStoreAndForwardAppender(String name, Filter filter, String file) {
    super(name, filter, null);
    try {
      queue = new JdbmQueue<>(new File(file));
    } catch ( IOException e ) {
      throw new IllegalStateException( e );
    }
    forward = (Logger)LogManager.getLogger( "audit.forward" );
    forward.setAdditive( false );
    forwarder = new Forwarder();
    forwarder.setDaemon( true );
    forwarder.start();
  }

  @PluginFactory
  public static JdbmStoreAndForwardAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginElement("Filter") Filter filter,
      @PluginAttribute("file") String file) {
    return new JdbmStoreAndForwardAppender(name, filter, file);
  }

  @Override
  public void append( LogEvent event ) {
    try {
      queue.enqueue( event );
    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }

  @Override
  public void stop() {
    try {
      queue.stop();
      forwarder.join();
      queue.close();
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
    super.stop();
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  private class Forwarder extends Thread {
    @Override
    public void run() {
      final AtomicBoolean done = new AtomicBoolean( false );
      while( !done.get() ) {
        try {
          queue.process(event -> {
            try {
              if( event == null ) {
                done.set( true );
              } else {
                for(Appender appender : forward.getAppenders().values()) {
                  appender.append(event);
                }
              }
              return true;
            } catch ( Exception e ) {
              e.printStackTrace();
              return false;
            }
          });
        } catch ( ThreadDeath e ) {
          throw e;
        } catch ( Throwable t ) {
          t.printStackTrace();
        }
      }
    }
  }
}

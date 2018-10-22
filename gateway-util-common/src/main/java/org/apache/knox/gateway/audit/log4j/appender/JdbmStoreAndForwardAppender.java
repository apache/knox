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

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

public class JdbmStoreAndForwardAppender extends AppenderSkeleton {

  private File file;
  private Thread forwarder;
  private JdbmQueue<LoggingEvent> queue;
  private Logger forward;
  private boolean fetchLocationInfo = true;

  @Override
  public boolean requiresLayout() {
    return false;
  }

  public void setFile( String file ) {
    this.file = new File( file );
  }

  public void setFetchLocationInfo( boolean fetchLocationInfo ) {
    this.fetchLocationInfo = fetchLocationInfo;
  }

  public boolean isFetchLocationInfo() {
    return fetchLocationInfo;
  }

  @Override
  public void activateOptions() {
    try {
      queue = new JdbmQueue<>( file );
    } catch ( IOException e ) {
      throw new IllegalStateException( e );
    }
    forward = Logger.getLogger( "audit.forward" );
    forward.setAdditivity( false );
    forwarder = new Forwarder();
    forwarder.setDaemon( true );
    forwarder.start();
  }

  @Override
  protected void append( LoggingEvent event ) {
    try {
      if( fetchLocationInfo ) {
        event.getLocationInformation();
      }
      queue.enqueue( event );
    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }

  @Override
  public void close() {
    try {
      queue.stop();
      forwarder.join();
      queue.close();
    } catch( InterruptedException e ) {
      throw new RuntimeException( e );
    } catch( IOException e ) {
      throw new RuntimeException( e );
    }
  }

  private class Forwarder extends Thread {

    public void run() {
      final AtomicBoolean done = new AtomicBoolean( false );
      while( !done.get() ) {
        try {
          queue.process( new JdbmQueue.Consumer<LoggingEvent>() {
            @Override
            public boolean consume( LoggingEvent event ) {
              try {
                if( event == null ) {
                  done.set( true );
                } else {
                  forward.callAppenders( event );
                }
                return true;
              } catch ( Exception e ) {
                e.printStackTrace();
                return false;
              }
            }
          } );
        } catch ( ThreadDeath e ) {
          throw e;
        } catch ( Throwable t ) {
          t.printStackTrace();
        }
      }
    }
  }

}

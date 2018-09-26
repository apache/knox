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

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.htree.HTree;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;

public class JdbmQueue<E> {

  private static final String STAT_NAME = "stat";
  private static final String DATA_TREE = "data";

  private RecordManager db;
  private long stat;
  private HTree data;
  private boolean open;

  public JdbmQueue( File file ) throws IOException {
    Properties props = new Properties();
    db = RecordManagerFactory.createRecordManager( file.getAbsolutePath(), props );
    stat = findStat();
    data = getData();
    db.commit();
    open = true;
  }

  public synchronized void enqueue( E e ) throws IOException {
    boolean committed = false;
    try {
      Stat stat = getStat();
      stat.lastEnqueue++;
      setStat( stat );
      data.put( stat.lastEnqueue, e );
      db.commit();
      committed = true;
      notify();
    } finally {
      if( !committed ) {
        db.rollback();
      }
    }
  }

  public synchronized E dequeue() throws InterruptedException, IOException {
    boolean committed = false;
    try {
      Stat s = getStat();
      while( open && s.size() == 0 ) {
        wait();
        if( !open ) {
          return null;
        }
        s = getStat();
      }
      s.nextDequeue++;
      Long key = Long.valueOf( s.nextDequeue );
      @SuppressWarnings("unchecked")
      E e = (E)data.get( key );
      data.remove( key );
      db.update( stat, s );
      db.commit();
      committed = true;
      return e;
    } finally {
      if( !committed && open ) {
        db.rollback();
      }
    }
  }

  public synchronized boolean process( Consumer<E> consumer ) throws IOException {
    boolean committed = false;
    try {
      E e = dequeue();
      boolean consumed = consumer.consume( e );
      if( consumed && open ) {
        db.commit();
        committed = true;
      }
    } catch( RuntimeException e ) {
      throw e;
    } catch( IOException e ) {
      throw e;
    } catch( Throwable t ) {
      throw new RuntimeException( t );
    } finally {
      if( !committed && open ) {
        db.rollback();
      }
    }
    return committed;
  }

  public synchronized void stop() {
    open = false;
    notifyAll();
  }

  public synchronized void close() throws IOException {
    stop();
    db.close();
  }

  long findStat() throws IOException {
    long recid = db.getNamedObject( STAT_NAME );
    if ( recid == 0 ) {
      recid = db.insert( new Stat() );
      db.setNamedObject( STAT_NAME, recid );
    }
    return recid;
  }

  Stat getStat() throws IOException {
    Stat stat;
    long recid = db.getNamedObject( STAT_NAME );
    if ( recid == 0 ) {
      stat = new Stat();
      db.setNamedObject( STAT_NAME, db.insert( stat ) );
    } else {
      stat = (Stat)db.fetch( recid );
    }
    return stat;
  }

  void setStat( Stat update ) throws IOException {
    db.update( stat, update );
  }

  HTree getData() throws IOException {
    HTree tree;
    long recid = db.getNamedObject( DATA_TREE );
    if ( recid != 0 ) {
      tree = HTree.load( db, recid );
    } else {
      tree = HTree.createInstance( db );
      db.setNamedObject( DATA_TREE, tree.getRecid() );
    }
    return tree;
  }

  private static final class Stat implements Serializable {

    private static final long serialVersionUID = 1L;
    private long lastEnqueue = 0;
    private long nextDequeue = 0;
    private long size() {
      return lastEnqueue - nextDequeue;
    }
  }

  public interface Consumer<E> {
    boolean consume( E e );
  }

}

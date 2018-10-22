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
package org.apache.knox.gateway.audit;

import org.apache.knox.gateway.audit.log4j.appender.JdbmQueue;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class JdbmQueueTest {

  private File file;
  private JdbmQueue<String> queue;

  @Before
  public void setup() throws IOException {
    file = new File( "target/JdbmQueueTest" );
    cleanup();
    queue = new JdbmQueue<>( file );
  }

  @After
  public void cleanup() throws IOException {
    if( queue != null ) {
      queue.close();
      queue = null;
    }
    LogManager.shutdown();
    String absolutePath = "target/audit";
    File db = new File( absolutePath + ".db" );
    if( db.exists() ) {
      assertThat( "Failed to delete audit store db file.", db.delete(), is( true ) );
    }
    File lg = new File( absolutePath + ".lg" );
    if( lg.exists() ) {
      assertThat( "Failed to delete audit store lg file.", lg.delete(), is( true ) );
    }
    PropertyConfigurator.configure( ClassLoader.getSystemResourceAsStream( "audit-log4j.properties" ) );
  }

  @Test
  public void testSimple() throws IOException, InterruptedException {
    System.out.println( "Running " + Thread.currentThread().getStackTrace()[1].getClassName() + "#" + Thread.currentThread().getStackTrace()[1].getMethodName() );
    String one = UUID.randomUUID().toString();
    String two = UUID.randomUUID().toString();
    String three = UUID.randomUUID().toString();
    String four = UUID.randomUUID().toString();
    queue.enqueue( one );
    assertThat( queue.dequeue(), is( one ) );
    queue.enqueue( two );
    queue.enqueue( three );
    assertThat( queue.dequeue(), is( two ) );
    assertThat( queue.dequeue(), is( three ) );

    final AtomicInteger counter = new AtomicInteger( 0 );
    queue.enqueue( four );
    queue.process( new JdbmQueue.Consumer<String>() {
      @Override
      public boolean consume( String s ) {
        counter.incrementAndGet();
        return true;
      }
    } );
    assertThat( counter.get(), is( 1 ) );
  }

//  @Ignore
//  @Test
//  public void testPerformanceAndStorageFootprint() throws IOException, InterruptedException {
//    System.out.println( "Running " + Thread.currentThread().getStackTrace()[1].getClassName() + "#" + Thread.currentThread().getStackTrace()[1].getMethodName() );
//
//    String fill = createFillString( 100 );
//    File dbFile = new File( file.getAbsolutePath() + ".db" );
//    File lgFile = new File( file.getAbsolutePath() + ".lg" );
//
//    String s = null;
//    long writeCount = 0;
//    long writeTime = 0;
//    long before;
//
//    int iterations = 10000;
//
//    for( int i=0; i<iterations; i++ ) {
//      s = UUID.randomUUID().toString() + ":" + fill;
//      before = System.currentTimeMillis();
//      queue.enqueue( s );
//      writeTime += ( System.currentTimeMillis() - before );
//      writeCount++;
//    }
//
//    System.out.println( String.format( "Line: len=%d", s.length() ) );
//    System.out.println( String.format( "Perf: avg=%.4fs, tot=%.2fs, cnt=%d", ( (double)writeTime / (double)writeCount / 1000.0 ),  (double)writeTime/1000.0, writeCount ) );
//    System.out.println( String.format(
//        "File: db=%s, lg=%s, tot=%s, per=%s",
//        humanReadableSize( dbFile.length() ),
//        humanReadableSize( lgFile.length() ),
//        humanReadableSize( dbFile.length() + lgFile.length() ),
//        humanReadableSize( ( ( dbFile.length() + lgFile.length() ) / writeCount ) ) ) );
//  }

//  @Ignore
//  @Test
//  public void testFileGrowth() throws IOException, InterruptedException {
//    System.out.println( "Running " + Thread.currentThread().getStackTrace()[1].getClassName() + "#" + Thread.currentThread().getStackTrace()[1].getMethodName() );
//
//    String fill = createFillString( 100 );
//    File dbFile = new File( file.getAbsolutePath() + ".db" );
//    File lgFile = new File( file.getAbsolutePath() + ".lg" );
//
//    String s = null;
//    long writeCount = 0;
//    long writeTime = 0;
//    long before;
//
//    int iterations = 10000;
//
//    for( int i=0; i<iterations; i++ ) {
//      s = UUID.randomUUID().toString() + ":" + fill;
//      before = System.currentTimeMillis();
//      queue.enqueue( s );
//      assertThat( queue.dequeue(), is( s ) );
//      writeTime += ( System.currentTimeMillis() - before );
//      writeCount++;
//    }
//
//    System.out.println( String.format( "Line: len=%d", s.length() ) );
//    System.out.println( String.format( "Perf: avg=%.4fs, tot=%.2fs, cnt=%d", ( (double)writeTime / (double)writeCount / 1000.0 ),  (double)writeTime/1000.0, writeCount ) );
//    System.out.println( String.format(
//        "File: db=%s, lg=%s, tot=%s, per=%s",
//        humanReadableSize( dbFile.length() ),
//        humanReadableSize( lgFile.length() ),
//        humanReadableSize( dbFile.length() + lgFile.length() ),
//        humanReadableSize( ( ( dbFile.length() + lgFile.length() ) / writeCount ) ) ) );
//  }

  @Test( timeout = 120000 )
  public void testConcurrentConsumer() throws InterruptedException, IOException {
    System.out.println( "Running " + Thread.currentThread().getStackTrace()[1].getClassName() + "#" + Thread.currentThread().getStackTrace()[1].getMethodName() );

    int iterations = 100;
    HashSet<String> consumed = new HashSet<>();
    Consumer consumer = new Consumer( consumed );
    consumer.start();
    Producer producer1 = new Producer( iterations );
    producer1.start();
    Producer producer2 = new Producer( iterations );
    producer2.start();
    producer1.join();
    producer2.join();
    while (consumed.size() < iterations * 2) {
      Thread.sleep( 5 );
    }
    queue.stop();
    consumer.join();
    assertThat( consumed, hasSize( iterations * 2 ) );
  }

  @Test( timeout=120000 )
  public void testConcurrentProcessor() throws InterruptedException, IOException {
    System.out.println( "Running " + Thread.currentThread().getStackTrace()[1].getClassName() + "#" + Thread.currentThread().getStackTrace()[1].getMethodName() );

    int iterations = 100;
    HashSet<String> consumed = new HashSet<>();
    Processor consumer = new Processor( consumed );
    consumer.start();
    Producer producer1 = new Producer( iterations );
    producer1.start();
    Producer producer2 = new Producer( iterations );
    producer2.start();
    producer1.join();
    producer2.join();
    while (consumed.size() < iterations * 2) {
      Thread.sleep( 5 );
    }
    queue.stop();
    consumer.join();
    assertThat( consumed, hasSize( iterations * 2 ) );
  }

  public class Producer extends Thread {
    public int iterations;
    public Producer( int iterations ) {
      this.iterations = iterations;
    }
    public void run() {
      try {
        for( int i = 0; i < iterations; i++ ) {
          queue.enqueue( UUID.randomUUID().toString() );
        }
      } catch ( Throwable t ) {
        t.printStackTrace();
      }
    }
  }

  public class Consumer extends Thread {
    public Set<String> consumed;
    public Consumer( Set<String> consumed ) {
      this.consumed = consumed;
    }
    public void run() {
      try {
        while( true ) {
          String s = queue.dequeue();
          if( s == null ) {
            return;
          } else if( consumed.contains( s ) ) {
            System.out.println( "DUPLICATE " + s );
            System.exit( 1 );
          } else {
            consumed.add( s );
          }
        }
      } catch ( Throwable t ) {
        t.printStackTrace();
      }
    }
  }

  public class Processor extends Thread {
    public Set<String> consumed;
    public Processor( Set<String> consumed ) {
      this.consumed = consumed;
    }
    public void run() {
      try {
        final AtomicBoolean done = new AtomicBoolean( false );
        while( !done.get() ) {
          queue.process( new JdbmQueue.Consumer<String>() {
            @Override
            public boolean consume( String s ) {
              if( s == null ) {
                done.set( true );
              } else if( consumed.contains( s ) ) {
                System.out.println( "DUPLICATE " + s );
                System.exit( 1 );
              } else {
                consumed.add( s );
              }
              return true;
            }
          } );
        }
      } catch ( Throwable t ) {
        t.printStackTrace();
      }
    }
  }

  public static String humanReadableSize( long size ) {
    if(size <= 0) return "0";
    final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
    int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
    return new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.getDefault()))
        .format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
  }

  public static String createFillString( int size ) {
    StringBuilder s = new StringBuilder();
    for( int i=0; i<size; i++ ) {
      s.append( UUID.randomUUID().toString() );
      s.append( "+" );
    }
    return s.toString();
  }

}

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
package org.apache.knox.gateway.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;

import org.eclipse.jetty.util.BlockingArrayQueue;

/**
 * A Test websocket client
 *
 */
@ClientEndpoint
public class WebsocketClient {

  private Session session;
  public CloseReason close;
  public MessageQueue messageQueue = new MessageQueue();
  public List<Throwable> errors = new LinkedList<>();
  public CountDownLatch closeLatch = new CountDownLatch(1);

  public boolean onError;
  public String errorMessage;

  @OnClose
  public void onWebSocketClose(CloseReason reason) {
    this.close = reason;
    closeLatch.countDown();
  }

  @OnMessage
  public void onMessage(String message) {
    this.messageQueue.offer(message);
  }

  @OnMessage
  public void onMessage(PongMessage message) {
    ByteBuffer byteMessage = message.getApplicationData();
    String s = StandardCharsets.UTF_8.decode(byteMessage).toString();
    this.messageQueue.offer(s);
  }

  @OnOpen
  public void onOpen(Session session) {
    this.session = session;
  }

  @OnError
  public void onError(Session session, Throwable thr) {
    errors.add(thr);
    this.onError = true;
    this.errorMessage = thr.toString();
  }

  public void sendText(String text) throws IOException {
    if (session != null) {
      session.getBasicRemote().sendText(text);
    }
  }

  /**
   * Check whether we have expected close code
   *
   * @param expectedCloseCode code to expect on close
   * @param timeoutDuration duration to wait
   * @param timeoutUnit duration unit
   * @throws TimeoutException if waiting too long to close
   */
  public void awaitClose(int expectedCloseCode, int timeoutDuration, TimeUnit timeoutUnit)
      throws TimeoutException {

    long msDur = TimeUnit.MILLISECONDS.convert(timeoutDuration, timeoutUnit);
    long now = System.currentTimeMillis();
    long expireOn = now + msDur;

    while (close == null) {
      try {
        TimeUnit.MILLISECONDS.sleep(10);
      } catch (InterruptedException ignore) {
        /* ignore */
      }
      if ((System.currentTimeMillis() > expireOn)) {
        throw new TimeoutException("Timed out reading message from queue");
      }

    }

  }

  public class MessageQueue extends BlockingArrayQueue<String> {

    public void awaitMessages(int expectedMessageCount, int timeoutDuration,
        TimeUnit timeoutUnit) throws TimeoutException {
      long msDur = TimeUnit.MILLISECONDS.convert(timeoutDuration, timeoutUnit);
      long now = System.currentTimeMillis();
      long expireOn = now + msDur;

      while (this.size() < expectedMessageCount) {
        try {
          TimeUnit.MILLISECONDS.sleep(20);
        } catch (InterruptedException ignore) {
          /* ignore */
        }
        if ((System.currentTimeMillis() > expireOn)) {
          throw new TimeoutException("Timed out reading message from queue");
        }

      }
    }
  }
}

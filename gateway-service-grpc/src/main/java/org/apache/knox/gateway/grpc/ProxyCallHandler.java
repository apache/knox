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
package org.apache.knox.gateway.grpc;

import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Pipes one gRPC call through to a backend, relaying messages, headers, status
 * and trailers in both directions.
 * <p>
 * All four RPC shapes collapse into this one handler. Unary, server-streaming,
 * client-streaming and bidirectional calls differ only in how many messages flow
 * each way, which the listener callbacks below express naturally — so there is
 * no need for a handler per shape: a long-lived server stream works by the same
 * code path as a unary call.
 * <p>
 * Backend {@link Status} and trailers are relayed verbatim. This matters more
 * than it might appear: gRPC carries {@code grpc-status} in trailers, and
 * protocols commonly pack structured error details in there too, so anything
 * that interprets or drops trailers breaks error reporting wholesale.
 * <p>
 * Flow control is explicit in both directions: a message is only requested from
 * one side once the other side has accepted the previous one, so a slow
 * {@code sc://} client cannot make the gateway buffer an unbounded number of
 * Arrow batches. This follows the flow-control structure of the upstream
 * grpc-java {@code GrpcProxy} example.
 *
 * @param <ReqT> the request message type
 * @param <RespT> the response message type
 */
public class ProxyCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {

  private final BackendChannelProvider channelProvider;
  private final MessageInterceptor<ReqT> messageInterceptor;
  private final HeaderRewriter headerRewriter;

  public ProxyCallHandler(BackendChannelProvider channelProvider,
                          MessageInterceptor<ReqT> messageInterceptor,
                          HeaderRewriter headerRewriter) {
    this.channelProvider = channelProvider;
    this.messageInterceptor = messageInterceptor;
    this.headerRewriter = headerRewriter;
  }

  @Override
  public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> serverCall, Metadata headers) {
    final Channel channel;
    try {
      channel = channelProvider.getChannel();
    } catch (StatusRuntimeException e) {
      serverCall.close(e.getStatus(), e.getTrailers() == null ? new Metadata() : e.getTrailers());
      return new ServerCall.Listener<ReqT>() { };
    } catch (Exception e) {
      serverCall.close(Status.UNAVAILABLE.withDescription("No backend available").withCause(e), new Metadata());
      return new ServerCall.Listener<ReqT>() { };
    }

    headerRewriter.rewrite(headers);

    final ClientCall<ReqT, RespT> clientCall =
        channel.newCall(serverCall.getMethodDescriptor(), CallOptions.DEFAULT);
    final CallProxy proxy = new CallProxy(serverCall, clientCall);
    clientCall.start(proxy.clientCallListener, headers);

    // Prime both directions with a single outstanding message; each side then
    // requests the next only when the other has taken the previous one.
    serverCall.request(1);
    clientCall.request(1);
    return proxy.serverCallListener;
  }

  /**
   * Holds the two halves of the relay and the shared close latch. The client
   * half forwards requests to the backend; the server half forwards responses
   * back.
   */
  private final class CallProxy {

    private final RequestProxy serverCallListener;
    private final ResponseProxy clientCallListener;
    /**
     * A {@link ServerCall} may only be closed once, and both halves can race to
     * close it: the backend can fail at the same moment a message interceptor
     * rejects a request. Whoever wins reports the status.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    CallProxy(ServerCall<ReqT, RespT> serverCall, ClientCall<ReqT, RespT> clientCall) {
      this.serverCallListener = new RequestProxy(clientCall);
      this.clientCallListener = new ResponseProxy(serverCall);
    }

    private void closeServerCall(ServerCall<ReqT, RespT> serverCall, Status status, Metadata trailers) {
      if (closed.compareAndSet(false, true)) {
        serverCall.close(status, trailers);
      }
    }

    /** Relays the client's request stream to the backend. */
    private final class RequestProxy extends ServerCall.Listener<ReqT> {

      private final ClientCall<ReqT, ?> clientCall;
      /** Guarded by {@code this}: a request is owed once the backend is writable again. */
      private boolean needToRequest;

      RequestProxy(ClientCall<ReqT, ?> clientCall) {
        this.clientCall = clientCall;
      }

      @Override
      public void onCancel() {
        clientCall.cancel("Cancelled by client", null);
      }

      @Override
      public void onHalfClose() {
        clientCall.halfClose();
      }

      @Override
      public void onMessage(ReqT message) {
        final ReqT forwarded;
        try {
          forwarded = messageInterceptor.intercept(message);
        } catch (StatusRuntimeException e) {
          // A gating decision, e.g. AddArtifacts denied or a write to a reserved
          // config key. Reject without the backend ever seeing the message.
          //
          // Close the client first, then cancel the backend. Cancelling first
          // would race: the backend's own onClose fires with CANCELLED — on a
          // direct executor, synchronously — and would win the close latch, so
          // the caller would see CANCELLED instead of why they were denied.
          closeServerCall(clientCallListener.serverCall, e.getStatus(),
              e.getTrailers() == null ? new Metadata() : e.getTrailers());
          clientCall.cancel(e.getStatus().getDescription(), e);
          return;
        }

        clientCall.sendMessage(forwarded);
        synchronized (this) {
          if (clientCall.isReady()) {
            clientCallListener.serverCall.request(1);
          } else {
            // The backend is not writable; wait for onClientReady rather than
            // pulling more from the client and buffering it here.
            needToRequest = true;
          }
        }
      }

      @Override
      public void onReady() {
        clientCallListener.onServerReady();
      }

      synchronized void onClientReady() {
        if (needToRequest) {
          clientCallListener.serverCall.request(1);
          needToRequest = false;
        }
      }
    }

    /** Relays the backend's response stream to the client. */
    private final class ResponseProxy extends ClientCall.Listener<RespT> {

      private final ServerCall<ReqT, RespT> serverCall;
      /** Guarded by {@code this}: a request is owed once the client is writable again. */
      private boolean needToRequest;

      ResponseProxy(ServerCall<ReqT, RespT> serverCall) {
        this.serverCall = serverCall;
      }

      @Override
      public void onClose(Status status, Metadata trailers) {
        closeServerCall(serverCall, status, trailers);
      }

      @Override
      public void onHeaders(Metadata headers) {
        serverCall.sendHeaders(headers);
      }

      @Override
      public void onMessage(RespT message) {
        serverCall.sendMessage(message);
        synchronized (this) {
          if (serverCall.isReady()) {
            serverCallListener.clientCall.request(1);
          } else {
            needToRequest = true;
          }
        }
      }

      @Override
      public void onReady() {
        serverCallListener.onClientReady();
      }

      synchronized void onServerReady() {
        if (needToRequest) {
          serverCallListener.clientCall.request(1);
          needToRequest = false;
        }
      }
    }
  }
}

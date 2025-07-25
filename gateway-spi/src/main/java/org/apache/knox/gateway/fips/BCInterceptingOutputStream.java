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
package org.apache.knox.gateway.fips;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

public class BCInterceptingOutputStream extends OutputStream {

    private static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);
    private final OutputStream delegate;
    private static final String BOUNCYCASTLE_TLS_PROTOCOL_NAME = "org.bouncycastle.tls.TlsProtocol";
    private static final String BOUNCYCASTLE_HANDLE_CLOSE_METHOD = "handleClose";
    private static final String BROKEN_PIPE_MESSAGE = "Broken pipe";

    public BCInterceptingOutputStream(OutputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        try {
            delegate.write(b);
        } catch (SocketException e) {
            handleSocketException(e);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            delegate.write(b);
        } catch (SocketException e) {
            handleSocketException(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            delegate.write(b, off, len);
        } catch (SocketException e) {
            handleSocketException(e);
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * This method swallows the socketException if org.bouncycastle.tls.TlsProtocol.handleClose method is in the stacktrace.
     * This exception is thrown with 'Broken Pipe' message on FIPS environments where the BouncyCastle provider is used.
     * @param socketException The exception thrown
     * @throws SocketException The exception is thrown again if it's not the BouncyCastle one.
     */
    private void handleSocketException(SocketException socketException) throws SocketException {
        boolean exceptionIgnored = false;
        if (socketException.getMessage() != null && StringUtils.containsIgnoreCase(socketException.getMessage(), BROKEN_PIPE_MESSAGE)) {
            StackTraceElement[] stackTrace = socketException.getStackTrace();
            for (StackTraceElement ste : stackTrace) {
                if (BOUNCYCASTLE_TLS_PROTOCOL_NAME.equals(ste.getClassName())) {
                    if (BOUNCYCASTLE_HANDLE_CLOSE_METHOD.equals(ste.getMethodName())) {
                        LOG.ignoreHandleCloseError();
                        exceptionIgnored = true;
                    }
                }
            }
        }
        if (!exceptionIgnored) {
            throw socketException;
        }
    }
}

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

import org.easymock.EasyMock;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

public class BCInterceptingOutputStreamTest {

    @Test
    public void writeExceptionIgnoredTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(10);
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Broken pipe (Write failed)").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(10);
    }

    @Test
    public void writeByteArrayExceptionIgnoredTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(new byte[]{10});
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Broken pipe (Write failed)").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(new byte[]{10});
    }

    @Test
    public void writeByteArrayOffsetExceptionIgnoredTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(new byte[]{10}, 10, 10);
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Broken pipe (Write failed)").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(new byte[]{10}, 10, 10);
    }

    @Test
    public void writeExceptionIgnoredJDK17Test() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(10);
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Broken pipe").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(10);
    }

    @Test(expected = SocketException.class)
    public void writeDifferentMessageTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(10);
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Non Broken message (Write failed)").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(10);
    }

    @Test(expected = SocketException.class)
    public void writeDifferentClassNameTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.NonTlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(10);
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Broken pipe (Write failed)").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(10);
    }

    @Test(expected = SocketException.class)
    public void writeDifferentMethodNameTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        SocketException socketException = EasyMock.createNiceMock(SocketException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "nonHandleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(10);
        EasyMock.expectLastCall().andThrow(socketException);
        EasyMock.expect(socketException.getMessage()).andReturn("Broken pipe (Write failed)").times(2);
        EasyMock.expect(socketException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, socketException);

        BCInterceptingOutputStream.write(10);
    }

    @Test(expected = IOException.class)
    public void writeNonSocketExceptionTest() throws IOException {
        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        BCInterceptingOutputStream BCInterceptingOutputStream =
                new BCInterceptingOutputStream(outputStream);
        IOException ioException = EasyMock.createNiceMock(IOException.class);
        StackTraceElement ste = new StackTraceElement("org.bouncycastle.tls.TlsProtocol", "handleClose", null, 1);
        StackTraceElement[] steArray = new StackTraceElement[1];
        steArray[0] = ste;

        outputStream.write(10);
        EasyMock.expectLastCall().andThrow(ioException);
        EasyMock.expect(ioException.getMessage()).andReturn("Broken pipe (Write failed)").times(2);
        EasyMock.expect(ioException.getStackTrace()).andReturn(steArray);
        EasyMock.replay(outputStream, ioException);

        BCInterceptingOutputStream.write(10);
    }
}

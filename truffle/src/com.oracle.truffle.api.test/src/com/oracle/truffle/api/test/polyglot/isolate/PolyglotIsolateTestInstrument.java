/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot.isolate;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.logging.Level;

@Registration(id = PolyglotIsolateTestInstrument.ID, name = PolyglotIsolateTestInstrument.ID)
public class PolyglotIsolateTestInstrument extends TruffleInstrument {

    static final String ID = "tristetool";

    @Option(help = "Connects to URL.", category = OptionCategory.EXPERT) static final OptionKey<String> Connect = new OptionKey<>("");
    @Option(help = "Fail on create with given message.", category = OptionCategory.EXPERT) static final OptionKey<String> Fail = new OptionKey<>("");

    @Override
    protected void onCreate(Env env) {
        String failureMessage = Fail.getValue(env.getOptions());
        if (!failureMessage.isEmpty()) {
            throw new InstrumentException(failureMessage);
        }
        String uri = Connect.getValue(env.getOptions());
        if (!uri.isEmpty()) {
            try {
                MessageEndpointImpl localEndPoint = new MessageEndpointImpl();
                MessageEndpoint peerEndPoint = env.startServer(URI.create(uri), localEndPoint);
                localEndPoint.setPeer(peerEndPoint);
            } catch (IOException | MessageTransport.VetoException exception) {
                env.getLogger("").log(Level.WARNING, exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new PolyglotIsolateTestInstrumentOptionDescriptors();
    }

    /**
     * Mock {@link MessageEndpoint} implementation doing echo.
     */
    private static final class MessageEndpointImpl implements MessageEndpoint {

        private volatile boolean closed;
        private volatile MessageEndpoint peer;

        @Override
        public void sendText(String text) throws IOException {
            checkClosed();
            peer.sendText(text);
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            checkClosed();
            peer.sendBinary(data);
        }

        @Override
        public void sendPing(ByteBuffer data) {
        }

        @Override
        public void sendPong(ByteBuffer data) {
        }

        @Override
        public void sendClose() {
            close();
        }

        void close() {
            closed = true;
        }

        void setPeer(MessageEndpoint peerEndPoint) {
            this.peer = peerEndPoint;
        }

        private void checkClosed() throws IOException {
            if (closed) {
                throw new IOException("Closed endpoint.");
            }
        }
    }
}

/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.interop;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.test.interop.values.StructObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class SlowPathCallTest extends InteropTestBase {

    private static TruffleObject[] function;
    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = InteropTestBase.loadTestBitcodeInternal("interopSlowpathCall");
    }

    public class TestSlow extends RootNode {

        @Child private Node execute;

        public TestSlow() {
            super(null);
            try {
                function = new TruffleObject[]{
                                (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "get_a"),
                                (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "get_b"),
                                (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "get_c"),
                                (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "get_d"),
                                (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "get_e")
                };
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
            execute = Message.EXECUTE.createNode();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                Object a = ForeignAccess.sendExecute(execute, function[0], frame.getArguments());
                Object b = ForeignAccess.sendExecute(execute, function[1], frame.getArguments());
                Object c = ForeignAccess.sendExecute(execute, function[2], frame.getArguments());
                Object d = ForeignAccess.sendExecute(execute, function[3], frame.getArguments());
                Object e = ForeignAccess.sendExecute(execute, function[4], frame.getArguments());
                assertResult(a, b, c, d, e);
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
            return null;
        }

        @CompilerDirectives.TruffleBoundary
        private void assertResult(Object a, Object b, Object c, Object d, Object e) {
            Map<String, Object> expected = makeObjectSlowpathCall();
            Assert.assertEquals(expected.get("a"), a);
            Assert.assertEquals(expected.get("b"), b);
            Assert.assertEquals(expected.get("c"), c);
            Assert.assertEquals(expected.get("d"), d);
            Assert.assertEquals(expected.get("e"), e);
        }

    }

    @Test
    public void testReadFromDerefHandle(@Inject(TestSlow.class) CallTarget accessDerefHandle) {
        Map<String, Object> members = makeObjectSlowpathCall();
        accessDerefHandle.call(new StructObject(members));
    }

    private static Map<String, Object> makeObjectSlowpathCall() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("a", 2);
        values.put("b", 3);
        values.put("c", 5);
        values.put("d", 7);
        values.put("e", 11);
        return values;
    }
}

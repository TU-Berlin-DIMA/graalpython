/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = {PBufferedWriter, PBufferedRandom})
public class BufferedWriterMixinBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedWriterMixinBuiltinsFactory.getFactories();
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getRaw(), WRITABLE);
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryWithInitErrorClinicBuiltinNode {

        @Specialization(guards = "self.isOK()")
        static Object write(@SuppressWarnings("unused") VirtualFrame frame, PBuffered self, Object buffer,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached BufferedIONodes.EnterBufferedNode lock,
                        @Cached("create(WRITE)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedWriterNodes.WriteNode writeNode) {
            try {
                lock.enter(self);
                checkIsClosedNode.execute(frame, self);
                return writeNode.execute(frame, self, buffer);
            } finally {
                bufferLib.release(buffer);
                BufferedIONodes.EnterBufferedNode.leave(self);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedWriterMixinBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached BufferedIONodes.EnterBufferedNode lock,
                        @Cached("create(FLUSH)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(self);
                flushAndRewindUnlockedNode.execute(frame, self);
            } finally {
                BufferedIONodes.EnterBufferedNode.leave(self);
            }
            return PNone.NONE;
        }
    }
}

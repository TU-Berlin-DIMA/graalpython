/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import java.io.IOException;

import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class WriteUnraisableNode extends Node {
    public final void execute(VirtualFrame frame, PBaseException exception, String message, Object object) {
        executeInternal(frame, exception, message, object);
    }

    public final void execute(PBaseException exception, String message, Object object) {
        executeInternal(null, exception, message, object);
    }

    protected abstract void executeInternal(Frame frame, PBaseException exception, String message, Object object);

    @Specialization
    static void writeUnraisable(VirtualFrame frame, PBaseException exception, String message, Object object,
                    @Cached PyObjectLookupAttr lookup,
                    @Cached CallNode callNode,
                    @Cached GetClassNode getClassNode,
                    @Cached PythonObjectFactory factory,
                    @Cached GetExceptionTracebackNode getExceptionTracebackNode) {
        PythonContext context = PythonContext.get(getClassNode);
        try {
            PythonModule sysModule = context.getCore().lookupBuiltinModule("sys");
            Object unraisablehook = lookup.execute(frame, sysModule, BuiltinNames.UNRAISABLEHOOK);
            Object exceptionType = getClassNode.execute(exception);
            Object traceback = getExceptionTracebackNode.execute(exception);
            if (traceback == null) {
                traceback = PNone.NONE;
            }
            Object messageObj = PNone.NONE;
            if (message != null) {
                messageObj = formatMessage(message);
            }
            Object hookArguments = factory.createStructSeq(SysModuleBuiltins.UNRAISABLE_HOOK_ARGS_DESC, exceptionType, exception, traceback, messageObj, object != null ? object : PNone.NONE);
            callNode.execute(frame, unraisablehook, hookArguments);
        } catch (PException e) {
            ignoreException(context, message);
        }
    }

    @TruffleBoundary
    private static void ignoreException(PythonContext context, String message) {
        try {
            if (message != null) {
                context.getEnv().err().write(formatMessage(message).getBytes());
            } else {
                context.getEnv().err().write("Exception ignored in sys.unraisablehook".getBytes());
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    @TruffleBoundary
    private static String formatMessage(String message) {
        return "Exception ignored " + message;
    }

    public static WriteUnraisableNode create() {
        return WriteUnraisableNodeGen.create();
    }

    public static WriteUnraisableNode getUncached() {
        return WriteUnraisableNodeGen.getUncached();
    }
}

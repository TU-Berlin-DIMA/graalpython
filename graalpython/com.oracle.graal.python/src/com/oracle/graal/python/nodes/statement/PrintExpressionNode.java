/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.BuiltinNames.DISPLAYHOOK;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class PrintExpressionNode extends ExpressionNode {
    @Child IsBuiltinClassProfile exceptionTypeProfile;
    @Child PRaiseNode raiseNode;
    @Child GetAttributeNode getAttribute = GetAttributeNode.create(DISPLAYHOOK);
    @Child CallNode callNode = CallNode.create();
    @Child ExpressionNode valueNode;

    public PrintExpressionNode(ExpressionNode valueNode) {
        this.valueNode = valueNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = valueNode.execute(frame);
        PythonContext context = PythonContext.get(this);
        PythonModule sysModule = context.getCore().lookupBuiltinModule("sys");
        Object displayhook;
        try {
            displayhook = getAttribute.executeObject(frame, sysModule);
        } catch (PException ex) {
            if (ensureExceptionTypeProfile().profileException(ex, PythonBuiltinClassType.AttributeError)) {
                throw ensureRaiseNode().raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.LOST_SYSDISPLAYHOOK);
            } else {
                throw ex;
            }
        }
        callNode.execute(frame, displayhook, value);
        return PNone.NONE;
    }

    public static PrintExpressionNode create(ExpressionNode valueNode) {
        return new PrintExpressionNode(valueNode);
    }

    public IsBuiltinClassProfile ensureExceptionTypeProfile() {
        if (exceptionTypeProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            exceptionTypeProfile = insert(IsBuiltinClassProfile.create());
        }
        return exceptionTypeProfile;
    }

    public PRaiseNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }
}

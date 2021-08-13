/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
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
package com.oracle.graal.python.nodes.expression;

import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNodeGen.IsNodeGen;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

public abstract class BinaryComparisonNode extends BinaryOpNode {

    protected final String magicMethod;
    protected final String magicReverseMethod;
    private final String operation;
    protected final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    @Child
    private LookupAndCallBinaryNode callNode;
    @Child
    private PRaiseNode raiseNode;
    @Child
    private IsNode isNode;

    BinaryComparisonNode(String magicMethod, String magicReverseMethod, String operation) {
        this.magicMethod = magicMethod;
        this.magicReverseMethod = magicReverseMethod;
        this.operation = operation;
        // see cpython://Objects/object.c#do_richcompare - CPython always calls the reverse method
        this.callNode = LookupAndCallBinaryNode.create(magicMethod, magicReverseMethod, true, true);
    }

    public static BinaryComparisonNode create(String magicMethod, String magicReverseMethod, String operation, ExpressionNode left, ExpressionNode right) {
        return BinaryComparisonNodeGen.create(magicMethod, magicReverseMethod, operation, left, right);
    }

    public static BinaryComparisonNode create(String magicMethod, String magicReverseMethod, String operation) {
        return create(magicMethod, magicReverseMethod, operation, null, null);
    }

    public abstract boolean executeBool(VirtualFrame frame, Object left, Object right) throws UnexpectedResultException;

    private boolean handleNotImplemented(Object left, Object right) {
        // just like python, if no implementation is available, do something sensible for
        // == and !=
        if (magicMethod == __EQ__) {
            return ensureIsNode().execute(left, right);
        } else if (magicMethod == __NE__) {
            return !ensureIsNode().execute(left, right);
        } else {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, operation, left, right);
        }
    }

    private static int asInt(boolean left) {
        return left ? 1 : 0;
    }

    private static long asLong(boolean left) {
        return left ? 1L : 0L;
    }

    private static double asDouble(boolean left) {
        return left ? 1.0 : 0.0;
    }

    private boolean profileCondition(boolean value) {
        return profile.profile(value);
    }

    @Specialization
    boolean doBB(VirtualFrame frame, boolean left, boolean right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doBI(VirtualFrame frame, boolean left, int right) {
        try {
            return profileCondition(callNode.executeBool(frame, asInt(left), right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doBL(VirtualFrame frame, boolean left, long right) {
        try {
            return profileCondition(callNode.executeBool(frame, asLong(left), right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doBD(VirtualFrame frame, boolean left, double right) {
        try {
            return profileCondition(callNode.executeBool(frame, asDouble(left), right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doIB(VirtualFrame frame, int left, boolean right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, asInt(right)));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doII(VirtualFrame frame, int left, int right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doIL(VirtualFrame frame, int left, long right) {
        try {
            return profileCondition(callNode.executeBool(frame, (long) left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doID(VirtualFrame frame, int left, double right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doLB(VirtualFrame frame, long left, boolean right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, asLong(right)));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doLI(VirtualFrame frame, long left, int right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, (long) right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doLI(VirtualFrame frame, long left, long right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doLI(VirtualFrame frame, long left, double right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doDB(VirtualFrame frame, double left, boolean right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, asDouble(right)));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doDI(VirtualFrame frame, double left, int right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doDL(VirtualFrame frame, double left, long right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }

    @Specialization
    boolean doDD(VirtualFrame frame, double left, double right) {
        try {
            return profileCondition(callNode.executeBool(frame, left, right));
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Comparison on primitive values didn't return a boolean");
        }
    }



    @Specialization(guards = {"lib.isBfNode(left) || lib.isBfNode(right)"})
    Object doInteropt(
            Object left,
            Object right,
            @CachedLibrary(limit = "30") InteropLibrary lib) {
        return lib.executeBinaryOperation(left, right, operation);
    }

    @Specialization
    Object doGeneric(VirtualFrame frame, Object left, Object right) {
        Object result = callNode.executeObject(frame, left, right);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return handleNotImplemented(left, right);
        }
        return result;
    }

    private IsNode ensureIsNode() {
        if (isNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isNode = insert(IsNodeGen.create());
        }
        return isNode;
    }
}

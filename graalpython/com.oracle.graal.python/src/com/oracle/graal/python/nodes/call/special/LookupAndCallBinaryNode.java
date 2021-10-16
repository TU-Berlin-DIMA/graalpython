/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

// cpython://Objects/abstract.c#binary_op1
// Order operations are tried until either a valid result or error: w.op(v,w)[*], v.op(v,w), w.op(v,w)
//
//       [*] only when v->ob_type != w->ob_type && w->ob_type is a subclass of v->ob_type
//
// The (long, double) and (double, long) specializations are needed since long->double conversion
// is not always correct (it can lose information). See FloatBuiltins.EqNode.compareDoubleToLong().
// The (int, double) and (double, int) specializations are needed to avoid int->long conversion.
// Although it would produce correct results, the special handling of long to double comparison
// is slower than converting int->double, which is always correct.
@ReportPolymorphism
@ImportStatic(PythonOptions.class)
public abstract class LookupAndCallBinaryNode extends Node {

    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object arg, Object arg2);
    }

    protected final String name;
    protected final String rname;
    protected final Supplier<NotImplementedHandler> handlerFactory;
    protected final boolean ignoreDescriptorException;
    private final boolean alwaysCheckReverse;

    @Child private PRaiseNode raiseNode;
    @Child private GetNameNode getNameNode;
    @Child private CallBinaryMethodNode dispatchNode;
    @Child private CallBinaryMethodNode reverseDispatchNode;
    @Child private NotImplementedHandler handler;

    public abstract boolean executeBool(VirtualFrame frame, boolean arg, boolean arg2) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, long arg, long arg2) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, int arg, double arg2) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, double arg, int arg2) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, long arg, double arg2) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, double arg, long arg2) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, double arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, int arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, double arg, int arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, long arg, long arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, long arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, double arg, long arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, double arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract Object executeObject(VirtualFrame frame, Object arg, Object arg2);

    LookupAndCallBinaryNode(String name, String rname, Supplier<NotImplementedHandler> handlerFactory, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        this.name = name;
        this.rname = rname;
        this.handlerFactory = handlerFactory;
        this.alwaysCheckReverse = alwaysCheckReverse;
        this.ignoreDescriptorException = ignoreDescriptorException;
    }

    public static LookupAndCallBinaryNode create(String name) {
        return LookupAndCallBinaryNodeGen.create(name, null, null, false, false);
    }

    public static LookupAndCallBinaryNode createReversible(String name, String reverseName, Supplier<NotImplementedHandler> handlerFactory) {
        assert name.startsWith("__") && reverseName.startsWith("__r");
        return LookupAndCallBinaryNodeGen.create(name, reverseName, handlerFactory, false, false);
    }

    public static LookupAndCallBinaryNode create(String name, String rname) {
        return LookupAndCallBinaryNodeGen.create(name, rname, null, false, false);
    }

    public static LookupAndCallBinaryNode create(String name, String rname, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        return LookupAndCallBinaryNodeGen.create(name, rname, null, alwaysCheckReverse, ignoreDescriptorException);
    }

    public static LookupAndCallBinaryNode create(String name, String rname, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallBinaryNodeGen.create(name, rname, handlerFactory, false, false);
    }

    protected Object getMethod(Object receiver, String methodName) {
        return LookupSpecialMethodNode.Dynamic.getUncached().execute(GetClassNode.getUncached().execute(receiver), methodName, receiver, ignoreDescriptorException);
    }

    protected boolean isReversible() {
        return rname != null;
    }

    private CallBinaryMethodNode ensureDispatch() {
        // this also serves as a branch profile
        if (dispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchNode = insert(CallBinaryMethodNode.create());
        }
        return dispatchNode;
    }

    private PRaiseNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    private GetNameNode ensureGetNameNode() {
        if (getNameNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getNameNode = insert(GetNameNode.create());
        }
        return getNameNode;
    }

    private CallBinaryMethodNode ensureReverseDispatch() {
        // this also serves as a branch profile
        if (reverseDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reverseDispatchNode = insert(CallBinaryMethodNode.create());
        }
        return reverseDispatchNode;
    }

    private UnexpectedResultException handleLeftURE(VirtualFrame frame, Object left, Object right, UnexpectedResultException e) throws UnexpectedResultException {
        if (isReversible() && e.getResult() == PNotImplemented.NOT_IMPLEMENTED) {
            throw new UnexpectedResultException(ensureReverseDispatch().executeObject(frame, getMethod(right, rname), right, left));
        } else {
            throw e;
        }
    }

    protected PythonBinaryBuiltinNode getBuiltin(Object receiver) {
        assert receiver instanceof Boolean || receiver instanceof Integer || receiver instanceof Long || receiver instanceof Double || receiver instanceof String;
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(GetClassNode.getUncached().execute(receiver), name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonBinaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonBinaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
            }
        }
        return null;
    }

    // bool, bool

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, boolean left, boolean right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, boolean left, boolean right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callInt(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // int, int

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, int left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callInt(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, int left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    long callLong(VirtualFrame frame, int left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callLong(frame, left, right); // implicit conversion to long
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // long, long

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    long callLong(VirtualFrame frame, long left, long right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callLong(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, long left, long right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // int, double

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static boolean callBoolean(VirtualFrame frame, int left, double right,
                    @Cached("getBuiltin(right)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callBool(frame, left, right);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static boolean callBoolean(VirtualFrame frame, double left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callBool(frame, left, right);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static double callDouble(VirtualFrame frame, int left, double right,
                    @Cached("getBuiltin(right)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callDouble(frame, left, right);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static double callDouble(VirtualFrame frame, double left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callDouble(frame, left, right);
    }

    // long, double

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static boolean callBoolean(VirtualFrame frame, long left, double right,
                    @Cached("getBuiltin(right)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callBool(frame, left, right);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static boolean callBoolean(VirtualFrame frame, double left, long right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callBool(frame, left, right);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static double callDouble(VirtualFrame frame, long left, double right,
                    @Cached("getBuiltin(right)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callDouble(frame, left, right);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    static double callDouble(VirtualFrame frame, double left, long right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        return function.callDouble(frame, left, right);
    }

    // double, double

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    double callDouble(VirtualFrame frame, double left, double right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callDouble(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, double left, double right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.callBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }
/**
    @Specialization(guards = {"lib.isBfNode(left) || lib.isBfNode(right)"})
    Object doInteropt(
            Object left,
            Object right,
            @CachedLibrary(limit = "30") InteropLibrary lib) {
        return lib.executeBinaryOperation(left, right, getName());
    }
*/
    // Object, Object

    @Specialization(guards = {"!isReversible()"}, limit = "5")
    Object callObject(VirtualFrame frame, Object left, Object right,
                    @CachedLibrary("left") PythonObjectLibrary libLeft,
                    @Cached("create(name, ignoreDescriptorException)") LookupSpecialMethodNode getattr) {
        Object leftClass = libLeft.getLazyPythonClass(left);
        Object leftValue = libLeft.getDelegatedValue(left);
        Object leftCallable = getattr.execute(frame, leftClass, leftValue);
        if (leftCallable == PNone.NO_VALUE) {
            if (handlerFactory != null) {
                return runErrorHandler(frame, leftValue, right);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
        return ensureDispatch().executeObject(frame, leftCallable, leftValue, right);
    }

    @Specialization(guards = {"!isReversible()"}, replaces = "callObject")
    Object callObjectUncached(VirtualFrame frame, Object left, Object right,
                    @CachedLibrary(limit = "1") PythonObjectLibrary libLeft,
                    @Cached("create(name, ignoreDescriptorException)") LookupSpecialMethodNode getattr) {
        return callObject(frame, left, right, libLeft, getattr);
    }

    @Specialization(guards = {"isReversible()"}, limit = "5")
    Object callObjectR(VirtualFrame frame, Object left, Object right,
                    @Cached("create(name, ignoreDescriptorException)") LookupSpecialMethodNode getattr,
                    @Cached("create(rname, ignoreDescriptorException)") LookupSpecialMethodNode getattrR,
                    @CachedLibrary("left") PythonObjectLibrary libLeft,
                    @CachedLibrary("right") PythonObjectLibrary libRight,
                    @Cached("create()") TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached("create()") IsSubtypeNode isSubtype,
                    @Cached("createBinaryProfile()") ConditionProfile hasLeftCallable,
                    @Cached("createBinaryProfile()") ConditionProfile hasRightCallable,
                    @Cached("createBinaryProfile()") ConditionProfile notImplementedProfile,
                    @Cached("createBinaryProfile()") ConditionProfile hasEnclosingBuiltin,
                    @Cached BranchProfile noLeftBuiltinClassType,
                    @Cached BranchProfile noRightBuiltinClassType,
                    @Cached BranchProfile gotResultBranch) {
        // This specialization implements the logic from cpython://Objects/abstract.c#binary_op1
        // (the structure is modelled closely on it), as well as the additional logic in
        // cpython://Objects/typeobject.c#SLOT1BINFULL. The latter has the addition that it swaps
        // the arguments around. The swapping of arguments is undone when the call ends up in a
        // builtin function using a wrapper in CPython. We implement this reversal in our
        // BuiltinFunctionRootNode. This is opposite to what CPython does (and more in line with
        // what PyPy does), in that CPython always dispatches with the same argument order and has
        // slot wrappers for heap types __r*__ methods to swap the arguments, but we don't wrap heap
        // types' methods and instead have our swapping for the builtin types.

        Object result = PNotImplemented.NOT_IMPLEMENTED;
        Object leftClass = libLeft.getLazyPythonClass(left);
        Object leftValue = libLeft.getDelegatedValue(left);
        Object leftCallable = getattr.execute(frame, leftClass, leftValue);
        Object rightClass = libRight.getLazyPythonClass(right);
        Object rightValue = libRight.getDelegatedValue(right);
        Object rightCallable = getattrR.execute(frame, rightClass, rightValue);

        if (!alwaysCheckReverse && leftCallable == rightCallable) {
            rightCallable = PNone.NO_VALUE;
        }

        if (hasLeftCallable.profile(leftCallable != PNone.NO_VALUE)) {
            if (hasRightCallable.profile(rightCallable != PNone.NO_VALUE) &&
                            (!isSameTypeNode.execute(leftClass, rightClass) && isSubtype.execute(frame, rightClass, leftClass) ||
                                            isFlagSequenceCompat(leftClass, rightClass, name, noLeftBuiltinClassType, noRightBuiltinClassType))) {
                result = dispatch(frame, ensureReverseDispatch(), rightCallable, rightValue, leftValue, rightClass, rname, isSubtype, hasEnclosingBuiltin);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                gotResultBranch.enter();
                rightCallable = PNone.NO_VALUE;
            }
            result = dispatch(frame, ensureDispatch(), leftCallable, leftValue, rightValue, leftClass, name, isSubtype, hasEnclosingBuiltin);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            gotResultBranch.enter();
        }
        if (notImplementedProfile.profile(rightCallable != PNone.NO_VALUE)) {
            result = dispatch(frame, ensureReverseDispatch(), rightCallable, rightValue, leftValue, rightClass, rname, isSubtype, hasEnclosingBuiltin);
        }
        if (handlerFactory != null && result == PNotImplemented.NOT_IMPLEMENTED) {
            return runErrorHandler(frame, leftValue, rightValue);
        }
        return result;
    }

    private Object dispatch(VirtualFrame frame, CallBinaryMethodNode dispatch, Object callable, Object leftValue, Object rightValue, Object leftClass, String op, IsSubtypeNode isSubtype,
                    ConditionProfile hasEnclosingBuiltin) {
        // see descrobject.c/wrapperdescr_call()
        Object enclosing = null;
        if (callable instanceof PBuiltinFunction) {
            enclosing = ((PBuiltinFunction) callable).getEnclosingType();
        } else if (callable instanceof PBuiltinMethod) {
            enclosing = ((PBuiltinMethod) callable).getFunction().getEnclosingType();
        }
        if (hasEnclosingBuiltin.profile(enclosing != null) && !isSubtype.execute(leftClass, enclosing)) {
            throw ensureRaiseNode().raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, op, ensureGetNameNode().execute(leftClass), leftValue);
        }
        return dispatch.executeObject(frame, callable, leftValue, rightValue);
    }

    @Specialization(guards = {"isReversible()"}, replaces = "callObjectR")
    Object callObjectRUncached(VirtualFrame frame, Object left, Object right,
                    @Cached("create(name, ignoreDescriptorException)") LookupSpecialMethodNode getattr,
                    @Cached("create(rname, ignoreDescriptorException)") LookupSpecialMethodNode getattrR,
                    @CachedLibrary(limit = "1") PythonObjectLibrary libLeft,
                    @CachedLibrary(limit = "1") PythonObjectLibrary libRight,
                    @Cached("create()") TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached("create()") IsSubtypeNode isSubtype,
                    @Cached("createBinaryProfile()") ConditionProfile hasLeftCallable,
                    @Cached("createBinaryProfile()") ConditionProfile hasRightCallable,
                    @Cached("createBinaryProfile()") ConditionProfile notImplementedProfile,
                    @Cached("createBinaryProfile()") ConditionProfile hasEnclosingBuiltin,
                    @Cached BranchProfile noLeftBuiltinClassType,
                    @Cached BranchProfile noRightBuiltinClassType,
                    @Cached BranchProfile gotResultBranch) {
        return callObjectR(frame, left, right, getattr, getattrR, libLeft, libRight, isSameTypeNode, isSubtype,
                        hasLeftCallable, hasRightCallable, notImplementedProfile, hasEnclosingBuiltin, noLeftBuiltinClassType, noRightBuiltinClassType, gotResultBranch);
    }

    private static boolean isFlagSequenceCompat(Object leftClass, Object rightClass, String name, BranchProfile gotLeftBuiltinClassType, BranchProfile gotRightBuiltinClassType) {
        if (PGuards.isNativeClass(leftClass) || PGuards.isNativeClass(rightClass)) {
            return false;
        }
        // see pypy descroperation.py#_make_binop_impl()
        boolean isSeqBugCompatOperation = (name.equals(__ADD__) || name.equals(__MUL__));
        return isSeqBugCompatOperation && isFlagSequenceBugCompat(leftClass, gotLeftBuiltinClassType) && !isFlagSequenceBugCompat(rightClass, gotRightBuiltinClassType);
    }

    private static boolean isFlagSequenceBugCompat(Object clazz, BranchProfile gotBuiltinClassType) {
        PythonBuiltinClassType type = null;
        if (clazz instanceof PythonBuiltinClassType) {
            type = (PythonBuiltinClassType) clazz;
        } else if (clazz instanceof PythonBuiltinClass) {
            type = ((PythonBuiltinClass) clazz).getType();
        } else {
            return false;
        }
        gotBuiltinClassType.enter();
        return type == PythonBuiltinClassType.PString ||
                        type == PythonBuiltinClassType.PByteArray ||
                        type == PythonBuiltinClassType.PBytes ||
                        type == PythonBuiltinClassType.PList ||
                        type == PythonBuiltinClassType.PTuple;
    }

    private Object runErrorHandler(VirtualFrame frame, Object left, Object right) {
        if (handler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handler = insert(handlerFactory.get());
        }
        return handler.execute(frame, left, right);
    }

    public String getName() {
        return name;
    }

    public String getRname() {
        return rname;
    }

}

/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignObject)
public class ForeignObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean bool(Object receiver,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.isBoolean(receiver)) {
                    return lib.asBoolean(receiver);
                }
                if (lib.fitsInLong(receiver)) {
                    return lib.asLong(receiver) != 0;
                }
                if (lib.fitsInDouble(receiver)) {
                    return lib.asDouble(receiver) != 0.0;
                }
                if (lib.hasArrayElements(receiver)) {
                    return lib.getArraySize(receiver) != 0;
                }
                if (lib.hasHashEntries(receiver)) {
                    return lib.getHashSize(receiver) != 0;
                }
                if (lib.isString(receiver)) {
                    return !lib.asString(receiver).isEmpty();
                }
                return !lib.isNull(receiver);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }
    }

    static Object[] unpackForeignArray(Object left, InteropLibrary lib, PForeignToPTypeNode convert) {
        try {
            long sizeObj = lib.getArraySize(left);
            if (sizeObj < Integer.MAX_VALUE) {
                int size = (int) sizeObj;
                Object[] data = new Object[size];

                // read data
                for (int i = 0; i < size; i++) {
                    data[i] = convert.executeConvert(lib.readArrayElement(left, i));
                }

                return data;
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("object does not unpack to array as it claims to");
        }
        return null;
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public long len(Object self,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.hasArrayElements(self)) {
                    return lib.getArraySize(self);
                } else if (lib.isIterator(self) || lib.hasIterator(self)) {
                    return 0; // a value signifying it has a length, but it's unknown
                } else if (lib.hasHashEntries(self)) {
                    return lib.getHashSize(self);
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            } finally {
                gil.acquire();
            }
            throw raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, __LEN__);
        }
    }

    abstract static class ForeignBinaryNode extends PythonBinaryBuiltinNode {
        @Child private BinaryOpNode op;
        protected final boolean reverse;

        protected ForeignBinaryNode(BinaryOpNode op, boolean reverse) {
            this.op = op;
            this.reverse = reverse;
        }

        @Specialization(guards = {"lib.isBoolean(left)"})
        Object doComparisonBool(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            boolean leftBoolean;
            gil.release(true);
            try {
                leftBoolean = lib.asBoolean(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to boolean as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftBoolean, right);
            } else {
                return op.executeObject(frame, right, leftBoolean);
            }

        }

        @Specialization(guards = {"!lib.isBoolean(left)", "lib.fitsInLong(left)"})
        Object doComparisonLong(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            long leftLong;
            gil.release(true);
            try {
                leftLong = lib.asLong(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to long as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftLong, right);
            } else {
                return op.executeObject(frame, right, leftLong);
            }
        }

        @Specialization(guards = {"!lib.isBoolean(left)", "!lib.fitsInLong(left)", "lib.fitsInDouble(left)"})
        Object doComparisonDouble(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            double leftDouble;
            gil.release(true);
            try {
                leftDouble = lib.asDouble(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to double as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftDouble, right);
            } else {
                return op.executeObject(frame, right, leftDouble);
            }
        }

        @Specialization(guards = {"!lib.isBoolean(left)", "!lib.fitsInLong(left)", "!lib.fitsInDouble(left)", "lib.isString(left)"})
        Object doComparisonString(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            String leftString;
            gil.release(true);
            try {
                leftString = lib.asString(left);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftString, right);
            } else {
                return op.executeObject(frame, right, leftString);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        public static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends ForeignBinaryNode {
        AddNode() {
            super(BinaryArithmetic.Add.create(), false);
        }

        AddNode(boolean reverse) {
            super(BinaryArithmetic.Add.create(), reverse);
        }

        @Specialization(insertBefore = "doGeneric", guards = {"lib.hasArrayElements(left)", "lib.hasArrayElements(right)"})
        static Object doForeignArray(Object left, Object right,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PForeignToPTypeNode convert,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                Object[] unpackedLeft = unpackForeignArray(left, lib, convert);
                Object[] unpackedRight = unpackForeignArray(right, lib, convert);
                if (unpackedLeft != null && unpackedRight != null) {
                    Object[] result = Arrays.copyOf(unpackedLeft, unpackedLeft.length + unpackedRight.length);
                    for (int i = 0, j = unpackedLeft.length; i < unpackedRight.length && j < result.length; i++, j++) {
                        assert j < result.length;
                        result[j] = unpackedRight[i];
                    }
                    return factory.createList(result);
                }
            } finally {
                gil.acquire();
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
        RAddNode() {
            super(true);
        }
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends ForeignBinaryNode {
        MulNode() {
            super(BinaryArithmetic.Mul.create(), false);
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"!lib.isBoolean(left)", "!lib.isNumber(left)", "lib.hasArrayElements(left)", "lib.fitsInLong(right)"})
        static Object doForeignArray(Object left, Object right,
                        @Cached PRaiseNode raise,
                        @Cached PythonObjectFactory factory,
                        @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode cast,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                long rightLong;
                try {
                    rightLong = lib.asLong(right);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("object does not unpack to index-sized int as it claims to");
                }
                return doMulArray(left, cast.execute(rightLong), raise, factory, convert, lib);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"!lib.isBoolean(left)", "!lib.isNumber(left)", "lib.hasArrayElements(left)", "lib.isBoolean(right)"})
        static Object doForeignArrayForeignBoolean(Object left, Object right,
                        @Cached PRaiseNode raise,
                        @Cached PythonObjectFactory factory,
                        @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                boolean rightBoolean;
                try {
                    rightBoolean = lib.asBoolean(right);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("object does not unpack to boolean (to be used as index) as it claims to");
                }
                return doMulArray(left, rightBoolean ? 1 : 0, raise, factory, convert, lib);
            } finally {
                gil.acquire();
            }
        }

        private static PythonAbstractObject doMulArray(Object left, int rightInt, PRaiseNode raise, PythonObjectFactory factory, PForeignToPTypeNode convert, InteropLibrary lib) {
            try {
                Object[] unpackForeignArray = unpackForeignArray(left, lib, convert);
                if (unpackForeignArray != null) {
                    if (rightInt < 0) {
                        return factory.createList();
                    }
                    Object[] repeatedData = new Object[Math.max(0, Math.multiplyExact(unpackForeignArray.length, rightInt > 0 ? rightInt : 0))];

                    // repeat data
                    for (int i = 0; i < repeatedData.length; i++) {
                        repeatedData[i] = unpackForeignArray[i % unpackForeignArray.length];
                    }

                    return factory.createList(repeatedData);
                }
                return PNotImplemented.NOT_IMPLEMENTED;
            } catch (ArithmeticException e) {
                throw raise.raise(MemoryError);
            }
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends ForeignBinaryNode {
        SubNode() {
            super(BinaryArithmetic.Sub.create(), false);
        }
    }

    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RSubNode extends ForeignBinaryNode {
        RSubNode() {
            super(BinaryArithmetic.Sub.create(), true);
        }
    }

    @Builtin(name = __TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class TrueDivNode extends ForeignBinaryNode {
        TrueDivNode() {
            super(BinaryArithmetic.TrueDiv.create(), false);
        }
    }

    @Builtin(name = __RTRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RTrueDivNode extends ForeignBinaryNode {
        RTrueDivNode() {
            super(BinaryArithmetic.TrueDiv.create(), true);
        }
    }

    @Builtin(name = __FLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends ForeignBinaryNode {
        FloorDivNode() {
            super(BinaryArithmetic.FloorDiv.create(), false);
        }
    }

    @Builtin(name = __RFLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends ForeignBinaryNode {
        RFloorDivNode() {
            super(BinaryArithmetic.FloorDiv.create(), true);
        }
    }

    @Builtin(name = __DIVMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DivModNode extends ForeignBinaryNode {
        DivModNode() {
            super(BinaryArithmetic.DivMod.create(), false);
        }
    }

    @Builtin(name = __RDIVMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RDivModNode extends ForeignBinaryNode {
        RDivModNode() {
            super(BinaryArithmetic.DivMod.create(), true);
        }
    }

    public abstract static class ForeignBinaryComparisonNode extends PythonBinaryBuiltinNode {
        @Child private BinaryComparisonNode comparisonNode;

        protected ForeignBinaryComparisonNode(BinaryComparisonNode op) {
            this.comparisonNode = op;
        }

        @Specialization(guards = {"lib.isBoolean(left)"})
        Object doComparisonBool(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            boolean leftBoolean;
            gil.release(true);
            try {
                leftBoolean = lib.asBoolean(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to boolean for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftBoolean, right);
        }

        @Specialization(guards = {"lib.fitsInLong(left)"})
        Object doComparisonLong(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            long leftLong;
            gil.release(true);
            try {
                leftLong = lib.asLong(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to long for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftLong, right);
        }

        @Specialization(guards = {"lib.fitsInDouble(left)"})
        Object doComparisonDouble(VirtualFrame frame, Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            double leftDouble;
            gil.release(true);
            try {
                leftDouble = lib.asDouble(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to double for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftDouble, right);
        }

        @Specialization(guards = "lib.isNull(left)")
        Object doComparison(VirtualFrame frame, @SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return comparisonNode.executeObject(frame, PNone.NONE, right);
        }

        @SuppressWarnings("unused")
        @Fallback
        public static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends ForeignBinaryComparisonNode {
        protected LtNode() {
            super(BinaryComparisonNode.LtNode.create());
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends ForeignBinaryComparisonNode {
        protected LeNode() {
            super(BinaryComparisonNode.LeNode.create());
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends ForeignBinaryComparisonNode {
        protected GtNode() {
            super(BinaryComparisonNode.GtNode.create());
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends ForeignBinaryComparisonNode {
        protected GeNode() {
            super(BinaryComparisonNode.GeNode.create());
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends ForeignBinaryComparisonNode {
        protected EqNode() {
            super(BinaryComparisonNode.EqNode.create());
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static int hash(Object self,
                        @CachedLibrary("self") InteropLibrary library) {
            if (library.hasIdentity(self)) {
                try {
                    return library.identityHashCode(self);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return hashCodeBoundary(self);
        }

        @TruffleBoundary
        private static int hashCodeBoundary(Object self) {
            return self.hashCode();
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object contains(VirtualFrame frame, Object self, Object arg,
                        // accesses both self and iterator
                        @CachedLibrary(limit = "3") InteropLibrary library,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached CastToJavaStringNode cast) {
            try {
                if (library.isString(self)) {
                    String selfStr = library.asString(self);
                    try {
                        String argStr = cast.execute(arg);
                        return containsBoundary(selfStr, argStr);
                    } catch (CannotCastException e) {
                        throw raise(TypeError, ErrorMessages.REQUIRES_STRING_AS_LEFT_OPERAND, arg);
                    }
                }
                if (library.hasArrayElements(self)) {
                    for (int i = 0; i < library.getArraySize(self); i++) {
                        if (library.isArrayElementReadable(self, i)) {
                            Object element = library.readArrayElement(self, i);
                            if (eqNode.execute(frame, arg, element)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                Object iterator = null;
                if (library.isIterator(self)) {
                    iterator = self;
                } else if (library.hasHashEntries(self)) {
                    iterator = library.getHashKeysIterator(self);
                } else if (library.hasIterator(self)) {
                    iterator = library.getIterator(self);
                }
                if (iterator != null) {
                    try {
                        while (library.hasIteratorNextElement(iterator)) {
                            Object next = library.getIteratorNextElement(iterator);
                            if (eqNode.execute(frame, arg, next)) {
                                return true;
                            }
                        }
                    } catch (StopIterationException e) {
                        // fallthrough
                    }
                    return false;
                }
                throw raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        private static boolean containsBoundary(String selfStr, String argStr) {
            return selfStr.contains(argStr);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        static Object doGeneric(Object object,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.isIterator(object)) {
                    return object;
                } else if (lib.hasIterator(object)) {
                    return lib.getIterator(object);
                } else if (lib.hasArrayElements(object)) {
                    long size = lib.getArraySize(object);
                    if (size < Integer.MAX_VALUE) {
                        return factory.createForeignArrayIterator(object);
                    }
                    throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
                } else if (lib.isString(object)) {
                    return factory.createStringIterator(lib.asString(object));
                } else if (lib.hasHashEntries(object)) {
                    // just like dict.__iter__, we take the keys by default
                    return lib.getHashKeysIterator(object);
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
            throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object doForeignArray(Object iterator,
                        @Cached ConditionProfile notIterator,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("iterator") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (notIterator.profile(lib.isIterator(iterator))) {
                gil.release(true);
                try {
                    return lib.getIteratorNextElement(iterator);
                } catch (StopIterationException e) {
                    throw raiseNode.raise(StopIteration);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("iterator claimed to be iterator but wasn't");
                } finally {
                    gil.acquire();
                }
            } else {
                throw raiseNode.raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, __NEXT__);
            }
        }
    }

    @Builtin(name = __NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonBuiltinNode {
        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.a
         */
        @Specialization(guards = {"isForeignObjectNode.execute(callee)", "!isNoValue(callee)", "keywords.length == 0"}, limit = "1")
        protected Object doInteropCall(Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                Object res = lib.instantiate(callee, arguments);
                return toPTypeNode.executeConvert(res);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            } finally {
                gil.acquire();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object doGeneric(Object callee, Object arguments, Object keywords) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        public final Object executeWithArgs(VirtualFrame frame, Object callee, Object[] arguments) {
            return execute(frame, callee, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public abstract Object execute(VirtualFrame frame, Object callee, Object[] arguments, PKeyword[] keywords);

        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.
         */
        @Specialization(guards = {"isForeignObjectNode.execute(callee)", "!isNoValue(callee)", "keywords.length == 0"}, limit = "1")
        protected Object doInteropCall(VirtualFrame frame, Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "4") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode gil) {
            PythonLanguage language = getLanguage();
            try {
                Object state = IndirectCallContext.enter(frame, language, getContext(), this);
                gil.release(true);
                try {
                    if (lib.isExecutable(callee)) {
                        return toPTypeNode.executeConvert(lib.execute(callee, arguments));
                    } else {
                        return toPTypeNode.executeConvert(lib.instantiate(callee, arguments));
                    }
                } finally {
                    gil.acquire();
                    IndirectCallContext.exit(frame, language, getContext(), state);
                }
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object doGeneric(Object callee, Object arguments, Object keywords) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }

        public static CallNode create() {
            return ForeignObjectBuiltinsFactory.CallNodeFactory.create(null);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Child private AccessForeignItemNodes.GetForeignItemNode getForeignItemNode = AccessForeignItemNodes.GetForeignItemNode.create();

        @Specialization
        Object doit(VirtualFrame frame, Object object, Object key) {
            return getForeignItemNode.execute(frame, object, key);
        }
    }

    @Builtin(name = __GETATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetattrNode extends PythonBinaryBuiltinNode {
        @Child private PForeignToPTypeNode toPythonNode = PForeignToPTypeNode.create();

        @Specialization
        protected Object doIt(Object object, Object memberObj,
                        @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") InteropLibrary read,
                        @Cached CastToJavaStringNode castToString,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                String member = castToString.execute(memberObj);
                if (read.isMemberReadable(object, member)) {
                    return toPythonNode.executeConvert(read.readMember(object, member));
                }
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, memberObj);
            } catch (UnknownIdentifierException | UnsupportedMessageException ignore) {
            } finally {
                gil.acquire();
            }
            throw raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, memberObj);
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = __SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization
        protected PNone doIt(Object object, Object key, Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToJavaStringNode castToString,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                lib.writeMember(object, castToString.execute(key), value);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
            } finally {
                gil.acquire();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetitemNode extends PythonTernaryBuiltinNode {
        @Child private AccessForeignItemNodes.SetForeignItemNode setForeignItemNode = AccessForeignItemNodes.SetForeignItemNode.create();

        @Specialization
        Object doit(VirtualFrame frame, Object object, Object key, Object value) {
            setForeignItemNode.execute(frame, object, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone doIt(Object object, String key,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToJavaStringNode castToString,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                lib.removeMember(object, castToString.execute(key));
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
            } finally {
                gil.acquire();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelitemNode extends PythonBinaryBuiltinNode {
        @Child private AccessForeignItemNodes.RemoveForeignItemNode delForeignItemNode = AccessForeignItemNodes.RemoveForeignItemNode.create();

        @Specialization
        PNone doit(VirtualFrame frame, Object object, Object key) {
            delForeignItemNode.execute(frame, object, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.hasMembers(object)) {
                gil.release(true);
                try {
                    return lib.getMembers(object);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("foreign object claims to have members, but does not return them");
                } finally {
                    gil.acquire();
                }
            } else {
                return factory().createList();
            }
        }
    }

    @Builtin(name = __INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        protected static long doIt(Object object,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.isBoolean(object)) {
                    try {
                        return PInt.intValue(lib.asBoolean(object));
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims to be a boolean but isn't");
                    }
                }
                if (lib.fitsInInt(object)) {
                    try {
                        return lib.asInt(object);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims it fits into index-sized int, but doesn't");
                    }
                }
                if (lib.fitsInLong(object)) {
                    try {
                        return lib.asLong(object);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims it fits into index-sized long, but doesn't");
                    }
                }
                throw raiseNode.raiseIntegerInterpretationError(object);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        protected final String method = __STR__;
        @Child private LookupAndCallUnaryNode callStrNode;
        @Child private CastToListNode castToListNode;
        @Child private ObjectNodes.DefaultObjectReprNode defaultReprNode;

        @Specialization
        Object str(VirtualFrame frame, Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached BranchProfile isNull,
                        @Cached BranchProfile isBoolean,
                        @Cached BranchProfile isString,
                        @Cached BranchProfile isLong,
                        @Cached BranchProfile isDouble,
                        @Cached BranchProfile isArray,
                        @Cached BranchProfile isHostObject) {
            try {
                if (lib.isNull(object)) {
                    isNull.enter();
                    return getCallStrNode().executeObject(frame, PNone.NONE);
                } else if (lib.isBoolean(object)) {
                    isBoolean.enter();
                    boolean value;
                    gil.release(true);
                    try {
                        value = lib.asBoolean(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.isString(object)) {
                    isString.enter();
                    String value;
                    gil.release(true);
                    try {
                        value = lib.asString(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.fitsInLong(object)) {
                    isLong.enter();
                    long value;
                    gil.release(true);
                    try {
                        value = lib.asLong(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.fitsInDouble(object)) {
                    isDouble.enter();
                    double value;
                    gil.release(true);
                    try {
                        value = lib.asDouble(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.hasArrayElements(object)) {
                    isArray.enter();
                    long size;
                    gil.release(true);
                    try {
                        size = lib.getArraySize(object);
                    } finally {
                        gil.acquire();
                    }
                    if (size <= Integer.MAX_VALUE && size >= 0) {
                        PForeignArrayIterator iterable = factory().createForeignArrayIterator(object);
                        return getCallStrNode().executeObject(frame, getCastToListNode().execute(frame, iterable));
                    }
                } else if (getContext().getEnv().isHostObject(object)) {
                    isHostObject.enter();
                    boolean isMetaObject = lib.isMetaObject(object);
                    Object metaObject = isMetaObject
                                    ? object
                                    : lib.hasMetaObject(object) ? lib.getMetaObject(object) : null;
                    if (metaObject != null) {
                        Object displayName = lib.toDisplayString(metaObject);
                        String text = createDisplayName(isMetaObject, displayName);
                        return PythonUtils.format("<%s at 0x%x>", text, PythonAbstractObject.systemHashCode(object));
                    }
                }
            } catch (UnsupportedMessageException e) {
                // Fall back to the generic impl
            }
            return defaultRepr(frame, object);
        }

        @TruffleBoundary
        private static String createDisplayName(boolean isMetaObject, Object object) {
            StringBuilder sb = new StringBuilder();
            sb.append(isMetaObject ? "JavaClass[" : "JavaObject[");
            sb.append(object.toString());
            sb.append("]");
            return sb.toString();
        }

        private LookupAndCallUnaryNode getCallStrNode() {
            if (callStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callStrNode = insert(LookupAndCallUnaryNode.create(method));
            }
            return callStrNode;
        }

        private CastToListNode getCastToListNode() {
            if (castToListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToListNode = insert(CastToListNode.create());
            }
            return castToListNode;
        }

        protected String defaultRepr(VirtualFrame frame, Object object) {
            if (defaultReprNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultReprNode = insert(ObjectNodes.DefaultObjectReprNode.create());
            }
            return defaultReprNode.execute(frame, object);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
        protected final String method = __REPR__;
    }

    @Builtin(name = __BASES__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        Object getBases(Object self,
                        @CachedLibrary("self") InteropLibrary lib) {
            if (lib.isMetaObject(self)) {
                return factory().createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            } else {
                throw raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, __BASES__);
            }
        }
    }

    @Builtin(name = __INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class InstancecheckNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        Object check(Object self, Object instance,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isMetaObject(self)) {
                gil.release(true);
                try {
                    return lib.isMetaInstance(self, instance);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                } finally {
                    gil.acquire();
                }
            } else {
                throw raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, __INSTANCECHECK__);
            }
        }
    }

    @Builtin(name = __RAND__, minNumOfPositionalArgs = 2)
    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitAndNode andNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isNumber(left) && lib.fitsInLong(left)) {
                try {
                    long leftLong;
                    gil.release(true);
                    try {
                        leftLong = lib.asLong(left);
                    } finally {
                        gil.acquire();
                    }
                    return andNode.executeObject(frame, leftLong, right);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = __ROR__, minNumOfPositionalArgs = 2)
    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitOrNode orNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isNumber(left) && lib.fitsInLong(left)) {
                try {
                    long leftLong;
                    gil.release(true);
                    try {
                        leftLong = lib.asLong(left);
                    } finally {
                        gil.acquire();
                    }
                    return orNode.executeObject(frame, leftLong, right);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = __RXOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class XorNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitXorNode xorNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isNumber(left) && lib.fitsInLong(left)) {
                try {
                    long leftLong;
                    gil.release(true);
                    try {
                        leftLong = lib.asLong(left);
                    } finally {
                        gil.acquire();
                    }
                    return xorNode.executeObject(frame, leftLong, right);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }
}

/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createASCIIString;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createUTF8String;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.utf8StringToBytes;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_BYTESLIKE_GOT_P;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.ArgumentIndex;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.BytesJoinNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.FindNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.ToBytesNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class BytesNodes {

    @GenerateUncached
    public abstract static class CreateBytesNode extends Node {

        public abstract PBytesLike execute(PythonObjectFactory factory, PBytesLike basedOn, Object bytes);

        @Specialization
        static PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, byte[] bytes) {
            return factory.createBytes(bytes);
        }

        @Specialization
        static PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, byte[] bytes) {
            return factory.createByteArray(bytes);
        }

        @Specialization
        static PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, SequenceStorage bytes) {
            return factory.createBytes(bytes);
        }

        @Specialization
        static PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, SequenceStorage bytes) {
            return factory.createByteArray(bytes);
        }

        @Specialization
        static PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, PBytesLike bytes) {
            return factory.createBytes(bytes.getSequenceStorage());
        }

        @Specialization
        static PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, PBytesLike bytes) {
            return factory.createByteArray(bytes.getSequenceStorage());
        }
    }

    @ImportStatic(PythonOptions.class)
    public abstract static class BytesJoinNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, byte[] sep, Object iterable);

        @Specialization
        static byte[] join(VirtualFrame frame, byte[] sep, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached ToBytesNode toBytesNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            ArrayList<byte[]> parts = new ArrayList<>();
            int partsTotalSize = 0;
            Object iterator = getIter.execute(frame, iterable);
            while (true) {
                try {
                    partsTotalSize += append(parts, toBytesNode.execute(getNextNode.execute(frame, iterator)));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return joinArrays(sep, parts, partsTotalSize);
                }
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static int append(ArrayList<byte[]> parts, byte[] barr) {
            parts.add(barr);
            return barr.length;
        }

        @TruffleBoundary(allowInlining = true)
        private static byte[] joinArrays(byte[] sep, ArrayList<byte[]> parts, int partsTotalSize) {
            byte[] joinedBytes = new byte[Math.max(0, partsTotalSize + (parts.size() - 1) * sep.length)];
            if (parts.size() > 0) {
                int offset = 0;
                byte[] array = parts.get(0);
                PythonUtils.arraycopy(array, 0, joinedBytes, offset, array.length);
                offset += array.length;
                for (int i = 1; i < parts.size(); i++) {
                    array = parts.get(i);
                    PythonUtils.arraycopy(sep, 0, joinedBytes, offset, sep.length);
                    offset += sep.length;
                    PythonUtils.arraycopy(array, 0, joinedBytes, offset, array.length);
                    offset += array.length;
                }
            }
            return joinedBytes;
        }

        public static BytesJoinNode create() {
            return BytesJoinNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ToBytesNode extends PNodeWithRaise {

        private final PythonBuiltinClassType errorType;
        private final String errorMessageFormat;

        ToBytesNode(PythonBuiltinClassType errorType, String errorMessageFormat) {
            this.errorType = errorType;
            this.errorMessageFormat = errorMessageFormat;
        }

        public abstract byte[] execute(Object obj);

        @Specialization(limit = "3")
        byte[] doBuffer(Object object,
                        @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            Object buffer;
            try {
                buffer = bufferAcquireLib.acquireReadonly(object);
            } catch (PException e) {
                throw raise(errorType, errorMessageFormat, object);
            }
            try {
                return bufferLib.getCopiedByteArray(buffer);
            } finally {
                bufferLib.release(buffer);
            }
        }

        public static ToBytesNode create() {
            return ToBytesNodeGen.create(TypeError, EXPECTED_BYTESLIKE_GOT_P);
        }

        public static ToBytesNode create(PythonBuiltinClassType errorType, String errorMessageFormat) {
            return ToBytesNodeGen.create(errorType, errorMessageFormat);
        }
    }

    public abstract static class FindNode extends PNodeWithRaise {

        public abstract int execute(byte[] self, int len1, byte[] sub, int start, int end);

        public abstract int execute(byte[] self, int len1, byte sub, int start, int end);

        public abstract int execute(SequenceStorage self, int len1, Object sub, int start, int end);

        @Specialization
        int find(byte[] haystack, int len1, byte needle, int start, int end,
                        @Cached ConditionProfile earlyExit) {
            if (earlyExit.profile(start >= len1)) {
                return -1;
            }
            return findElement(haystack, needle, start, end > len1 ? len1 : end);
        }

        @Specialization
        int find(byte[] haystack, int len1, byte[] needle, int start, int end,
                        @Cached ConditionProfile earlyExit1,
                        @Cached ConditionProfile earlyExit2,
                        @Cached ConditionProfile lenIsOne) {
            int len2 = needle.length;

            if (earlyExit1.profile(len2 == 0 && start <= len1)) {
                return emptySubIndex(start, end);
            }
            if (earlyExit2.profile(start >= len1 || len1 < len2)) {
                return -1;
            }
            if (lenIsOne.profile(len2 == 1)) {
                return findElement(haystack, needle[0], start, end);
            }

            return findSubSequence(haystack, needle, len2, start, end > len1 ? len1 : end);
        }

        @Specialization
        int find(SequenceStorage self, int len1, PBytesLike sub, int start, int end,
                        @Cached ConditionProfile earlyExit1,
                        @Cached ConditionProfile earlyExit2,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            byte[] haystack = getBytes.execute(self);
            byte[] needle = getBytes.execute(sub.getSequenceStorage());
            int len2 = lenNode.execute(sub.getSequenceStorage());

            if (earlyExit1.profile(len2 == 0 && start <= len1)) {
                return emptySubIndex(start, end);
            }
            if (earlyExit2.profile(start >= len1 || len1 < len2)) {
                return -1;
            }
            if (len2 == 1) {
                return findElement(haystack, needle[0], start, end);
            }

            return findSubSequence(haystack, needle, len2, start, end > len1 ? len1 : end);
        }

        @Specialization
        int find(SequenceStorage self, int len1, int sub, int start, int end,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached CastToJavaByteNode cast) {
            if (earlyExit.profile(start >= len1)) {
                return -1;
            }
            byte[] haystack = getBytes.execute(self);
            return findElement(haystack, cast.execute(sub), start, end > len1 ? len1 : end);
        }

        @Specialization(guards = "!isBytes(sub)")
        int useIndex(SequenceStorage self, int len1, Object sub, int start, int end,
                        @Cached ConditionProfile earlyExit,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaByteNode cast,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            if (earlyExit.profile(start >= len1)) {
                return -1;
            }
            if (indexCheckNode.execute(sub)) {
                byte[] haystack = getBytes.execute(self);
                byte subByte = cast.execute(indexNode.execute(null, sub));
                return findElement(haystack, subByte, start, end > len1 ? len1 : end);
            } else {
                throw raise(TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "a bytes-like object", sub);
            }
        }

        // Overridden in RFind
        @SuppressWarnings("unused")
        protected int emptySubIndex(int start, int end) {
            return start;
        }

        @TruffleBoundary(allowInlining = true)
        protected static int isEqual(int i, byte[] haystack, byte[] needle, int len2) {
            for (int j = 0; j < len2; j++) {
                if (haystack[i + j] != needle[j]) {
                    return -1;
                }
            }
            return i;
        }

        @TruffleBoundary(allowInlining = true)
        protected int findSubSequence(byte[] haystack, byte[] needle, int len2, int start, int end) {
            // TODO implement a more efficient algorithm
            for (int i = start; i < end - len2 + 1; i++) {
                if (isEqual(i, haystack, needle, len2) != -1) {
                    return i;
                }
            }
            return -1;
        }

        @TruffleBoundary(allowInlining = true)
        protected int findElement(byte[] haystack, byte sub, int start, int end) {
            for (int i = start; i < end; i++) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }

        public static FindNode create() {
            return FindNodeGen.create();
        }
    }

    public abstract static class RFindNode extends FindNode {

        @Override
        protected int emptySubIndex(int start, int end) {
            return (end - start) + start;
        }

        @TruffleBoundary(allowInlining = true)
        @Override
        protected int findSubSequence(byte[] haystack, byte[] needle, int len2, int start, int end) {
            // TODO implement a more efficient algorithm
            for (int i = end - len2; i >= start; i--) {
                if (isEqual(i, haystack, needle, len2) != -1) {
                    return i;
                }
            }
            return -1;
        }

        @TruffleBoundary(allowInlining = true)
        @Override
        protected int findElement(byte[] haystack, byte sub, int start, int end) {
            for (int i = end - 1; i >= start; i--) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }

        public static RFindNode create() {
            return BytesNodesFactory.RFindNodeGen.create();
        }
    }

    public static class FromSequenceStorageNode extends Node {

        @Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Child private CastToByteNode castToByteNode;
        @Child private SequenceStorageNodes.LenNode lenNode;

        public byte[] execute(VirtualFrame frame, SequenceStorage storage) {
            int len = getLenNode().execute(storage);
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                Object item = getGetItemNode().execute(frame, storage, i);
                bytes[i] = getCastToByteNode().execute(frame, item);
            }
            return bytes;
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList()));
            }
            return getItemNode;
        }

        private CastToByteNode getCastToByteNode() {
            if (castToByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToByteNode = insert(CastToByteNode.create());
            }
            return castToByteNode;
        }

        private SequenceStorageNodes.LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode;
        }

        public static FromSequenceStorageNode create() {
            return new FromSequenceStorageNode();
        }
    }

    public static class FromSequenceNode extends Node {

        @Child private FromSequenceStorageNode fromSequenceStorageNode;
        @Child private SequenceNodes.GetSequenceStorageNode getSequenceStorageNode;

        public byte[] execute(VirtualFrame frame, PSequence sequence) {
            if (fromSequenceStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromSequenceStorageNode = insert(FromSequenceStorageNode.create());
                getSequenceStorageNode = insert(SequenceNodes.GetSequenceStorageNode.create());
            }

            return fromSequenceStorageNode.execute(frame, getSequenceStorageNode.execute(sequence));
        }

        public static FromSequenceNode create() {
            return new FromSequenceNode();
        }
    }

    public abstract static class FromIteratorNode extends PNodeWithContext {

        @Child private SequenceStorageNodes.AppendNode appendByteNode;

        public abstract byte[] execute(VirtualFrame frame, Object iterator);

        private SequenceStorageNodes.AppendNode getAppendByteNode() {
            if (appendByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendByteNode = insert(SequenceStorageNodes.AppendNode.create());
            }
            return appendByteNode;
        }

        @Specialization
        byte[] doIt(VirtualFrame frame, Object iterObject,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            ByteSequenceStorage bss = new ByteSequenceStorage(16);
            while (true) {
                try {
                    getAppendByteNode().execute(bss, getNextNode.execute(frame, iterObject), BytesLikeNoGeneralizationNode.SUPPLIER);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return bss.getInternalByteArray();
                }
            }
        }

        public static FromIteratorNode create() {
            return BytesNodesFactory.FromIteratorNodeGen.create();
        }
    }

    public abstract static class CmpNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, PBytesLike left, PBytesLike right);

        @Specialization
        static int cmp(VirtualFrame frame, PBytesLike left, PBytesLike right,
                        @Cached SequenceStorageNodes.GetItemNode getLeftItemNode,
                        @Cached SequenceStorageNodes.GetItemNode getRightItemNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            int llen = lenNode.execute(left.getSequenceStorage());
            int rlen = lenNode.execute(right.getSequenceStorage());
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int a = getLeftItemNode.executeInt(frame, left.getSequenceStorage(), i);
                int b = getRightItemNode.executeInt(frame, right.getSequenceStorage(), i);
                if (a != b) {
                    // CPython uses 'memcmp'; so do unsigned comparison
                    return (a & 0xFF) - (b & 0xFF);
                }
            }
            return llen - rlen;
        }

        public static CmpNode create() {
            return BytesNodesFactory.CmpNodeGen.create();
        }
    }

    public abstract static class ExpectStringNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final int argNum;
        final String className;

        protected ExpectStringNode(int argNum, String className) {
            this.argNum = argNum;
            this.className = className;
        }

        protected String className() {
            return className;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        static Object none(PNone none) {
            return none;
        }

        @Specialization
        static Object str(String str) {
            return str;
        }

        @Specialization
        static Object str(PString str,
                        @Cached StringNodes.StringMaterializeNode toStr) {
            return toStr.execute(str);
        }

        @Fallback
        Object doOthers(@SuppressWarnings("unused") VirtualFrame frame, Object value) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, className(), argNum, PythonBuiltinClassType.PString, value);
        }

        @ClinicConverterFactory
        public static ExpectStringNode create(@ArgumentIndex int argNum, String className) {
            return BytesNodesFactory.ExpectStringNodeGen.create(argNum, className);
        }
    }

    /**
     * Like {@code PyBytes_FromObject}, but returns a Java byte array. The array is guaranteed to be
     * a new copy. Note that {@code PyBytes_FromObject} returns the argument unchanged when it's
     * already bytes. We obviously cannot do that here, it must be done by the caller if the need
     * this behavior.
     */
    public abstract static class BytesFromObject extends PNodeWithContext {
        public abstract byte[] execute(VirtualFrame frame, Object object);

        // TODO make fast paths for builtin list/tuple - note that FromSequenceNode doesn't work
        // properly when the list is mutated by its __index__

        @Specialization
        static byte[] doGeneric(VirtualFrame frame, Object object,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.IterableToByteNode iterableToByteNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PRaiseNode raise) {
            if (bufferAcquireLib.hasBuffer(object)) {
                // TODO PyBUF_FULL_RO
                Object buffer = bufferAcquireLib.acquire(object, BufferFlags.PyBUF_ND);
                try {
                    return bufferLib.getCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer);
                }
            }
            if (!PGuards.isString(object)) {
                try {
                    return iterableToByteNode.execute(frame, object);
                } catch (PException e) {
                    e.expect(TypeError, errorProfile);
                }
            }
            throw raise.raise(TypeError, ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S, object);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class BytesInitNode extends PNodeWithRaise {

        public abstract byte[] execute(VirtualFrame frame, Object source, Object encoding, Object errors);

        @Specialization
        static byte[] none(@SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return PythonUtils.EMPTY_BYTE_ARRAY;
        }

        @Specialization(guards = "isByteStorage(source)")
        static byte[] byteslike(PBytesLike source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return (byte[]) ((ByteSequenceStorage) source.getSequenceStorage()).getCopyOfInternalArrayObject();
        }

        @Specialization(guards = {"!isString(source)", "!isNoValue(source)"})
        byte[] fromObject(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached BytesFromObject bytesFromObject) {
            if (indexCheckNode.execute(source)) {
                try {
                    int size = asSizeNode.executeExact(frame, source);
                    if (size < 0) {
                        throw raise(ValueError, ErrorMessages.NEGATIVE_COUNT);
                    }
                    try {
                        return new byte[size];
                    } catch (OutOfMemoryError error) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raise(MemoryError);
                    }
                } catch (PException e) {
                    e.expect(TypeError, errorProfile);
                    // fallthrough
                }
            }
            return bytesFromObject.execute(frame, source);
        }

        @Specialization(guards = {"isString(source)", "isString(encoding)"})
        byte[] fromString(Object source, Object encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode encodeNode) {
            return encodeNode.execute(source, castStr.execute(encoding), "strict");
        }

        @Specialization(guards = {"isString(source)", "isString(encoding)", "isString(errors)"})
        byte[] fromString(Object source, Object encoding, Object errors,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode encodeNode) {
            return encodeNode.execute(source, castStr.execute(encoding), castStr.execute(errors));
        }

        @Specialization(guards = "isString(source)")
        @SuppressWarnings("unused")
        byte[] fromString(Object source, PNone encoding, Object errors) {
            throw raise(TypeError, ErrorMessages.STRING_ARG_WO_ENCODING);
        }

        @Fallback
        @SuppressWarnings("unused")
        public byte[] error(Object source, Object encoding, Object errors) {
            if (PGuards.isNone(encoding)) {
                throw raise(TypeError, ErrorMessages.ENCODING_ARG_WO_STRING);
            }
            throw raise(TypeError, ErrorMessages.ERRORS_WITHOUT_STR_ARG);
        }
    }

    @GenerateNodeFactory
    public abstract static class ByteToHexNode extends PNodeWithRaise {

        public abstract String execute(byte[] argbuf, int arglen, byte sep, int bytesPerSepGroup);

        @Specialization(guards = "bytesPerSepGroup == 0")
        public static String zero(byte[] argbuf, int arglen, @SuppressWarnings("unused") byte sep, @SuppressWarnings("unused") int bytesPerSepGroup) {

            int resultlen = arglen * 2;
            byte[] retbuf = new byte[resultlen];

            for (int i = 0, j = 0; i < arglen; ++i) {
                assert ((j + 1) < resultlen);
                int c = argbuf[i] & 0xFF;
                retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
            }
            return createASCIIString(retbuf);
        }

        @Specialization(guards = "bytesPerSepGroup < 0")
        public String negative(byte[] argbuf, int arglen, byte sep, int bytesPerSepGroup,
                        @Cached ConditionProfile earlyExit,
                        @Cached ConditionProfile memoryError) {
            if (earlyExit.profile(arglen == 0)) {
                return "";
            }
            int absBytesPerSepGroup = -bytesPerSepGroup;
            /* How many sep characters we'll be inserting. */
            int resultlen = (arglen - 1) / absBytesPerSepGroup;
            if (memoryError.profile(arglen >= SysModuleBuiltins.MAXSIZE / 2 - resultlen)) {
                throw raise(MemoryError);
            }

            resultlen += arglen * 2;

            if (absBytesPerSepGroup >= arglen) {
                return zero(argbuf, arglen, sep, 0);
            }

            byte[] retbuf = new byte[resultlen];
            int chunks = (arglen - 1) / absBytesPerSepGroup;
            int i = 0, j = 0;
            for (int chunk = 0; chunk < chunks; chunk++) {
                for (int k = 0; k < absBytesPerSepGroup; k++) {
                    int c = argbuf[i++] & 0xFF;
                    retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                    retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
                }
                retbuf[j++] = sep;
            }
            while (i < arglen) {
                int c = argbuf[i++] & 0xFF;
                retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
            }

            return createASCIIString(retbuf);
        }

        @Specialization(guards = "absBytesPerSepGroup > 0")
        public String positive(byte[] argbuf, int arglen, byte sep, int absBytesPerSepGroup,
                        @Cached ConditionProfile earlyExit,
                        @Cached ConditionProfile memoryError) {
            if (earlyExit.profile(arglen == 0)) {
                return "";
            }
            /* How many sep characters we'll be inserting. */
            int resultlen = (arglen - 1) / absBytesPerSepGroup;

            if (memoryError.profile(arglen >= SysModuleBuiltins.MAXSIZE / 2 - resultlen)) {
                throw raise(MemoryError);
            }

            resultlen += arglen * 2;

            if (absBytesPerSepGroup >= arglen) {
                return zero(argbuf, arglen, sep, 0);
            }

            byte[] retbuf = new byte[resultlen];
            int chunks = (arglen - 1) / absBytesPerSepGroup;
            int i = arglen - 1;
            int j = resultlen - 1;
            for (int chunk = 0; chunk < chunks; chunk++) {
                for (int k = 0; k < absBytesPerSepGroup; k++) {
                    int c = argbuf[i--] & 0xFF;
                    retbuf[j--] = BytesUtils.HEXDIGITS[c & 0x0f];
                    retbuf[j--] = BytesUtils.HEXDIGITS[c >>> 4];
                }
                retbuf[j--] = sep;
            }
            while (i >= 0) {
                int c = argbuf[i--] & 0xFF;
                retbuf[j--] = BytesUtils.HEXDIGITS[c & 0x0f];
                retbuf[j--] = BytesUtils.HEXDIGITS[c >>> 4];
            }
            return createASCIIString(retbuf);
        }
    }

    public abstract static class IterableToByteNode extends PNodeWithRaise {
        public abstract byte[] execute(VirtualFrame frame, Object iterable);

        @Specialization
        public static byte[] bytearray(VirtualFrame frame, Object iterable,
                        @Cached IteratorNodes.GetLength lenghtHintNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile stopIterationProfile,
                        @Cached CastToByteNode castToByteNode,
                        @Cached PyObjectGetIter getIter) {
            Object it = getIter.execute(frame, iterable);
            int len = lenghtHintNode.execute(frame, iterable);
            byte[] arr = new byte[len < 16 && len > 0 ? len : 16];
            int i = 0;
            while (true) {
                try {
                    byte item = castToByteNode.execute(frame, getNextNode.execute(frame, it));
                    if (i >= arr.length) {
                        arr = resize(arr, arr.length * 2);
                    }
                    arr[i++] = item;
                } catch (PException e) {
                    e.expectStopIteration(stopIterationProfile);
                    return resize(arr, i);
                }
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] resize(byte[] arr, int len) {
            return Arrays.copyOf(arr, len);
        }
    }

    public abstract static class DecodeUTF8FSPathNode extends PNodeWithRaise {

        public byte[] getBytes(VirtualFrame frame, Object value) {
            return utf8StringToBytes(execute(frame, value));
        }

        public abstract String execute(VirtualFrame frame, Object value);

        @Specialization
        static String doit(VirtualFrame frame, Object value,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached CastToJavaStringNode toString,
                        @Cached PosixModuleBuiltins.FspathNode fsPath) {
            Object path = fsPath.execute(frame, value);
            if (bufferAcquireLib.hasBuffer(path)) {
                Object buffer = bufferAcquireLib.acquireReadonly(path);
                try {
                    return encodeFSDefault(bufferLib.getCopiedByteArray(path));
                } finally {
                    bufferLib.release(buffer);
                }
            }
            return toString.execute(path);
        }

        /*-
         * This should be equivalent to PyUnicode_EncodeFSDefault
         * TODO: encoding preference is set per context but will force
         * it to UTF-8 for the time being.
         */
        private static String encodeFSDefault(byte[] path) {
            return createUTF8String(path);
        }
    }
}

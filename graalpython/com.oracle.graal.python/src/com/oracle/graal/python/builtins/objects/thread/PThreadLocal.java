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
package com.oracle.graal.python.builtins.objects.thread;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
@ImportStatic(SpecialMethodSlot.class)
public final class PThreadLocal extends PythonBuiltinObject {
    private final ThreadLocal<PDict> threadLocalDict;
    private final Object[] args;
    private final PKeyword[] keywords;

    public PThreadLocal(Object cls, Shape instanceShape, Object[] args, PKeyword[] keywords) {
        super(cls, instanceShape);
        threadLocalDict = new ThreadLocal<>();
        this.args = args;
        this.keywords = keywords;
    }

    @TruffleBoundary
    public PDict getThreadLocalDict() {
        return threadLocalDict.get();
    }

    @TruffleBoundary
    public void setThreadLocalDict(PDict dict) {
        threadLocalDict.set(dict);
    }

    public Object[] getArgs() {
        return args;
    }

    public PKeyword[] getKeywords() {
        return keywords;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        List<String> keys = getLocalAttributes(hlib);
        return new Keys(keys.toArray(PythonUtils.EMPTY_STRING_ARRAY));
    }

    @TruffleBoundary
    private List<String> getLocalAttributes(HashingStorageLibrary hlib) {
        PDict localDict = getThreadLocalDict();
        List<String> keys = new ArrayList<>();
        if (localDict != null) {
            for (HashingStorage.DictEntry e : hlib.entries(localDict.getDictStorage())) {
                if (e.getKey() instanceof String) {
                    String strKey = (String) e.getKey();
                    keys.add(strKey);
                }
            }
        }
        return keys;
    }

    @Ignore
    private Object readMember(String member, HashingStorageLibrary hlib) {
        PDict localDict = getThreadLocalDict();
        return localDict == null ? null : hlib.getItem(localDict.getDictStorage(), member);
    }

    @ExportMessage
    public boolean isMemberReadable(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        return readMember(member, hlib) != null;
    }

    @ExportMessage
    public boolean isMemberModifiable(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        return readMember(member, hlib) != null;
    }

    @ExportMessage
    public boolean isMemberInsertable(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        return !isMemberReadable(member, hlib);
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        PDict localDict = getThreadLocalDict();
        return localDict != null && PGuards.isCallable(hlib.getItem(localDict.getDictStorage(), member));
    }

    @ExportMessage
    public boolean isMemberRemovable(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        return isMemberReadable(member, hlib);
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Get") LookupCallableSlotInMRONode lookupGet) {
        Object attr = readMember(member, hlib);
        return attr != null && lookupGet.execute(getClassNode.execute(attr)) != PNone.NO_VALUE;
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String member,
                    @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary hlib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Set") LookupCallableSlotInMRONode lookupSet) {
        Object attr = readMember(member, hlib);
        return attr != null && lookupSet.execute(getClassNode.execute(attr)) != PNone.NO_VALUE;
    }
}

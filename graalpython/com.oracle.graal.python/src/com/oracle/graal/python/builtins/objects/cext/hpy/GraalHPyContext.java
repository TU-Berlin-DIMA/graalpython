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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.DataPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.DataPtrPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Double;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPy;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPyTracker;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.HPy_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Int;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Long;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextSignatureType.Void;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CONTEXT_TO_NATIVE;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsIndex;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyAsPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBinaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBoolFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderBuild;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderCancel;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBuilderSet;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesAsString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesFromStringAndSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyBytesGetSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCallBuiltinFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCallTupleDict;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCast;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyCheckBuiltinType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyClose;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDictSetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDump;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyDup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrClear;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrOccurred;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrRaisePredefined;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyErrSetString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFatalError;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatAsDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFloatFromDouble;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyFromPyObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyGetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyHasAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyImportModule;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyInplaceArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIs;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsCallable;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsNumber;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyIsTrue;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListAppend;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyListNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongAsPrimitive;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyModuleCreate;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNewException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyRichcompare;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetAttr;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPySetItem;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTernaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerAdd;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerCleanup;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerForgetAll;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTrackerNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTupleFromArray;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeCheck;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeFromSpec;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyTypeGenericNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnaryArithmetic;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeAsUTF8AndSize;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeAsUTF8String;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeDecodeFSDefault;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromString;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyUnicodeFromWchar;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.ReturnType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAttachFunctionTypeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRaiseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.CanBeDoubleNodeGen;
import com.oracle.graal.python.lib.PyFloatAsDoubleNodeGen;
import com.oracle.graal.python.lib.PyIndexCheckNodeGen;
import com.oracle.graal.python.lib.PyObjectSizeNodeGen;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

import sun.misc.Unsafe;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public class GraalHPyContext extends CExtContext implements TruffleObject {

    private static final boolean TRACE = Boolean.getBoolean("HPyTraceUpcalls");

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.class);

    private static final long SIZEOF_LONG = java.lang.Long.BYTES;

    @TruffleBoundary
    public static GraalHPyContext ensureHPyWasLoaded(Node node, PythonContext context, String name, String path) throws IOException, ApiInitException, ImportException {
        if (!context.hasHPyContext()) {
            /*
             * TODO(fa): Currently, you can't have the HPy context without the C API context. This
             * should eventually be possible but requires some refactoring.
             */
            CApiContext.ensureCapiWasLoaded(node, context, name, path);
            Env env = context.getEnv();
            CompilerDirectives.transferToInterpreterAndInvalidate();

            String libPythonName = "libhpy" + context.getSoAbi();
            TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome());
            TruffleFile capiFile = homePath.resolve(libPythonName);
            try {
                SourceBuilder capiSrcBuilder = Source.newBuilder(PythonLanguage.LLVM_LANGUAGE, capiFile);
                if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                    capiSrcBuilder.internal(true);
                }
                Object hpyLibrary = context.getEnv().parseInternal(capiSrcBuilder.build()).call();
                GraalHPyContext hPyContext = context.createHPyContext(hpyLibrary);

                InteropLibrary interopLibrary = InteropLibrary.getFactory().getUncached(hpyLibrary);
                interopLibrary.invokeMember(hpyLibrary, "graal_hpy_init", hPyContext, new GraalHPyInitObject(hPyContext));
            } catch (PException e) {
                /*
                 * Python exceptions that occur during the HPy API initialization are just passed
                 * through.
                 */
                throw e.getExceptionForReraise();
            } catch (RuntimeException | InteropException e) {
                throw new ApiInitException(CExtContext.wrapJavaException(e, node), name, ErrorMessages.HPY_LOAD_ERROR, capiFile.getAbsoluteFile().getPath());
            }
        }
        return context.getHPyContext();
    }

    /**
     * This method loads an HPy extension module and will initialize the corresponding native
     * contexts if necessary.
     *
     * @param location The node that's requesting this operation. This is required for reporting
     *            correct source code location in case exceptions occur.
     * @param context The Python context object.
     * @param name The name of the module to load (also just required for creating appropriate error
     *            messages).
     * @param path The path of the C extension module to load (usually something ending with
     *            {@code .so} or {@code .dylib} or similar).
     * @param checkResultNode An adopted node instance. This is necessary because the result check
     *            could raise an exception and only an adopted node will report useful source
     *            locations.
     * @return A Python module.
     * @throws IOException If the specified file cannot be loaded.
     * @throws ApiInitException If the corresponding native context could not be initialized.
     * @throws ImportException If an exception occurred during C extension initialization.
     */
    @TruffleBoundary
    public static Object loadHPyModule(Node location, PythonContext context, String name, String path, boolean debug,
                    HPyCheckFunctionResultNode checkResultNode) throws IOException, ApiInitException, ImportException {

        /*
         * Unfortunately, we need eagerly initialize the HPy context because the ctors of the
         * extension may already require some of the symbols defined in the HPy API or C API.
         */
        GraalHPyContext hpyContext = GraalHPyContext.ensureHPyWasLoaded(location, context, name, path);
        Object llvmLibrary = loadLLVMLibrary(location, context, name, path);
        InteropLibrary llvmInteropLib = InteropLibrary.getUncached(llvmLibrary);
        String basename = name.substring(name.lastIndexOf('.') + 1);
        String hpyInitFuncName = "HPyInit_" + basename;
        try {
            if (llvmInteropLib.isMemberExisting(llvmLibrary, hpyInitFuncName)) {
                return hpyContext.initHPyModule(context, llvmLibrary, hpyInitFuncName, name, path, debug, llvmInteropLib, checkResultNode);
            }
            throw new ImportException(null, name, path, ErrorMessages.CANNOT_INITIALIZE_WITH, path, basename, "");
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), name, path, ErrorMessages.CANNOT_INITIALIZE_WITH, path, basename, "");
        }
    }

    @TruffleBoundary
    public final Object initHPyModule(PythonContext context, Object llvmLibrary, String initFuncName, String name, String path, boolean debug,
                    InteropLibrary llvmInteropLib,
                    HPyCheckFunctionResultNode checkResultNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
        Object initFunction;
        try {
            initFunction = llvmInteropLib.readMember(llvmLibrary, initFuncName);
        } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
            throw new ImportException(null, name, path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, path);
        }
        // select appropriate HPy context
        GraalHPyContext hpyContext = debug ? context.getHPyDebugContext() : this;

        InteropLibrary initFunctionLib = InteropLibrary.getUncached(initFunction);
        if (!initFunctionLib.isExecutable(initFunction)) {
            initFunction = HPyAttachFunctionTypeNode.getUncached().execute(hpyContext, initFunction, LLVMType.HPyModule_init);
            // attaching the type could change the type of 'initFunction'; so get a new interop lib
            initFunctionLib = InteropLibrary.getUncached(initFunction);
        }
        Object nativeResult = initFunctionLib.execute(initFunction, hpyContext);
        PythonLanguage language = context.getLanguage();
        return checkResultNode.execute(context.getThreadState(language), hpyContext, name, nativeResult);
    }

    /**
     * Describes the type of an argument or return type in the HPyContext functions.
     */
    enum HPyContextSignatureType {
        Void("void", "VOID", void.class),
        Int("int", "SINT32", int.class),
        Long("long", "SINT64", long.class),
        Double("double", "DOUBLE", double.class),
        UnsignedLongLong("unsigned long long", "UINT64", long.class),
        LongLong("long long", "SINT64", long.class),
        UnsignedLong("unsigned long", "UINT64", long.class),
        HPy("HPy", "POINTER", long.class),
        WideChars("wchar_t*", "STRING", long.class),
        ConstWideChars("const wchart_t*", "STRING", long.class),
        Chars("char*", "STRING", long.class),
        ConsChars("const char*", "STRING", long.class),
        DataPtr("void*", "POINTER", long.class),
        DataPtrPtr("void**", "POINTER", long.class),
        HPyTracker("HPyTracker", "POINTER", long.class),
        HPy_ssize_t("HPy_ssize_t", "UINT64", long.class),
        HPyTupleBuilder("HPyTupleBuilder", "POINTER", long.class),
        HPyListBuilder("HPyListBuilder", "POINTER", long.class),
        cpy_PyObject("cpy_PyObject*", "POINTER", long.class),
        _HPyPtr("_HPyPtr", "POINTER", long.class),
        HPyType_Spec("HPyType_Spec*", "POINTER", long.class),
        HPyType_SpecParam("HPyType_SpecParam*", "POINTER", long.class),
        HPyModuleDef("HPyModuleDef*", "POINTER", long.class);

        /** The type definition used in C source code. */
        final String cType;
        /** The type definition that is used in NFI signatures. */
        final String nfiType;
        /** The type used on the Java side in JNI/CLinker functions. */
        final Class<?> jniType;

        private HPyContextSignatureType(String cType, String nfiType, Class<?> jniType) {
            this.cType = cType;
            this.nfiType = nfiType;
            this.jniType = jniType;
        }
    }

    /**
     * Describes the signature of an HPyContext function.
     */
    static final class HPyContextSignature {
        final HPyContextSignatureType returnType;
        final HPyContextSignatureType[] parameterTypes;

        HPyContextSignature(HPyContextSignatureType returnType, HPyContextSignatureType[] parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }
    }

    private static HPyContextSignature signature(HPyContextSignatureType returnType, HPyContextSignatureType... parameterTypes) {
        return new HPyContextSignature(returnType, parameterTypes);
    }

    /**
     * An enum of the functions currently available in the HPy Context (see {@code public_api.h}).
     */
    enum HPyContextMember {
        NAME("name"),
        PRIVATE("_private"),
        CTX_VERSION("ctx_version"),

        // constants
        H_NONE("h_None"),
        H_TRUE("h_True"),
        H_FALSE("h_False"),
        H_NOTIMPLEMENTED("h_NotImplemented"),
        H_ELLIPSIS("h_Ellipsis"),

        // exception types
        H_BASEEXCEPTION("h_BaseException"),
        H_EXCEPTION("h_Exception"),
        H_STOPASYNCITERATION("h_StopAsyncIteration"),
        H_STOPITERATION("h_StopIteration"),
        H_GENERATOREXIT("h_GeneratorExit"),
        H_ARITHMETICERROR("h_ArithmeticError"),
        H_LOOKUPERROR("h_LookupError"),
        H_ASSERTIONERROR("h_AssertionError"),
        H_ATTRIBUTEERROR("h_AttributeError"),
        H_BUFFERERROR("h_BufferError"),
        H_EOFERROR("h_EOFError"),
        H_FLOATINGPOINTERROR("h_FloatingPointError"),
        H_OSERROR("h_OSError"),
        H_IMPORTERROR("h_ImportError"),
        H_MODULENOTFOUNDERROR("h_ModuleNotFoundError"),
        H_INDEXERROR("h_IndexError"),
        H_KEYERROR("h_KeyError"),
        H_KEYBOARDINTERRUPT("h_KeyboardInterrupt"),
        H_MEMORYERROR("h_MemoryError"),
        H_NAMEERROR("h_NameError"),
        H_OVERFLOWERROR("h_OverflowError"),
        H_RUNTIMEERROR("h_RuntimeError"),
        H_RECURSIONERROR("h_RecursionError"),
        H_NOTIMPLEMENTEDERROR("h_NotImplementedError"),
        H_SYNTAXERROR("h_SyntaxError"),
        H_INDENTATIONERROR("h_IndentationError"),
        H_TABERROR("h_TabError"),
        H_REFERENCEERROR("h_ReferenceError"),
        H_SYSTEMERROR("h_SystemError"),
        H_SYSTEMEXIT("h_SystemExit"),
        H_TYPEERROR("h_TypeError"),
        H_UNBOUNDLOCALERROR("h_UnboundLocalError"),
        H_UNICODEERROR("h_UnicodeError"),
        H_UNICODEENCODEERROR("h_UnicodeEncodeError"),
        H_UNICODEDECODEERROR("h_UnicodeDecodeError"),
        H_UNICODETRANSLATEERROR("h_UnicodeTranslateError"),
        H_VALUEERROR("h_ValueError"),
        H_ZERODIVISIONERROR("h_ZeroDivisionError"),
        H_BLOCKINGIOERROR("h_BlockingIOError"),
        H_BROKENPIPEERROR("h_BrokenPipeError"),
        H_CHILDPROCESSERROR("h_ChildProcessError"),
        H_CONNECTIONERROR("h_ConnectionError"),
        H_CONNECTIONABORTEDERROR("h_ConnectionAbortedError"),
        H_CONNECTIONREFUSEDERROR("h_ConnectionRefusedError"),
        H_CONNECTIONRESETERROR("h_ConnectionResetError"),
        H_FILEEXISTSERROR("h_FileExistsError"),
        H_FILENOTFOUNDERROR("h_FileNotFoundError"),
        H_INTERRUPTEDERROR("h_InterruptedError"),
        H_ISADIRECTORYERROR("h_IsADirectoryError"),
        H_NOTADIRECTORYERROR("h_NotADirectoryError"),
        H_PERMISSIONERROR("h_PermissionError"),
        H_PROCESSLOOKUPERROR("h_ProcessLookupError"),
        H_TIMEOUTERROR("h_TimeoutError"),
        H_WARNING("h_Warning"),
        H_USERWARNING("h_UserWarning"),
        H_DEPRECATIONWARNING("h_DeprecationWarning"),
        H_PENDINGDEPRECATIONWARNING("h_PendingDeprecationWarning"),
        H_SYNTAXWARNING("h_SyntaxWarning"),
        H_RUNTIMEWARNING("h_RuntimeWarning"),
        H_FUTUREWARNING("h_FutureWarning"),
        H_IMPORTWARNING("h_ImportWarning"),
        H_UNICODEWARNING("h_UnicodeWarning"),
        H_BYTESWARNING("h_BytesWarning"),
        H_RESOURCEWARNING("h_ResourceWarning"),

        // built-in types
        H_BASEOBJECTTYPE("h_BaseObjectType"),
        H_TYPETYPE("h_TypeType"),
        H_LONGTYPE("h_LongType"),
        H_UNICODETYPE("h_UnicodeType"),
        H_TUPLETYPE("h_TupleType"),
        H_LISTTYPE("h_ListType"),

        CTX_MODULE_CREATE("ctx_Module_Create"),
        CTX_DUP("ctx_Dup", signature(HPy, HPy)),
        CTX_AS_STRUCT("ctx_AsStruct", signature(DataPtr, HPy)),
        CTX_AS_STRUCT_LEGACY("ctx_AsStructLegacy"),
        CTX_CLOSE("ctx_Close", signature(Void, HPy)),
        CTX_BOOL_FROMLONG("ctx_Bool_FromLong"),
        CTX_LONG_FROMLONG("ctx_Long_FromLong"),
        CTX_LONG_FROMUNSIGNEDLONG("ctx_Long_FromUnsignedLong"),
        CTX_LONG_FROMLONGLONG("ctx_Long_FromLongLong"),
        CTX_LONG_FROM_UNSIGNEDLONGLONG("ctx_Long_FromUnsignedLongLong"),
        CTX_LONG_FROMSSIZE_T("ctx_Long_FromSsize_t"),
        CTX_LONG_FROMSIZE_T("ctx_Long_FromSize_t"),
        CTX_LONG_ASLONG("ctx_Long_AsLong", signature(Long, HPy)),
        CTX_LONG_ASLONGLONG("ctx_Long_AsLongLong"),
        CTX_LONG_ASUNSIGNEDLONG("ctx_Long_AsUnsignedLong"),
        CTX_LONG_ASUNSIGNEDLONGMASK("ctx_Long_AsUnsignedLongMask"),
        CTX_LONG_ASUNSIGNEDLONGLONG("ctx_Long_AsUnsignedLongLong"),
        CTX_LONG_ASUNSIGNEDLONGLONGMASK("ctx_Long_AsUnsignedLongLongMask"),
        CTX_LONG_ASSIZE_T("ctx_Long_AsSize_t"),
        CTX_LONG_ASSSIZE_T("ctx_Long_AsSsize_t"),
        CTX_NEW("ctx_New", signature(HPy, HPy, DataPtrPtr)),
        CTX_TYPE("ctx_Type"),
        CTX_TYPECHECK("ctx_TypeCheck"),
        CTX_IS("ctx_Is"),
        CTX_TYPE_GENERIC_NEW("ctx_Type_GenericNew", signature(HPy, HPy)),
        CTX_FLOAT_FROMDOUBLE("ctx_Float_FromDouble", signature(HPy, Double)),
        CTX_FLOAT_ASDOUBLE("ctx_Float_AsDouble", signature(Double, HPy)),

        // unary
        CTX_NEGATIVE("ctx_Negative"),
        CTX_POSITIVE("ctx_Positive"),
        CTX_ABSOLUTE("ctx_Absolute"),
        CTX_INVERT("ctx_Invert"),
        CTX_INDEX("ctx_Index"),
        CTX_LONG("ctx_Long"),
        CTX_FLOAT("ctx_Float"),

        // binary
        CTX_ADD("ctx_Add"),
        CTX_SUB("ctx_Subtract"),
        CTX_MULTIPLY("ctx_Multiply"),
        CTX_MATRIXMULTIPLY("ctx_MatrixMultiply"),
        CTX_FLOORDIVIDE("ctx_FloorDivide"),
        CTX_TRUEDIVIDE("ctx_TrueDivide"),
        CTX_REMAINDER("ctx_Remainder"),
        CTX_DIVMOD("ctx_Divmod"),
        CTX_LSHIFT("ctx_Lshift"),
        CTX_RSHIFT("ctx_Rshift"),
        CTX_AND("ctx_And"),
        CTX_XOR("ctx_Xor"),
        CTX_OR("ctx_Or"),
        CTX_INPLACEADD("ctx_InPlaceAdd"),
        CTX_INPLACESUBTRACT("ctx_InPlaceSubtract"),
        CTX_INPLACEMULTIPLY("ctx_InPlaceMultiply"),
        CTX_INPLACEMATRIXMULTIPLY("ctx_InPlaceMatrixMultiply"),
        CTX_INPLACEFLOORDIVIDE("ctx_InPlaceFloorDivide"),
        CTX_INPLACETRUEDIVIDE("ctx_InPlaceTrueDivide"),
        CTX_INPLACEREMAINDER("ctx_InPlaceRemainder"),
        // TODO(fa): support IDivMod
        // CTX_INPLACEDIVMOD("ctx_InPlaceDivmod"),
        CTX_INPLACELSHIFT("ctx_InPlaceLshift"),
        CTX_INPLACERSHIFT("ctx_InPlaceRshift"),
        CTX_INPLACEAND("ctx_InPlaceAnd"),
        CTX_INPLACEXOR("ctx_InPlaceXor"),
        CTX_INPLACEOR("ctx_InPlaceOr"),

        // ternary
        CTX_POWER("ctx_Power"),
        CTX_INPLACEPOWER("ctx_InPlacePower"),

        CTX_CALLABLE_CHECK("ctx_Callable_Check"),
        CTX_CALLTUPLEDICT("ctx_CallTupleDict"),
        CTX_ERR_NOMEMORY("ctx_Err_NoMemory"),
        CTX_ERR_SETSTRING("ctx_Err_SetString"),
        CTX_ERR_SETOBJECT("ctx_Err_SetObject"),
        CTX_ERR_OCCURRED("ctx_Err_Occurred"),
        CTX_ERR_CLEAR("ctx_Err_Clear"),
        CTX_ERR_NEWEXCEPTION("ctx_Err_NewException"),
        CTX_ERR_NEWEXCEPTIONWITHDOC("ctx_Err_NewExceptionWithDoc"),
        CTX_FATALERROR("ctx_FatalError"),
        CTX_ISTRUE("ctx_IsTrue"),
        CTX_TYPE_FROM_SPEC("ctx_Type_FromSpec"),
        CTX_GETATTR("ctx_GetAttr"),
        CTX_GETATTR_S("ctx_GetAttr_s"),
        CTX_HASATTR("ctx_HasAttr"),
        CTX_HASATTR_S("ctx_HasAttr_s"),
        CTX_SETATTR("ctx_SetAttr"),
        CTX_SETATTR_S("ctx_SetAttr_s"),
        CTX_GETITEM("ctx_GetItem"),
        CTX_GETITEM_I("ctx_GetItem_i", signature(HPy, HPy, HPy_ssize_t)),
        CTX_GETITEM_S("ctx_GetItem_s"),
        CTX_SETITEM("ctx_SetItem"),
        CTX_SETITEM_I("ctx_SetItem_i"),
        CTX_SETITEM_S("ctx_SetItem_s"),
        CTX_BYTES_CHECK("ctx_Bytes_Check"),
        CTX_BYTES_SIZE("ctx_Bytes_Size"),
        CTX_BYTES_GET_SIZE("ctx_Bytes_GET_SIZE"),
        CTX_BYTES_ASSTRING("ctx_Bytes_AsString"),
        CTX_BYTES_AS_STRING("ctx_Bytes_AS_STRING"),
        CTX_BYTES_FROMSTRING("ctx_Bytes_FromString"),
        CTX_BYTES_FROMSTRINGANDSIZE("ctx_Bytes_FromStringAndSize"),
        CTX_UNICODE_FROMSTRING("ctx_Unicode_FromString"),
        CTX_UNICODE_CHECK("ctx_Unicode_Check"),
        CTX_UNICODE_ASUTF8STRING("ctx_Unicode_AsUTF8String"),
        CTX_UNICODE_ASUTF8ANDSIZE("ctx_Unicode_AsUTF8AndSize"),
        CTX_UNICODE_FROMWIDECHAR("ctx_Unicode_FromWideChar"),
        CTX_UNICODE_DECODEFSDEFAULT("ctx_Unicode_DecodeFSDefault"),
        CTX_LIST_NEW("ctx_List_New"),
        CTX_LIST_APPEND("ctx_List_Append"),
        CTX_DICT_CHECK("ctx_Dict_Check"),
        CTX_DICT_NEW("ctx_Dict_New"),
        CTX_DICT_SETITEM("ctx_Dict_SetItem"),
        CTX_DICT_GETITEM("ctx_Dict_GetItem"),
        CTX_FROMPYOBJECT("ctx_FromPyObject"),
        CTX_ASPYOBJECT("ctx_AsPyObject"),
        CTX_CALLREALFUNCTIONFROMTRAMPOLINE("ctx_CallRealFunctionFromTrampoline"),
        CTX_REPR("ctx_Repr"),
        CTX_STR("ctx_Str"),
        CTX_ASCII("ctx_ASCII"),
        CTX_BYTES("ctx_Bytes"),
        CTX_RICHCOMPARE("ctx_RichCompare"),
        CTX_RICHCOMPAREBOOL("ctx_RichCompareBool"),
        CTX_HASH("ctx_Hash"),
        CTX_NUMBER_CHECK("ctx_Number_Check", signature(Int, HPy)),
        CTX_LENGTH("ctx_Length", signature(HPy_ssize_t, HPy)),
        CTX_IMPORT_IMPORTMODULE("ctx_Import_ImportModule"),
        CTX_TUPLE_CHECK("ctx_Tuple_Check"),
        CTX_TUPLE_FROMARRAY("ctx_Tuple_FromArray"),
        CTX_TUPLE_BUILDER_NEW("ctx_TupleBuilder_New"),
        CTX_TUPLE_BUILDER_SET("ctx_TupleBuilder_Set"),
        CTX_TUPLE_BUILDER_BUILD("ctx_TupleBuilder_Build"),
        CTX_TUPLE_BUILDER_CANCEL("ctx_TupleBuilder_Cancel"),
        CTX_LIST_CHECK("ctx_List_Check", signature(Int, HPy)),
        CTX_LIST_BUILDER_NEW("ctx_ListBuilder_New"),
        CTX_LIST_BUILDER_SET("ctx_ListBuilder_Set"),
        CTX_LIST_BUILDER_BUILD("ctx_ListBuilder_Build"),
        CTX_LIST_BUILDER_CANCEL("ctx_ListBuilder_Cancel"),
        CTX_TRACKER_NEW("ctx_Tracker_New", signature(HPyTracker, HPy_ssize_t)),
        CTX_TRACKER_ADD("ctx_Tracker_Add", signature(Int, HPyTracker, HPy)),
        CTX_TRACKER_FORGET_ALL("ctx_Tracker_ForgetAll"),
        CTX_TRACKER_CLOSE("ctx_Tracker_Close", signature(Void, HPyTracker)),
        CTX_DUMP("ctx_Dump");

        final String name;

        /**
         * If this signature is present (non-null), then a corresponding function in
         * {@link GraalHPyContext} needs to exist. E.g., for {@code ctx_Number_Check}, the function
         * {@link GraalHPyContext#ctxNumberCheck(long)} is used.
         */
        final HPyContextSignature signature;

        HPyContextMember(String name) {
            this.name = name;
            this.signature = null;
        }

        HPyContextMember(String name, HPyContextSignature signature) {
            this.name = name;
            this.signature = signature;
        }

        @CompilationFinal(dimensions = 1) private static final HPyContextMember[] VALUES = values();
        public static final HashMap<String, HPyContextMember> MEMBERS = new HashMap<>();
        public static final Object KEYS;

        static {
            for (HPyContextMember member : VALUES) {
                MEMBERS.put(member.name, member);
            }

            String[] names = new String[VALUES.length];
            for (int i = 0; i < names.length; i++) {
                names[i] = VALUES[i].name;
            }
            KEYS = new PythonAbstractObject.Keys(names);
        }

        @TruffleBoundary
        public static int getIndex(String key) {
            HPyContextMember member = HPyContextMember.MEMBERS.get(key);
            return member == null ? -1 : member.ordinal();
        }
    }

    /**
     * Enum of C types used in the HPy API. These type names need to stay in sync with the
     * declarations in 'hpytypes.h'.
     */
    public enum LLVMType {
        HPyFunc_noargs,
        HPyFunc_o,
        HPyFunc_varargs,
        HPyFunc_keywords,
        HPyFunc_unaryfunc,
        HPyFunc_binaryfunc,
        HPyFunc_ternaryfunc,
        HPyFunc_inquiry,
        HPyFunc_lenfunc,
        HPyFunc_ssizeargfunc,
        HPyFunc_ssizessizeargfunc,
        HPyFunc_ssizeobjargproc,
        HPyFunc_ssizessizeobjargproc,
        HPyFunc_objobjargproc,
        HPyFunc_freefunc,
        HPyFunc_getattrfunc,
        HPyFunc_getattrofunc,
        HPyFunc_setattrfunc,
        HPyFunc_setattrofunc,
        HPyFunc_reprfunc,
        HPyFunc_hashfunc,
        HPyFunc_richcmpfunc,
        HPyFunc_getiterfunc,
        HPyFunc_iternextfunc,
        HPyFunc_descrgetfunc,
        HPyFunc_descrsetfunc,
        HPyFunc_initproc,
        HPyFunc_getter,
        HPyFunc_setter,
        HPyFunc_objobjproc,
        HPyFunc_getbufferproc,
        HPyFunc_releasebufferproc,
        HPyFunc_destroyfunc,
        HPyModule_init;

        public static GraalHPyNativeSymbol getGetterFunctionName(LLVMType llvmType) {
            CompilerAsserts.neverPartOfCompilation();
            String getterFunctionName = "get_" + llvmType.name() + "_typeid";
            if (!GraalHPyNativeSymbol.isValid(getterFunctionName)) {
                throw CompilerDirectives.shouldNotReachHere("Unknown C API function " + getterFunctionName);
            }
            return GraalHPyNativeSymbol.getByName(getterFunctionName);
        }
    }

    private GraalHPyHandle[] hpyHandleTable = new GraalHPyHandle[]{GraalHPyHandle.NULL_HANDLE};
    private final HandleStack freeStack = new HandleStack(16);
    Object nativePointer;

    @CompilationFinal(dimensions = 1) protected final Object[] hpyContextMembers;

    /** the native type ID of C struct 'HPyContext' */
    @CompilationFinal private Object hpyContextNativeTypeID;

    /** the native type ID of C struct 'HPy' */
    @CompilationFinal private Object hpyNativeTypeID;
    @CompilationFinal private Object hpyArrayNativeTypeID;
    @CompilationFinal private long wcharSize = -1;

    /**
     * This field mirrors value of {@link PythonOptions#HPyEnableJNIFastPaths}. We store it in this
     * final field because the value is also used in non-PE code paths.
     */
    private final boolean useNativeFastPaths;

    /**
     * The global reference queue is a list consisting of {@link GraalHPyHandleReference} objects.
     * It is used to keep those objects (which are weak refs) alive until they are enqueued in the
     * corresponding reference queue. The list instance referenced by this variable is exclusively
     * owned by the main thread (i.e. the main thread may operate on the list without
     * synchronization). The HPy reference cleaner thread (see
     * {@link GraalHPyReferenceCleanerRunnable}) will consume this instance using an atomic
     * {@code getAndSet} operation. At this point, the ownership is transferred to the cleaner
     * thread.
     */
    public final AtomicReference<GraalHPyHandleReference> references = new AtomicReference<>(null);
    private ReferenceQueue<Object> nativeSpaceReferenceQueue;
    @CompilationFinal private RootCallTarget referenceCleanerCallTarget;
    private Thread hpyReferenceCleanerThread;

    private PythonObjectSlowPathFactory slowPathFactory;

    public GraalHPyContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, GraalHPyConversionNodeSupplier.HANDLE);
        this.hpyContextMembers = createMembers(context, getName());
        this.useNativeFastPaths = context.getLanguage().getEngineOption(PythonOptions.HPyEnableJNIFastPaths);
    }

    protected String getName() {
        return "HPy Universal ABI (GraalVM backend)";
    }

    /**
     * Reference cleaner action that will be executed by the {@link AsyncHandler}.
     */
    private static final class GraalHPyHandleReferenceCleanerAction implements AsyncHandler.AsyncAction {

        private final GraalHPyHandleReference[] nativeObjectReferences;

        public GraalHPyHandleReferenceCleanerAction(GraalHPyHandleReference[] nativeObjectReferences) {
            this.nativeObjectReferences = nativeObjectReferences;
        }

        @Override
        public void execute(PythonContext context) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, nativeObjectReferences);
            GenericInvokeNode.getUncached().execute(context.getHPyContext().getReferenceCleanerCallTarget(), pArguments);
        }
    }

    private RootCallTarget getReferenceCleanerCallTarget() {
        if (referenceCleanerCallTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            referenceCleanerCallTarget = PythonUtils.getOrCreateCallTarget(new HPyNativeSpaceCleanerRootNode(getContext()));
        }
        return referenceCleanerCallTarget;
    }

    /**
     * This is the HPy cleaner thread runnable. It will run in parallel to the main thread, collect
     * references from the corresponding reference queue, and eventually call
     * {@link HPyNativeSpaceCleanerRootNode}. For this, the cleaner thread consumes the
     * {@link #references} list by exchanging it with an empty one (for a description of the
     * exchanging process, see also {@link #references}).
     */
    static final class GraalHPyReferenceCleanerRunnable implements Runnable {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyReferenceCleanerRunnable.class);
        private final ReferenceQueue<?> referenceQueue;
        private GraalHPyHandleReference cleanerList;

        GraalHPyReferenceCleanerRunnable(ReferenceQueue<?> referenceQueue) {
            this.referenceQueue = referenceQueue;
        }

        @Override
        public void run() {
            try {
                PythonContext pythonContext = PythonContext.get(null);
                PythonLanguage language = pythonContext.getLanguage();
                GraalHPyContext hPyContext = pythonContext.getHPyContext();
                RootCallTarget callTarget = hPyContext.getReferenceCleanerCallTarget();
                PDict dummyGlobals = PythonObjectFactory.getUncached().createDict();
                boolean isLoggable = LOGGER.isLoggable(Level.FINE);
                /*
                 * Intentionally retrieve the thread state every time since this will kill the
                 * thread if shutting down.
                 */
                while (!pythonContext.getThreadState(language).isShuttingDown()) {
                    Reference<?> reference = null;
                    try {
                        reference = referenceQueue.remove();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    ArrayList<GraalHPyHandleReference> refs = new ArrayList<>();
                    do {
                        if (reference instanceof GraalHPyHandleReference) {
                            refs.add((GraalHPyHandleReference) reference);
                        }
                        // consume all
                        reference = referenceQueue.poll();
                    } while (reference != null);

                    if (isLoggable) {
                        LOGGER.fine(() -> "Collected references: " + refs.size());
                    }

                    /*
                     * To avoid race conditions, we take the whole references list such that we can
                     * solely process it. At this point, the references list is owned by the main
                     * thread and this will now transfer ownership to the cleaner thread. The list
                     * will be replaced by an empty list (which will then be owned by the main
                     * thread).
                     */
                    GraalHPyHandleReference refList;
                    do {
                        /*
                         * If 'refList' is null then the main is currently updating it. So, we need
                         * to repeat until we get something. The written empty list will just be
                         * lost.
                         */
                        refList = hPyContext.references.getAndSet(null);
                    } while (refList == null);

                    if (!refs.isEmpty()) {
                        try {
                            Object[] arguments = PArguments.create(3);
                            PArguments.setGlobals(arguments, dummyGlobals);
                            PArguments.setException(arguments, PException.NO_EXCEPTION);
                            PArguments.setCallerFrameInfo(arguments, PFrame.Reference.EMPTY);
                            PArguments.setArgument(arguments, 0, refs.toArray(new GraalHPyHandleReference[0]));
                            PArguments.setArgument(arguments, 1, refList);
                            PArguments.setArgument(arguments, 2, cleanerList);
                            cleanerList = (GraalHPyHandleReference) CallTargetInvokeNode.invokeUncached(callTarget, arguments);
                        } catch (PException e) {
                            /*
                             * Since the cleaner thread is not running any Python code, we should
                             * never receive a Python exception. If it happens, consider that to be
                             * a problem (however, it is not fatal problem).
                             */
                            PException exceptionForReraise = e.getExceptionForReraise();
                            exceptionForReraise.setMessage(exceptionForReraise.getUnreifiedException().getFormattedMessage());
                            LOGGER.warning("HPy reference cleaner thread received a Python exception: " + e);
                        }
                    }
                }
            } catch (PythonThreadKillException e) {
                // this is exception shuts down the thread
                LOGGER.fine("HPy reference cleaner thread received exit signal.");
            } catch (ControlFlowException e) {
                LOGGER.warning("HPy reference cleaner thread received unexpected control flow exception.");
            } catch (Exception e) {
                LOGGER.severe("HPy reference cleaner thread received fatal exception: " + e);
            }
            LOGGER.fine("HPy reference cleaner thread is exiting.");
        }
    }

    /**
     * Root node that actually runs the destroy functions for the native memory of unreachable
     * Python objects.
     */
    private static final class HPyNativeSpaceCleanerRootNode extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"refs"}, PythonUtils.EMPTY_STRING_ARRAY);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyContext.HPyNativeSpaceCleanerRootNode.class);

        @Child private PCallHPyFunction callBulkFree;

        HPyNativeSpaceCleanerRootNode(PythonContext context) {
            super(context.getLanguage());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            /*
             * This node is not running any Python code in the sense that it does not run any code
             * that would run in CPython's interpreter loop. So, we don't need to do a
             * calleeContext.enter/exit since we should never get any Python exception.
             */

            GraalHPyHandleReference[] handleReferences = (GraalHPyHandleReference[]) PArguments.getArgument(frame, 0);
            GraalHPyHandleReference refList = (GraalHPyHandleReference) PArguments.getArgument(frame, 1);
            GraalHPyHandleReference oldRefList = (GraalHPyHandleReference) PArguments.getArgument(frame, 2);
            long startTime = 0;
            long middleTime = 0;
            final int n = handleReferences.length;
            boolean loggable = LOGGER.isLoggable(Level.FINE);

            if (loggable) {
                startTime = System.currentTimeMillis();
            }

            GraalHPyContext context = PythonContext.get(this).getHPyContext();

            if (CompilerDirectives.inInterpreter()) {
                com.oracle.truffle.api.nodes.LoopNode.reportLoopCount(this, n);
            }

            // mark queued references as cleaned
            for (int i = 0; i < n; i++) {
                handleReferences[i].cleaned = true;
            }

            // remove marked references from the global reference list such that they can die
            GraalHPyHandleReference prev = null;
            for (GraalHPyHandleReference cur = refList; cur != null; cur = cur.next) {
                if (cur.cleaned) {
                    if (prev != null) {
                        prev.next = cur.next;
                    } else {
                        // new head
                        refList = cur.next;
                    }
                } else {
                    prev = cur;
                }
            }

            /*
             * Merge the received reference list into the existing one or just take it if there
             * wasn't one before.
             */
            if (prev != null) {
                // if prev exists, it now points to the tail
                prev.next = oldRefList;
            } else {
                refList = oldRefList;
            }

            if (loggable) {
                middleTime = System.currentTimeMillis();
            }

            NativeSpaceArrayWrapper nativeSpaceArrayWrapper = new NativeSpaceArrayWrapper(handleReferences);
            if (callBulkFree == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBulkFree = insert(PCallHPyFunctionNodeGen.create());
            }
            callBulkFree.call(context, GraalHPyNativeSymbol.GRAAL_HPY_BULK_FREE, nativeSpaceArrayWrapper, nativeSpaceArrayWrapper.getArraySize());

            if (loggable) {
                final long countDuration = middleTime - startTime;
                final long duration = System.currentTimeMillis() - middleTime;
                LOGGER.fine(() -> "Cleaned references: " + n);
                LOGGER.fine(() -> "Count duration: " + countDuration);
                LOGGER.fine(() -> "Duration: " + duration);
            }
            return refList;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public String getName() {
            return "hpy_native_reference_cleaner";
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    final void setHPyContextNativeType(Object nativeType) {
        this.hpyContextNativeTypeID = nativeType;
    }

    final void setHPyNativeType(Object hpyNativeTypeID) {
        assert this.hpyNativeTypeID == null : "setting HPy native type ID a second time";
        this.hpyNativeTypeID = hpyNativeTypeID;
    }

    public final Object getHPyNativeType() {
        assert this.hpyNativeTypeID != null : "HPy native type ID not available";
        return hpyNativeTypeID;
    }

    final void setHPyArrayNativeType(Object hpyArrayNativeTypeID) {
        assert this.hpyArrayNativeTypeID == null : "setting HPy* native type ID a second time";
        this.hpyArrayNativeTypeID = hpyArrayNativeTypeID;
    }

    public final Object getHPyArrayNativeType() {
        assert this.hpyArrayNativeTypeID != null : "HPy* native type ID not available";
        return hpyArrayNativeTypeID;
    }

    final void setWcharSize(long wcharSize) {
        assert this.wcharSize == -1 : "setting wchar size a second time";
        this.wcharSize = wcharSize;
    }

    public final long getWcharSize() {
        assert this.wcharSize >= 0 : "wchar size is not available";
        return wcharSize;
    }

    @ExportMessage
    final boolean isPointer() {
        return nativePointer != null;
    }

    @ExportMessage(limit = "1")
    @SuppressWarnings("static-method")
    final long asPointer(
                    @CachedLibrary("this.nativePointer") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        if (isPointer()) {
            return interopLibrary.asPointer(nativePointer);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UnsupportedMessageException.create();
    }

    private Object setNativeSpaceFunction;

    /**
     * Encodes a long value such that it responds to {@link InteropLibrary#isPointer(Object)}
     * messages.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class HPyContextNativePointer implements TruffleObject {

        private final long pointer;

        public HPyContextNativePointer(long pointer) {
            this.pointer = pointer;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long asPointer() {
            return pointer;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        void toNative() {
            // nothing to do
        }
    }

    private static long castLong(Object value) {
        try {
            return value instanceof Long ? (long) value : InteropLibrary.getUncached().asPointer(value);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere("cannot cast " + value);
        }
    }

    @ExportMessage
    final void toNative() {
        if (!isPointer()) {
            CompilerDirectives.transferToInterpreter();
            nativePointer = PCallHPyFunctionNodeGen.getUncached().call(this, GRAAL_HPY_CONTEXT_TO_NATIVE, this, new GraalHPyJNIContext(this));
            PythonLanguage language = PythonLanguage.get(null);
            if (language.getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.JNI) {
                loadJNIBackend();
                if (initJNI(this, castLong(nativePointer)) != 0) {
                    throw new RuntimeException("Could not initialize HPy JNI backend.");
                }
            }
            if (useNativeFastPaths) {
                PythonContext context = getContext();
                Source src = Source.newBuilder("nfi", "load \"" + getJNILibrary() + "\"", "load " + PythonContext.PYTHON_JNI_LIBRARY_NAME).build();
                CallTarget lib = context.getEnv().parseInternal(src);
                InteropLibrary interop = InteropLibrary.getUncached();
                try {
                    Object rlib = lib.call();
                    Object augmentFunction = interop.invokeMember(interop.readMember(rlib, "initDirectFastPaths"), "bind", "(POINTER):VOID");
                    interop.execute(augmentFunction, nativePointer);
                    setNativeSpaceFunction = interop.invokeMember(interop.readMember(rlib, "setHPyContextNativeSpace"), "bind", "(POINTER, SINT64):VOID");

                    /*
                     * Allocate a native array for the native space pointers of HPy objects and
                     * initialize it.
                     */
                    allocateNativeSpacePointersMirror();

                    interop.execute(setNativeSpaceFunction, nativePointer, nativeSpacePointers);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    private static boolean jniBackendLoaded = false;

    private String getJNILibrary() {
        CompilerAsserts.neverPartOfCompilation();
        return Paths.get(getContext().getJNIHome(), PythonContext.PYTHON_JNI_LIBRARY_NAME).toString();
    }

    private void loadJNIBackend() {
        if (!(ImageInfo.inImageBuildtimeCode() || jniBackendLoaded)) {
            String pythonJNIPath = getJNILibrary();
            LOGGER.fine("Loading HPy JNI backend from " + pythonJNIPath);
            try {
                System.load(pythonJNIPath);
                jniBackendLoaded = true;
            } catch (NullPointerException | UnsatisfiedLinkError e) {
                LOGGER.fine("HPy JNI backend library could not be found: " + pythonJNIPath);
            }
        }
    }

    /* HPy JNI trampoline declarations */

    @TruffleBoundary
    static native void hpyCallDestroyFunc(long nativeSpace, long destroyFunc);

    @TruffleBoundary
    public static native long executePrimitive1(long target, long arg1);

    @TruffleBoundary
    public static native long executePrimitive2(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native long executePrimitive3(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executePrimitive4(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native long executePrimitive5(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native long executePrimitive6(long target, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6);

    @TruffleBoundary
    public static native long executePrimitive7(long target, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6, long arg7);

    @TruffleBoundary
    public static native long executePrimitive8(long target, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6, long arg7, long arg8);

    @TruffleBoundary
    public static native long executePrimitive9(long target, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6, long arg7, long arg8, long arg9);

    @TruffleBoundary
    public static native long executePrimitive10(long target, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6, long arg7, long arg8, long arg9, long arg10);

    @TruffleBoundary
    public static native int executeInquiry(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native int executeSsizeobjargproc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeSsizesizeobjargproc(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native int executeObjobjproc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native int executeObjobjargproc(long target, long arg1, long arg2, long arg3, long arg4);

    @TruffleBoundary
    public static native int executeInitproc(long target, long arg1, long arg2, long arg3, long arg4, long arg5);

    @TruffleBoundary
    public static native void executeFreefunc(long target, long arg1, long arg2);

    @TruffleBoundary
    public static native int executeGetbufferproc(long target, long arg1, long arg2, long arg3, int arg4);

    @TruffleBoundary
    public static native void executeReleasebufferproc(long target, long arg1, long arg2, long arg3);

    @TruffleBoundary
    public static native long executeRichcomparefunc(long target, long arg1, long arg2, long arg3, int arg4);

    @TruffleBoundary
    private static native int initJNI(GraalHPyContext context, long nativePointer);

    public enum Counter {
        UpcallCast,
        UpcallNew,
        UpcallTypeGenericNew,
        UpcallTrackerClose,
        UpcallTrackerAdd,
        UpcallClose,
        UpcallTrackerNew,
        UpcallGetItemI,
        UpcallSetItem,
        UpcallSetItemI,
        UpcallDup,
        UpcallNumberCheck,
        UpcallLength,
        UpcallListCheck,
        UpcallLongAsLong,
        UpcallLongFromLong,
        UpcallFloatAsDouble,
        UpcallFloatFromDouble,
        UpcallUnicodeFromWideChar,
        UpcallUnicodeFromJCharArray,
        UpcallDictNew,
        UpcallListNew;

        long count;

        void increment() {
            if (TRACE) {
                count++;
            }
        }
    }

    static {
        if (TRACE) {
            Thread thread = new Thread(() -> {

                while (true) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        // fall through
                    }
                    System.out.println("====  HPy counts");
                    for (Counter c : Counter.values()) {
                        System.out.printf("  %20s: %8d\n", c.name(), c.count);
                    }
                }

            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Returns a Python object factory that should only be used on the slow path. The factory object
     * is initialized lazily.
     */
    private PythonObjectSlowPathFactory factory() {
        if (slowPathFactory == null) {
            slowPathFactory = new PythonObjectSlowPathFactory(getContext().getAllocationReporter());
        }
        return slowPathFactory;
    }

    @SuppressWarnings("static-method")
    public final long ctxFloatFromDouble(double value) {
        Counter.UpcallFloatFromDouble.increment();
        return GraalHPyBoxing.boxDouble(value);
    }

    public final double ctxFloatAsDouble(long handle) {
        Counter.UpcallFloatAsDouble.increment();

        if (GraalHPyBoxing.isBoxedDouble(handle)) {
            return GraalHPyBoxing.unboxDouble(handle);
        } else if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).getDelegate();
            return PyFloatAsDoubleNodeGen.getUncached().execute(null, object);
        }

    }

    public final long ctxLongAsLong(long handle) {
        Counter.UpcallLongAsLong.increment();

        if (GraalHPyBoxing.isBoxedInt(handle)) {
            return GraalHPyBoxing.unboxInt(handle);
        } else {
            Object object = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).getDelegate();
            try {
                return (long) AsNativePrimitiveNodeGen.getUncached().execute(object, 1, java.lang.Long.BYTES, true);
            } catch (PException e) {
                HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
                return -1L;
            }
        }
    }

    public final long ctxLongFromLong(long l) {
        Counter.UpcallLongFromLong.increment();

        if (com.oracle.graal.python.builtins.objects.ints.PInt.isIntRange(l)) {
            return GraalHPyBoxing.boxInt((int) l);
        }
        return createHandle(l).getId(this, ConditionProfile.getUncached());
    }

    public final long ctxAsStruct(long handle) {
        Counter.UpcallCast.increment();

        Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).getDelegate();
        return (long) HPyGetNativeSpacePointerNodeGen.getUncached().execute(receiver);
    }

    public final long ctxNew(long typeHandle, long dataOutVar) {
        Counter.UpcallNew.increment();

        Object type = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle)).getDelegate();
        PythonObject pythonObject;

        /*
         * Check if argument is actually a type. We will only accept PythonClass because that's the
         * only one that makes sense here.
         */
        if (type instanceof PythonClass) {
            PythonClass clazz = (PythonClass) type;

            // allocate native space
            long basicSize = clazz.basicSize;
            if (basicSize == -1) {
                // create the managed Python object
                pythonObject = factory().createPythonObject(clazz, clazz.getInstanceShape());
            } else {
                /*
                 * Since this is a JNI upcall method, we know that (1) we are not running in some
                 * managed mode, and (2) the data will be used in real native code. Hence, we can
                 * immediately allocate native memory via Unsafe.
                 */
                long dataPtr = unsafe.allocateMemory(basicSize);
                unsafe.setMemory(dataPtr, basicSize, (byte) 0);
                unsafe.putLong(dataOutVar, dataPtr);
                pythonObject = factory().createPythonHPyObject(clazz, dataPtr);
                Object destroyFunc = clazz.hpyDestroyFunc;
                createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
            }
        } else {
            // check if argument is still a type (e.g. a built-in type, ...)
            if (!IsTypeNode.getUncached().execute(type)) {
                return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(this, 0, PythonBuiltinClassType.TypeError, "HPy_New arg 1 must be a type");
            }
            // TODO(fa): this should actually call __new__
            pythonObject = factory().createPythonObject(type);
        }
        return GraalHPyBoxing.boxHandle(createHandle(pythonObject).getId(this, ConditionProfile.getUncached()));
    }

    public final long ctxTypeGenericNew(long typeHandle) {
        Counter.UpcallTypeGenericNew.increment();

        Object type = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(typeHandle)).getDelegate();

        if (type instanceof PythonClass) {
            PythonClass clazz = (PythonClass) type;

            PythonObject pythonObject;
            long basicSize = clazz.basicSize;
            if (basicSize != -1) {
                // allocate native space
                long dataPtr = unsafe.allocateMemory(basicSize);
                unsafe.setMemory(dataPtr, basicSize, (byte) 0);
                pythonObject = factory().createPythonHPyObject(clazz, dataPtr);
            } else {
                pythonObject = factory().createPythonObject(clazz);
            }
            return GraalHPyBoxing.boxHandle(createHandle(pythonObject).getId(this, ConditionProfile.getUncached()));
        }
        throw CompilerDirectives.shouldNotReachHere("not implemented");
    }

    public final void ctxClose(long handle) {
        Counter.UpcallClose.increment();
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).closeAndInvalidate(this);
        }
    }

    public final long ctxDup(long handle) {
        Counter.UpcallDup.increment();
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            GraalHPyHandle pyHandle = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle));
            return GraalHPyBoxing.boxHandle(createHandle(pyHandle.getDelegate()).getId(this, ConditionProfile.getUncached()));
        } else {
            return handle;
        }
    }

    public final long ctxGetItemi(long hCollection, long lidx) {
        Counter.UpcallGetItemI.increment();
        try {
            // If handle 'hCollection' is a boxed int or double, the object is not subscriptable.
            if (!GraalHPyBoxing.isBoxedHandle(hCollection)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, 0);
            }
            Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hCollection)).getDelegate();
            Object clazz = GetClassNode.getUncached().execute(receiver);
            if (clazz == PythonBuiltinClassType.PList || clazz == PythonBuiltinClassType.PTuple) {
                if (!PInt.isIntRange(lidx)) {
                    throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, lidx);
                }
                int idx = (int) lidx;
                PSequence sequence = (PSequence) receiver;
                SequenceStorage storage = sequence.getSequenceStorage();
                if (storage instanceof IntSequenceStorage) {
                    return GraalHPyBoxing.boxInt(((IntSequenceStorage) storage).getIntItemNormalized(idx));
                } else if (storage instanceof DoubleSequenceStorage) {
                    return GraalHPyBoxing.boxDouble(((DoubleSequenceStorage) storage).getDoubleItemNormalized(idx));
                } else if (storage instanceof LongSequenceStorage) {
                    long lresult = ((LongSequenceStorage) storage).getLongItemNormalized(idx);
                    if (com.oracle.graal.python.builtins.objects.ints.PInt.isIntRange(lresult)) {
                        return GraalHPyBoxing.boxInt((int) lresult);
                    }
                    return GraalHPyBoxing.boxHandle(createHandle(lresult).getId(this, ConditionProfile.getUncached()));
                } else if (storage instanceof ObjectSequenceStorage) {
                    Object result = ((ObjectSequenceStorage) storage).getItemNormalized(idx);
                    return GraalHPyBoxing.boxHandle(createHandle(result).getId(this, ConditionProfile.getUncached()));
                }
                // TODO: other storages...
            }
            Object result = PInteropSubscriptNode.getUncached().execute(receiver, lidx);
            return GraalHPyBoxing.boxHandle(createHandle(result).getId(this, ConditionProfile.getUncached()));
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            // NULL handle
            return 0;
        }
    }

    /**
     * HPy signature: {@code HPy_SetItem(HPyContext ctx, HPy obj, HPy key, HPy value)}
     * 
     * @param hSequence
     * @param hKey
     * @param hValue
     * @return {@code 0} on success; {@code -1} on error
     */
    public final int ctxSetItem(long hSequence, long hKey, long hValue) {
        Counter.UpcallSetItem.increment();
        try {
            // If handle 'hSequence' is a boxed int or double, the object is not a sequence.
            if (!GraalHPyBoxing.isBoxedHandle(hSequence)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hSequence)).getDelegate();
            Object clazz = GetClassNode.getUncached().execute(receiver);
            Object key = HPyAsPythonObjectNodeGen.getUncached().execute(this, hKey);
            Object value = HPyAsPythonObjectNodeGen.getUncached().execute(this, hValue);

            // fast path
            if (clazz == PythonBuiltinClassType.PDict) {
                PDict dict = (PDict) receiver;
                HashingStorage dictStorage = dict.getDictStorage();

                // super-fast path for string keys
                if (key instanceof String) {
                    if (dictStorage instanceof EmptyStorage) {
                        dictStorage = PDict.createNewStorage(true, 1);
                        dict.setDictStorage(dictStorage);
                    }

                    if (dictStorage instanceof HashMapStorage) {
                        ((HashMapStorage) dictStorage).put((String) key, value);
                        return 0;
                    }
                    // fall through to generic case
                }
                dict.setDictStorage(HashingStorageLibrary.getUncached().setItem(dictStorage, key, value));
                return 0;
            } else if (clazz == PythonBuiltinClassType.PList && PGuards.isInteger(key) && ctxListSetItem(receiver, ((Number) key).longValue(), hValue)) {
                return 0;
            }
            return setItemGeneric(receiver, clazz, key, value);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            // non-null value indicates an error
            return -1;
        }
    }

    public final int ctxSetItemi(long hSequence, long lidx, long hValue) {
        Counter.UpcallSetItemI.increment();
        try {
            // If handle 'hSequence' is a boxed int or double, the object is not a sequence.
            if (!GraalHPyBoxing.isBoxedHandle(hSequence)) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, 0);
            }
            Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hSequence)).getDelegate();
            Object clazz = GetClassNode.getUncached().execute(receiver);

            if (clazz == PythonBuiltinClassType.PList && ctxListSetItem(receiver, lidx, hValue)) {
                return 0;
            }
            Object value = HPyAsPythonObjectNodeGen.getUncached().execute(this, hValue);
            return setItemGeneric(receiver, clazz, lidx, value);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            // non-null value indicates an error
            return -1;
        }
    }

    private boolean ctxListSetItem(Object receiver, long lidx, long hValue) {
        // fast path for list
        if (!PInt.isIntRange(lidx)) {
            throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.IndexError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, lidx);
        }
        int idx = (int) lidx;
        PList sequence = (PList) receiver;
        SequenceStorage storage = sequence.getSequenceStorage();
        if (storage instanceof IntSequenceStorage && GraalHPyBoxing.isBoxedInt(hValue)) {
            ((IntSequenceStorage) storage).setIntItemNormalized(idx, GraalHPyBoxing.unboxInt(hValue));
            return true;
        } else if (storage instanceof DoubleSequenceStorage && GraalHPyBoxing.isBoxedDouble(hValue)) {
            ((DoubleSequenceStorage) storage).setDoubleItemNormalized(idx, GraalHPyBoxing.unboxDouble(hValue));
            return true;
        } else if (storage instanceof LongSequenceStorage && GraalHPyBoxing.isBoxedInt(hValue)) {
            ((LongSequenceStorage) storage).setLongItemNormalized(idx, GraalHPyBoxing.unboxInt(hValue));
            return true;
        } else if (storage instanceof ObjectSequenceStorage) {
            Object value = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(hValue)).getDelegate();
            ((ObjectSequenceStorage) storage).setItemNormalized(idx, value);
            return true;
        }
        // TODO: other storages...
        return false;
    }

    private static int setItemGeneric(Object receiver, Object clazz, Object key, Object value) {
        Object setItemAttribute = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.SetItem).execute(clazz);
        if (setItemAttribute == PNone.NO_VALUE) {
            throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, receiver);
        }
        CallTernaryMethodNode.getUncached().execute(null, setItemAttribute, receiver, key, value);
        return 0;
    }

    public final int ctxNumberCheck(long handle) {
        Counter.UpcallNumberCheck.increment();
        if (GraalHPyBoxing.isBoxedDouble(handle) || GraalHPyBoxing.isBoxedInt(handle)) {
            return 1;
        }
        Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).getDelegate();

        try {
            if (PyIndexCheckNodeGen.getUncached().execute(receiver) || CanBeDoubleNodeGen.getUncached().execute(receiver)) {
                return 1;
            }
            Object receiverType = GetClassNode.getUncached().execute(receiver);
            return PInt.intValue(LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Int).execute(receiverType) != PNone.NO_VALUE);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            return 0;
        }
    }

    public final long ctxLength(long handle) {
        Counter.UpcallLength.increment();
        assert GraalHPyBoxing.isBoxedHandle(handle);

        Object receiver = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).getDelegate();

        Object clazz = GetClassNode.getUncached().execute(receiver);
        if (clazz == PythonBuiltinClassType.PList || clazz == PythonBuiltinClassType.PTuple) {
            PSequence sequence = (PSequence) receiver;
            SequenceStorage storage = sequence.getSequenceStorage();
            return storage.length();
        }
        try {
            return PyObjectSizeNodeGen.getUncached().execute(null, receiver);
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            return -1;
        }
    }

    public final int ctxListCheck(long handle) {
        Counter.UpcallListCheck.increment();
        if (GraalHPyBoxing.isBoxedHandle(handle)) {
            Object obj = getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(handle)).getDelegate();
            Object clazz = GetClassNode.getUncached().execute(obj);
            return PInt.intValue(clazz == PythonBuiltinClassType.PList || IsSubtypeNodeGen.getUncached().execute(clazz, PythonBuiltinClassType.PList));
        } else {
            return 0;
        }
    }

    public final long ctxUnicodeFromWideChar(long wcharArrayPtr, long size) {
        Counter.UpcallUnicodeFromWideChar.increment();

        if (!PInt.isIntRange(size)) {
            // NULL handle
            return 0;
        }
        int isize = (int) size;

        char[] decoded = new char[isize];
        for (int i = 0; i < size; i++) {
            int wchar = unsafe.getInt(wcharArrayPtr + (long) Integer.BYTES * i);
            if (Character.isBmpCodePoint(wchar)) {
                decoded[i] = (char) wchar;
            } else {
                // TODO(fa): handle this case
                throw new RuntimeException();
            }
        }
        return createHandle(new String(decoded, 0, isize)).getId(this, ConditionProfile.getUncached());
    }

    public final long ctxUnicodeFromJCharArray(char[] arr) {
        Counter.UpcallUnicodeFromJCharArray.increment();
        return createHandle(new String(arr, 0, arr.length)).getId(this, ConditionProfile.getUncached());
    }

    public final long ctxDictNew() {
        Counter.UpcallDictNew.increment();
        PDict dict = PythonObjectFactory.getUncached().createDict();
        return createHandle(dict).getId(this, ConditionProfile.getUncached());
    }

    public final long ctxListNew(long llen) {
        try {
            Counter.UpcallListNew.increment();
            int len = CastToJavaIntExactNode.getUncached().execute(llen);
            Object[] data = new Object[len];
            Arrays.fill(data, PNone.NONE);
            PList list = PythonObjectFactory.getUncached().createList(data);
            return createHandle(list).getId(this, ConditionProfile.getUncached());
        } catch (PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(this, e);
            // NULL handle
            return 0;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return HPyContextMember.KEYS;
    }

    @ExportMessage
    @ImportStatic(HPyContextMember.class)
    static class IsMemberReadable {
        @Specialization(guards = "cachedKey.equals(key)", limit = "1")
        static boolean isMemberReadableCached(@SuppressWarnings("unused") GraalHPyContext context, @SuppressWarnings("unused") String key,
                        @Cached(value = "key") @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)") int cachedIdx) {
            return cachedIdx != -1;
        }

        @Specialization(replaces = "isMemberReadableCached")
        static boolean isMemberReadable(@SuppressWarnings("unused") GraalHPyContext context, String key) {
            return HPyContextMember.getIndex(key) != -1;
        }
    }

    @ExportMessage
    final Object readMember(String key,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode) {
        return readMemberNode.execute(this, key);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    final Object getNativeType() {
        return hpyContextNativeTypeID;
    }

    @GenerateUncached
    @ImportStatic(HPyContextMember.class)
    abstract static class GraalHPyReadMemberNode extends Node {

        public abstract Object execute(GraalHPyContext hpyContext, String key);

        @Specialization(guards = "cachedKey.equals(key)", limit = "1")
        static Object doMemberCached(GraalHPyContext hpyContext, String key,
                        @Cached(value = "key") @SuppressWarnings("unused") String cachedKey,
                        @Cached(value = "getIndex(key)") int cachedIdx) {
            // TODO(fa) once everything is implemented, remove this check
            if (cachedIdx != -1) {
                Object value = hpyContext.hpyContextMembers[cachedIdx];
                if (value != null) {
                    return value;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.format("context function %s not yet implemented: ", key));
        }

        @Specialization(replaces = "doMemberCached")
        static Object doMember(GraalHPyContext hpyContext, String key,
                        @Cached(value = "key", allowUncached = true) @SuppressWarnings("unused") String cachedKey) {
            return doMemberCached(hpyContext, key, key, HPyContextMember.getIndex(key));
        }
    }

    @ExportMessage
    final boolean isMemberInvocable(String key,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib) {
        Object member = readMemberNode.execute(this, key);
        return member != null && memberInvokeLib.isExecutable(member);
    }

    @ExportMessage
    final Object invokeMember(String key, Object[] args,
                    @Shared("readMemberNode") @Cached GraalHPyReadMemberNode readMemberNode,
                    @Shared("memberInvokeLib") @CachedLibrary(limit = "1") InteropLibrary memberInvokeLib)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        Object member = readMemberNode.execute(this, key);
        assert member != null;
        /*
         * Optimization: the first argument *MUST* always be the context. If not, we can just set
         * 'this'.
         */
        args[0] = this;
        return memberInvokeLib.execute(member, args);
    }

    private static Object[] createMembers(PythonContext context, String name) {
        Object[] members = new Object[HPyContextMember.VALUES.length];
        Python3Core core = context.getCore();

        members[HPyContextMember.NAME.ordinal()] = new CStringWrapper(name);
        createIntConstant(members, HPyContextMember.CTX_VERSION, 1);

        createConstant(members, HPyContextMember.H_NONE, PNone.NONE);
        createConstant(members, HPyContextMember.H_TRUE, core.getTrue());
        createConstant(members, HPyContextMember.H_FALSE, core.getFalse());
        createConstant(members, HPyContextMember.H_NOTIMPLEMENTED, PNotImplemented.NOT_IMPLEMENTED);
        createConstant(members, HPyContextMember.H_ELLIPSIS, PEllipsis.INSTANCE);

        createTypeConstant(members, HPyContextMember.H_BASEEXCEPTION, core, PythonBuiltinClassType.PBaseException);
        createTypeConstant(members, HPyContextMember.H_EXCEPTION, core, PythonBuiltinClassType.Exception);
        createTypeConstant(members, HPyContextMember.H_STOPASYNCITERATION, core, PythonBuiltinClassType.StopAsyncIteration);
        createTypeConstant(members, HPyContextMember.H_STOPITERATION, core, PythonBuiltinClassType.StopIteration);
        createTypeConstant(members, HPyContextMember.H_GENERATOREXIT, core, PythonBuiltinClassType.GeneratorExit);
        createTypeConstant(members, HPyContextMember.H_ARITHMETICERROR, core, PythonBuiltinClassType.ArithmeticError);
        createTypeConstant(members, HPyContextMember.H_LOOKUPERROR, core, PythonBuiltinClassType.LookupError);
        createTypeConstant(members, HPyContextMember.H_ASSERTIONERROR, core, PythonBuiltinClassType.AssertionError);
        createTypeConstant(members, HPyContextMember.H_ATTRIBUTEERROR, core, PythonBuiltinClassType.AttributeError);
        createTypeConstant(members, HPyContextMember.H_BUFFERERROR, core, PythonBuiltinClassType.BufferError);
        createTypeConstant(members, HPyContextMember.H_EOFERROR, core, PythonBuiltinClassType.EOFError);
        createTypeConstant(members, HPyContextMember.H_FLOATINGPOINTERROR, core, PythonBuiltinClassType.FloatingPointError);
        createTypeConstant(members, HPyContextMember.H_OSERROR, core, PythonBuiltinClassType.OSError);
        createTypeConstant(members, HPyContextMember.H_IMPORTERROR, core, PythonBuiltinClassType.ImportError);
        createTypeConstant(members, HPyContextMember.H_MODULENOTFOUNDERROR, core, PythonBuiltinClassType.ModuleNotFoundError);
        createTypeConstant(members, HPyContextMember.H_INDEXERROR, core, PythonBuiltinClassType.IndexError);
        createTypeConstant(members, HPyContextMember.H_KEYERROR, core, PythonBuiltinClassType.KeyError);
        createTypeConstant(members, HPyContextMember.H_KEYBOARDINTERRUPT, core, PythonBuiltinClassType.KeyboardInterrupt);
        createTypeConstant(members, HPyContextMember.H_MEMORYERROR, core, PythonBuiltinClassType.MemoryError);
        createTypeConstant(members, HPyContextMember.H_NAMEERROR, core, PythonBuiltinClassType.NameError);
        createTypeConstant(members, HPyContextMember.H_OVERFLOWERROR, core, PythonBuiltinClassType.OverflowError);
        createTypeConstant(members, HPyContextMember.H_RUNTIMEERROR, core, PythonBuiltinClassType.RuntimeError);
        createTypeConstant(members, HPyContextMember.H_RECURSIONERROR, core, PythonBuiltinClassType.RecursionError);
        createTypeConstant(members, HPyContextMember.H_NOTIMPLEMENTEDERROR, core, PythonBuiltinClassType.NotImplementedError);
        createTypeConstant(members, HPyContextMember.H_SYNTAXERROR, core, PythonBuiltinClassType.SyntaxError);
        createTypeConstant(members, HPyContextMember.H_INDENTATIONERROR, core, PythonBuiltinClassType.IndentationError);
        createTypeConstant(members, HPyContextMember.H_TABERROR, core, PythonBuiltinClassType.TabError);
        createTypeConstant(members, HPyContextMember.H_REFERENCEERROR, core, PythonBuiltinClassType.ReferenceError);
        createTypeConstant(members, HPyContextMember.H_SYSTEMERROR, core, PythonBuiltinClassType.SystemError);
        createTypeConstant(members, HPyContextMember.H_SYSTEMEXIT, core, PythonBuiltinClassType.SystemExit);
        createTypeConstant(members, HPyContextMember.H_TYPEERROR, core, PythonBuiltinClassType.TypeError);
        createTypeConstant(members, HPyContextMember.H_UNBOUNDLOCALERROR, core, PythonBuiltinClassType.UnboundLocalError);
        createTypeConstant(members, HPyContextMember.H_UNICODEERROR, core, PythonBuiltinClassType.UnicodeError);
        createTypeConstant(members, HPyContextMember.H_UNICODEENCODEERROR, core, PythonBuiltinClassType.UnicodeEncodeError);
        createTypeConstant(members, HPyContextMember.H_UNICODEDECODEERROR, core, PythonBuiltinClassType.UnicodeDecodeError);
        createTypeConstant(members, HPyContextMember.H_UNICODETRANSLATEERROR, core, PythonBuiltinClassType.UnicodeTranslateError);
        createTypeConstant(members, HPyContextMember.H_VALUEERROR, core, PythonBuiltinClassType.ValueError);
        createTypeConstant(members, HPyContextMember.H_ZERODIVISIONERROR, core, PythonBuiltinClassType.ZeroDivisionError);
        createTypeConstant(members, HPyContextMember.H_BLOCKINGIOERROR, core, PythonBuiltinClassType.BlockingIOError);
        createTypeConstant(members, HPyContextMember.H_BROKENPIPEERROR, core, PythonBuiltinClassType.BrokenPipeError);
        createTypeConstant(members, HPyContextMember.H_CHILDPROCESSERROR, core, PythonBuiltinClassType.ChildProcessError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONERROR, core, PythonBuiltinClassType.ConnectionError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONABORTEDERROR, core, PythonBuiltinClassType.ConnectionAbortedError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONREFUSEDERROR, core, PythonBuiltinClassType.ConnectionRefusedError);
        createTypeConstant(members, HPyContextMember.H_CONNECTIONRESETERROR, core, PythonBuiltinClassType.ConnectionResetError);
        createTypeConstant(members, HPyContextMember.H_FILEEXISTSERROR, core, PythonBuiltinClassType.FileExistsError);
        createTypeConstant(members, HPyContextMember.H_FILENOTFOUNDERROR, core, PythonBuiltinClassType.FileNotFoundError);
        createTypeConstant(members, HPyContextMember.H_INTERRUPTEDERROR, core, PythonBuiltinClassType.InterruptedError);
        createTypeConstant(members, HPyContextMember.H_ISADIRECTORYERROR, core, PythonBuiltinClassType.IsADirectoryError);
        createTypeConstant(members, HPyContextMember.H_NOTADIRECTORYERROR, core, PythonBuiltinClassType.NotADirectoryError);
        createTypeConstant(members, HPyContextMember.H_PERMISSIONERROR, core, PythonBuiltinClassType.PermissionError);
        createTypeConstant(members, HPyContextMember.H_PROCESSLOOKUPERROR, core, PythonBuiltinClassType.ProcessLookupError);
        createTypeConstant(members, HPyContextMember.H_TIMEOUTERROR, core, PythonBuiltinClassType.TimeoutError);
        createTypeConstant(members, HPyContextMember.H_WARNING, core, PythonBuiltinClassType.Warning);
        createTypeConstant(members, HPyContextMember.H_USERWARNING, core, PythonBuiltinClassType.UserWarning);
        createTypeConstant(members, HPyContextMember.H_DEPRECATIONWARNING, core, PythonBuiltinClassType.DeprecationWarning);
        createTypeConstant(members, HPyContextMember.H_PENDINGDEPRECATIONWARNING, core, PythonBuiltinClassType.PendingDeprecationWarning);
        createTypeConstant(members, HPyContextMember.H_SYNTAXWARNING, core, PythonBuiltinClassType.SyntaxWarning);
        createTypeConstant(members, HPyContextMember.H_RUNTIMEWARNING, core, PythonBuiltinClassType.RuntimeWarning);
        createTypeConstant(members, HPyContextMember.H_FUTUREWARNING, core, PythonBuiltinClassType.FutureWarning);
        createTypeConstant(members, HPyContextMember.H_IMPORTWARNING, core, PythonBuiltinClassType.ImportWarning);
        createTypeConstant(members, HPyContextMember.H_UNICODEWARNING, core, PythonBuiltinClassType.UnicodeWarning);
        createTypeConstant(members, HPyContextMember.H_BYTESWARNING, core, PythonBuiltinClassType.BytesWarning);
        createTypeConstant(members, HPyContextMember.H_RESOURCEWARNING, core, PythonBuiltinClassType.ResourceWarning);

        createTypeConstant(members, HPyContextMember.H_BASEOBJECTTYPE, core, PythonBuiltinClassType.PythonObject);
        createTypeConstant(members, HPyContextMember.H_TYPETYPE, core, PythonBuiltinClassType.PythonClass);
        createTypeConstant(members, HPyContextMember.H_LONGTYPE, core, PythonBuiltinClassType.PInt);
        createTypeConstant(members, HPyContextMember.H_UNICODETYPE, core, PythonBuiltinClassType.PString);
        createTypeConstant(members, HPyContextMember.H_TUPLETYPE, core, PythonBuiltinClassType.PTuple);
        createTypeConstant(members, HPyContextMember.H_LISTTYPE, core, PythonBuiltinClassType.PList);

        members[HPyContextMember.CTX_ASPYOBJECT.ordinal()] = new GraalHPyAsPyObject();
        members[HPyContextMember.CTX_DUP.ordinal()] = new GraalHPyDup();
        members[HPyContextMember.CTX_CLOSE.ordinal()] = new GraalHPyClose();
        members[HPyContextMember.CTX_MODULE_CREATE.ordinal()] = new GraalHPyModuleCreate();
        members[HPyContextMember.CTX_BOOL_FROMLONG.ordinal()] = new GraalHPyBoolFromLong();
        GraalHPyLongFromLong fromSignedLong = new GraalHPyLongFromLong();
        GraalHPyLongFromLong fromUnsignedLong = new GraalHPyLongFromLong(false);
        members[HPyContextMember.CTX_LONG_FROMLONG.ordinal()] = fromSignedLong;
        members[HPyContextMember.CTX_LONG_FROMUNSIGNEDLONG.ordinal()] = fromUnsignedLong;
        members[HPyContextMember.CTX_LONG_FROMLONGLONG.ordinal()] = fromSignedLong;
        members[HPyContextMember.CTX_LONG_FROM_UNSIGNEDLONGLONG.ordinal()] = fromUnsignedLong;
        members[HPyContextMember.CTX_LONG_FROMSSIZE_T.ordinal()] = fromSignedLong;
        members[HPyContextMember.CTX_LONG_FROMSIZE_T.ordinal()] = fromUnsignedLong;
        GraalHPyLongAsPrimitive asSignedLong = new GraalHPyLongAsPrimitive(1, java.lang.Long.BYTES, true);
        GraalHPyLongAsPrimitive asUnsignedLong = new GraalHPyLongAsPrimitive(0, java.lang.Long.BYTES, true, true);
        GraalHPyLongAsPrimitive asUnsignedLongMask = new GraalHPyLongAsPrimitive(0, java.lang.Long.BYTES, false);
        members[HPyContextMember.CTX_LONG_ASLONG.ordinal()] = asSignedLong;
        members[HPyContextMember.CTX_LONG_ASLONGLONG.ordinal()] = asSignedLong;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONG.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONG.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGMASK.ordinal()] = asUnsignedLongMask;
        members[HPyContextMember.CTX_LONG_ASUNSIGNEDLONGLONGMASK.ordinal()] = asUnsignedLongMask;
        members[HPyContextMember.CTX_LONG_ASSIZE_T.ordinal()] = asUnsignedLong;
        members[HPyContextMember.CTX_LONG_ASSSIZE_T.ordinal()] = new GraalHPyLongAsPrimitive(1, java.lang.Long.BYTES, true, true);
        members[HPyContextMember.CTX_NEW.ordinal()] = new GraalHPyNew();
        members[HPyContextMember.CTX_TYPE.ordinal()] = new GraalHPyType();
        members[HPyContextMember.CTX_TYPECHECK.ordinal()] = new GraalHPyTypeCheck();
        members[HPyContextMember.CTX_IS.ordinal()] = new GraalHPyIs();
        members[HPyContextMember.CTX_TYPE_GENERIC_NEW.ordinal()] = new GraalHPyTypeGenericNew();
        GraalHPyCast graalHPyCast = new GraalHPyCast();
        members[HPyContextMember.CTX_AS_STRUCT.ordinal()] = graalHPyCast;
        members[HPyContextMember.CTX_AS_STRUCT_LEGACY.ordinal()] = graalHPyCast;

        // unary
        members[HPyContextMember.CTX_NEGATIVE.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Neg);
        members[HPyContextMember.CTX_POSITIVE.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Pos);
        members[HPyContextMember.CTX_ABSOLUTE.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.ABS, 1);
        members[HPyContextMember.CTX_INVERT.ordinal()] = new GraalHPyUnaryArithmetic(UnaryArithmetic.Invert);
        members[HPyContextMember.CTX_INDEX.ordinal()] = new GraalHPyAsIndex();
        members[HPyContextMember.CTX_LONG.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.INT, 1);
        members[HPyContextMember.CTX_FLOAT.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.FLOAT, 1);

        // binary
        members[HPyContextMember.CTX_ADD.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Add);
        members[HPyContextMember.CTX_SUB.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Sub);
        members[HPyContextMember.CTX_MULTIPLY.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Mul);
        members[HPyContextMember.CTX_MATRIXMULTIPLY.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.MatMul);
        members[HPyContextMember.CTX_FLOORDIVIDE.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.FloorDiv);
        members[HPyContextMember.CTX_TRUEDIVIDE.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.TrueDiv);
        members[HPyContextMember.CTX_REMAINDER.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Mod);
        members[HPyContextMember.CTX_DIVMOD.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.DivMod);
        members[HPyContextMember.CTX_LSHIFT.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.LShift);
        members[HPyContextMember.CTX_RSHIFT.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.RShift);
        members[HPyContextMember.CTX_AND.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.And);
        members[HPyContextMember.CTX_XOR.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Xor);
        members[HPyContextMember.CTX_OR.ordinal()] = new GraalHPyBinaryArithmetic(BinaryArithmetic.Or);
        members[HPyContextMember.CTX_INPLACEADD.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IAdd);
        members[HPyContextMember.CTX_INPLACESUBTRACT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ISub);
        members[HPyContextMember.CTX_INPLACEMULTIPLY.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMul);
        members[HPyContextMember.CTX_INPLACEMATRIXMULTIPLY.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMatMul);
        members[HPyContextMember.CTX_INPLACEFLOORDIVIDE.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IFloorDiv);
        members[HPyContextMember.CTX_INPLACETRUEDIVIDE.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ITrueDiv);
        members[HPyContextMember.CTX_INPLACEREMAINDER.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IMod);
        members[HPyContextMember.CTX_INPLACELSHIFT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.ILShift);
        members[HPyContextMember.CTX_INPLACERSHIFT.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IRShift);
        members[HPyContextMember.CTX_INPLACEAND.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IAnd);
        members[HPyContextMember.CTX_INPLACEXOR.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IXor);
        members[HPyContextMember.CTX_INPLACEOR.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IOr);

        // ternary
        members[HPyContextMember.CTX_POWER.ordinal()] = new GraalHPyTernaryArithmetic(TernaryArithmetic.Pow);
        members[HPyContextMember.CTX_INPLACEPOWER.ordinal()] = new GraalHPyInplaceArithmetic(InplaceArithmetic.IPow);

        members[HPyContextMember.CTX_CALLABLE_CHECK.ordinal()] = new GraalHPyIsCallable();
        members[HPyContextMember.CTX_CALLTUPLEDICT.ordinal()] = new GraalHPyCallTupleDict();

        members[HPyContextMember.CTX_DICT_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PDict);
        members[HPyContextMember.CTX_DICT_NEW.ordinal()] = new GraalHPyDictNew();
        members[HPyContextMember.CTX_DICT_SETITEM.ordinal()] = new GraalHPyDictSetItem();
        members[HPyContextMember.CTX_DICT_GETITEM.ordinal()] = new GraalHPyDictGetItem();
        members[HPyContextMember.CTX_LIST_NEW.ordinal()] = new GraalHPyListNew();
        members[HPyContextMember.CTX_LIST_APPEND.ordinal()] = new GraalHPyListAppend();
        members[HPyContextMember.CTX_FLOAT_FROMDOUBLE.ordinal()] = new GraalHPyFloatFromDouble();
        members[HPyContextMember.CTX_FLOAT_ASDOUBLE.ordinal()] = new GraalHPyFloatAsDouble();
        members[HPyContextMember.CTX_BYTES_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PBytes);
        members[HPyContextMember.CTX_BYTES_GET_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMember.CTX_BYTES_SIZE.ordinal()] = new GraalHPyBytesGetSize();
        members[HPyContextMember.CTX_BYTES_AS_STRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMember.CTX_BYTES_ASSTRING.ordinal()] = new GraalHPyBytesAsString();
        members[HPyContextMember.CTX_BYTES_FROMSTRING.ordinal()] = new GraalHPyBytesFromStringAndSize(false);
        members[HPyContextMember.CTX_BYTES_FROMSTRINGANDSIZE.ordinal()] = new GraalHPyBytesFromStringAndSize(true);

        members[HPyContextMember.CTX_ERR_NOMEMORY.ordinal()] = new GraalHPyErrRaisePredefined(PythonBuiltinClassType.MemoryError);
        members[HPyContextMember.CTX_ERR_SETSTRING.ordinal()] = new GraalHPyErrSetString(true);
        members[HPyContextMember.CTX_ERR_SETOBJECT.ordinal()] = new GraalHPyErrSetString(false);
        members[HPyContextMember.CTX_ERR_OCCURRED.ordinal()] = new GraalHPyErrOccurred();
        members[HPyContextMember.CTX_ERR_CLEAR.ordinal()] = new GraalHPyErrClear();
        members[HPyContextMember.CTX_ERR_NEWEXCEPTION.ordinal()] = new GraalHPyNewException(false);
        members[HPyContextMember.CTX_ERR_NEWEXCEPTIONWITHDOC.ordinal()] = new GraalHPyNewException(true);
        members[HPyContextMember.CTX_FATALERROR.ordinal()] = new GraalHPyFatalError();
        members[HPyContextMember.CTX_FROMPYOBJECT.ordinal()] = new GraalHPyFromPyObject();
        members[HPyContextMember.CTX_UNICODE_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PString);
        members[HPyContextMember.CTX_ISTRUE.ordinal()] = new GraalHPyIsTrue();
        members[HPyContextMember.CTX_UNICODE_ASUTF8STRING.ordinal()] = new GraalHPyUnicodeAsUTF8String();
        members[HPyContextMember.CTX_UNICODE_ASUTF8ANDSIZE.ordinal()] = new GraalHPyUnicodeAsUTF8AndSize();
        members[HPyContextMember.CTX_UNICODE_FROMSTRING.ordinal()] = new GraalHPyUnicodeFromString();
        members[HPyContextMember.CTX_UNICODE_FROMWIDECHAR.ordinal()] = new GraalHPyUnicodeFromWchar();
        members[HPyContextMember.CTX_UNICODE_DECODEFSDEFAULT.ordinal()] = new GraalHPyUnicodeDecodeFSDefault();
        members[HPyContextMember.CTX_TYPE_FROM_SPEC.ordinal()] = new GraalHPyTypeFromSpec();
        members[HPyContextMember.CTX_GETATTR.ordinal()] = new GraalHPyGetAttr(OBJECT);
        members[HPyContextMember.CTX_GETATTR_S.ordinal()] = new GraalHPyGetAttr(CHAR_PTR);
        members[HPyContextMember.CTX_HASATTR.ordinal()] = new GraalHPyHasAttr(OBJECT);
        members[HPyContextMember.CTX_HASATTR_S.ordinal()] = new GraalHPyHasAttr(CHAR_PTR);
        members[HPyContextMember.CTX_SETATTR.ordinal()] = new GraalHPySetAttr(OBJECT);
        members[HPyContextMember.CTX_SETATTR_S.ordinal()] = new GraalHPySetAttr(CHAR_PTR);
        members[HPyContextMember.CTX_GETITEM.ordinal()] = new GraalHPyGetItem(OBJECT);
        members[HPyContextMember.CTX_GETITEM_S.ordinal()] = new GraalHPyGetItem(CHAR_PTR);
        members[HPyContextMember.CTX_GETITEM_I.ordinal()] = new GraalHPyGetItem(INT32);
        members[HPyContextMember.CTX_SETITEM.ordinal()] = new GraalHPySetItem(OBJECT);
        members[HPyContextMember.CTX_SETITEM_S.ordinal()] = new GraalHPySetItem(CHAR_PTR);
        members[HPyContextMember.CTX_SETITEM_I.ordinal()] = new GraalHPySetItem(INT32);
        members[HPyContextMember.CTX_REPR.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.REPR, 1);
        members[HPyContextMember.CTX_STR.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.STR, 1);
        members[HPyContextMember.CTX_ASCII.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.ASCII, 1);
        members[HPyContextMember.CTX_BYTES.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.BYTES, 1);
        members[HPyContextMember.CTX_RICHCOMPARE.ordinal()] = new GraalHPyRichcompare(false);
        members[HPyContextMember.CTX_RICHCOMPAREBOOL.ordinal()] = new GraalHPyRichcompare(true);
        members[HPyContextMember.CTX_HASH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.HASH, 1, ReturnType.INT, GraalHPyConversionNodeSupplier.TO_INT64);
        members[HPyContextMember.CTX_NUMBER_CHECK.ordinal()] = new GraalHPyIsNumber();
        members[HPyContextMember.CTX_LENGTH.ordinal()] = new GraalHPyCallBuiltinFunction(BuiltinNames.LEN, 1, ReturnType.INT, GraalHPyConversionNodeSupplier.TO_INT64);
        members[HPyContextMember.CTX_IMPORT_IMPORTMODULE.ordinal()] = new GraalHPyImportModule();
        members[HPyContextMember.CTX_TUPLE_FROMARRAY.ordinal()] = new GraalHPyTupleFromArray();
        members[HPyContextMember.CTX_TUPLE_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PTuple);

        GraalHPyBuilderNew graalHPyBuilderNew = new GraalHPyBuilderNew();
        GraalHPyBuilderSet graalHPyBuilderSet = new GraalHPyBuilderSet();
        GraalHPyBuilderCancel graalHPyBuilderCancel = new GraalHPyBuilderCancel();
        members[HPyContextMember.CTX_TUPLE_BUILDER_NEW.ordinal()] = graalHPyBuilderNew;
        members[HPyContextMember.CTX_TUPLE_BUILDER_SET.ordinal()] = graalHPyBuilderSet;
        members[HPyContextMember.CTX_TUPLE_BUILDER_BUILD.ordinal()] = new GraalHPyBuilderBuild(PythonBuiltinClassType.PTuple);
        members[HPyContextMember.CTX_TUPLE_BUILDER_CANCEL.ordinal()] = graalHPyBuilderCancel;

        members[HPyContextMember.CTX_LIST_CHECK.ordinal()] = new GraalHPyCheckBuiltinType(PythonBuiltinClassType.PList);
        members[HPyContextMember.CTX_LIST_BUILDER_NEW.ordinal()] = graalHPyBuilderNew;
        members[HPyContextMember.CTX_LIST_BUILDER_SET.ordinal()] = graalHPyBuilderSet;
        members[HPyContextMember.CTX_LIST_BUILDER_BUILD.ordinal()] = new GraalHPyBuilderBuild(PythonBuiltinClassType.PList);
        members[HPyContextMember.CTX_LIST_BUILDER_CANCEL.ordinal()] = graalHPyBuilderCancel;

        members[HPyContextMember.CTX_TRACKER_NEW.ordinal()] = new GraalHPyTrackerNew();
        members[HPyContextMember.CTX_TRACKER_ADD.ordinal()] = new GraalHPyTrackerAdd();
        members[HPyContextMember.CTX_TRACKER_FORGET_ALL.ordinal()] = new GraalHPyTrackerForgetAll();
        members[HPyContextMember.CTX_TRACKER_CLOSE.ordinal()] = new GraalHPyTrackerCleanup();

        members[HPyContextMember.CTX_DUMP.ordinal()] = new GraalHPyDump();

        if (TRACE) {
            for (int i = 0; i < members.length; i++) {
                Object m = members[i];
                if (m != null && !(m instanceof Number || m instanceof GraalHPyHandle)) {
                    // wrap
                    members[i] = new HPyExecuteWrapper(i, m);
                }
            }
        }

        return members;
    }

    static final int[] counts = new int[HPyContextMember.VALUES.length];

    static {
        if (TRACE) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.out.println("========= stats");
                        for (int i = 0; i < counts.length; i++) {
                            if (counts[i] != 0) {
                                System.out.printf("  %40s[%3d]: %d\n", HPyContextMember.VALUES[i].name, i, counts[i]);
                            }
                        }
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    public static final class HPyExecuteWrapper implements TruffleObject {

        final Object delegate;
        final int index;

        public HPyExecuteWrapper(int index, Object delegate) {
            this.index = index;
            this.delegate = delegate;
        }

        @ExportMessage
        boolean isExecutable(
                        @CachedLibrary("this.delegate") InteropLibrary lib) {
            return lib.isExecutable(delegate);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @CachedLibrary("this.delegate") InteropLibrary lib) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            counts[index]++;
            return lib.execute(delegate, arguments);
        }
    }

    private static void createIntConstant(Object[] members, HPyContextMember member, int value) {
        members[member.ordinal()] = value;
    }

    private static void createConstant(Object[] members, HPyContextMember member, Object value) {
        members[member.ordinal()] = new GraalHPyHandle(value);
    }

    private static void createTypeConstant(Object[] members, HPyContextMember member, Python3Core core, PythonBuiltinClassType value) {
        members[member.ordinal()] = new GraalHPyHandle(core.lookupType(value));
    }

    public GraalHPyHandle createHandle(Object delegate) {
        return new GraalHPyHandle(delegate);
    }

    private static Unsafe unsafe = CArrayWrappers.UNSAFE;

    private long nativeSpacePointers;

    @TruffleBoundary(allowInlining = true)
    private int allocateHandle() {
        int freeItem = freeStack.pop();
        if (freeItem != -1) {
            assert 0 <= freeItem && freeItem < hpyHandleTable.length;
            assert hpyHandleTable[freeItem] == null;
            return freeItem;
        }
        for (int i = 1; i < hpyHandleTable.length; i++) {
            if (hpyHandleTable[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public final synchronized int getHPyHandleForObject(GraalHPyHandle object) {
        // find free association
        int handle = allocateHandle();
        if (handle == -1) {
            // resize
            int newSize = Math.max(16, hpyHandleTable.length * 2);
            LOGGER.fine(() -> "resizing HPy handle table to " + newSize);
            hpyHandleTable = Arrays.copyOf(hpyHandleTable, newSize);
            if (useNativeFastPaths && isPointer()) {
                reallocateNativeSpacePointersMirror();
            }
            handle = allocateHandle();
        }
        assert handle > 0;
        hpyHandleTable[handle] = object;
        if (useNativeFastPaths && isPointer()) {
            mirrorNativeSpacePointerToNative(object, handle);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            final int handleID = handle;
            LOGGER.finer(() -> String.format("allocating HPy handle %d (object: %s)", handleID, object));
        }
        return handle;
    }

    @TruffleBoundary
    private void mirrorNativeSpacePointerToNative(GraalHPyHandle handleObject, int handleID) {
        assert isPointer();
        assert useNativeFastPaths;
        Object nativeSpace = PNone.NO_VALUE;
        Object delegate = handleObject.getDelegate();
        if (delegate instanceof PythonHPyObject) {
            nativeSpace = ((PythonHPyObject) delegate).getHPyNativeSpace();
        }
        try {
            long l = nativeSpace instanceof Long ? ((long) nativeSpace) : nativeSpace == PNone.NO_VALUE ? 0 : InteropLibrary.getUncached().asPointer(nativeSpace);
            unsafe.putLong(nativeSpacePointers + handleID * SIZEOF_LONG, l);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @TruffleBoundary
    private void reallocateNativeSpacePointersMirror() {
        assert isPointer();
        assert useNativeFastPaths;
        nativeSpacePointers = unsafe.reallocateMemory(nativeSpacePointers, hpyHandleTable.length * SIZEOF_LONG);
        try {
            InteropLibrary.getUncached().execute(setNativeSpaceFunction, nativePointer, nativeSpacePointers);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Allocates a native array (element size is {@link #SIZEOF_LONG} for as many elements as in
     * {@link #hpyHandleTable} and writes the native space pointers of all objects in the handle
     * table into this array. The pointer of the array is then set to
     * {@code ((HPyContext) ctx)->_private} and meant to be used by the {@code ctx_Cast}'s upcall
     * stub to avoid an expensive upcall.
     */
    @TruffleBoundary
    private void allocateNativeSpacePointersMirror() {
        long arraySize = hpyHandleTable.length * SIZEOF_LONG;
        long arrayPtr = unsafe.allocateMemory(arraySize);
        unsafe.setMemory(arrayPtr, arraySize, (byte) 0);

        // publish pointer value (needed for initialization)
        nativeSpacePointers = arrayPtr;

        // write existing values to mirror; start at 1 to omit the NULL handle
        for (int i = 1; i < hpyHandleTable.length; i++) {
            GraalHPyHandle handleObject = hpyHandleTable[i];
            if (handleObject != null) {
                mirrorNativeSpacePointerToNative(handleObject, i);
            }
        }

        // commit pointer value for native usage
        try {
            InteropLibrary.getUncached().execute(setNativeSpaceFunction, nativePointer, arrayPtr);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public synchronized GraalHPyHandle getObjectForHPyHandle(int handle) {
        assert !GraalHPyBoxing.isBoxedInt(handle) && !GraalHPyBoxing.isBoxedDouble(handle) : "trying to lookup boxed primitive";
        return hpyHandleTable[handle];
    }

    synchronized boolean releaseHPyHandleForObject(int handle) {
        assert handle != 0 : "NULL handle cannot be released";
        assert hpyHandleTable[handle] != null : PythonUtils.format("releasing handle that has already been released: %d", handle);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(() -> "releasing HPy handle " + handle);
        }
        hpyHandleTable[handle] = null;
        freeStack.push(handle);
        return true;
    }

    void onInvalidHandle(@SuppressWarnings("unused") int id) {
        // nothing to do in the universal context
    }

    private static final class HandleStack {
        private int[] handles;
        private int top = 0;

        public HandleStack(int initialCapacity) {
            handles = new int[initialCapacity];
        }

        void push(int i) {
            if (top >= handles.length) {
                handles = Arrays.copyOf(handles, handles.length * 2);
            }
            handles[top++] = i;
        }

        int pop() {
            if (top <= 0) {
                return -1;
            }
            return handles[--top];
        }
    }

    /**
     * A weak reference to an object that has an associated HPy native space (
     * {@link PythonHPyObject}).
     */
    static final class GraalHPyHandleReference extends WeakReference<Object> {

        private final Object nativeSpace;
        private final Object destroyFunc;

        boolean cleaned;
        private GraalHPyHandleReference next;

        public GraalHPyHandleReference(Object referent, ReferenceQueue<Object> q, Object nativeSpace, Object destroyFunc) {
            super(referent, q);
            this.nativeSpace = nativeSpace;
            this.destroyFunc = destroyFunc;
        }

        public Object getNativeSpace() {
            return nativeSpace;
        }

        public Object getDestroyFunc() {
            return destroyFunc;
        }

        public GraalHPyHandleReference getNext() {
            return next;
        }

        public void setNext(GraalHPyHandleReference next) {
            this.next = next;
        }
    }

    /**
     * Registers an HPy native space of a Python object.<br/>
     * Use this method to register a native memory that is associated with a Python object in order
     * to ensure that the native memory will be free'd when the owning Python object dies.<br/>
     * This works by creating a weak reference to the Python object, using a thread that
     * concurrently polls the reference queue. If threading is allowed, cleaning will be done fully
     * concurrent on a cleaner thread. If not, an async action will be scheduled to free the native
     * memory. Hence, the destroy function could also be executed on the cleaner thread.
     *
     * @param pythonObject The Python object that has associated native memory.
     * @param dataPtr The pointer object of the native memory.
     * @param destroyFunc The destroy function to call when the Python object is unreachable (may be
     *            {@code null}; in this case, bare {@code free} will be used).
     */
    @TruffleBoundary
    final void createHandleReference(PythonObject pythonObject, Object dataPtr, Object destroyFunc) {
        GraalHPyHandleReference newHead = new GraalHPyHandleReference(pythonObject, ensureReferenceQueue(), dataPtr, destroyFunc);
        references.getAndAccumulate(newHead, (prev, x) -> {
            x.next = prev;
            return x;
        });
    }

    private ReferenceQueue<Object> ensureReferenceQueue() {
        if (nativeSpaceReferenceQueue == null) {
            ReferenceQueue<Object> referenceQueue = createReferenceQueue();
            nativeSpaceReferenceQueue = referenceQueue;
            return referenceQueue;
        }
        return nativeSpaceReferenceQueue;
    }

    @TruffleBoundary
    private ReferenceQueue<Object> createReferenceQueue() {
        final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

        // lazily register the runnable that concurrently collects the queued references
        Env env = getContext().getEnv();
        if (env.isCreateThreadAllowed()) {
            Thread thread = env.createThread(new GraalHPyReferenceCleanerRunnable(referenceQueue), null);
            // Make the cleaner thread a daemon; it should not prevent JVM shutdown.
            thread.setDaemon(true);
            thread.start();
            hpyReferenceCleanerThread = thread;
        } else {
            getContext().registerAsyncAction(() -> {
                Reference<?> reference = null;
                try {
                    reference = referenceQueue.remove();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                ArrayList<GraalHPyHandleReference> refs = new ArrayList<>();
                do {
                    if (reference instanceof GraalHPyHandleReference) {
                        refs.add((GraalHPyHandleReference) reference);
                    }
                    // consume all
                    reference = referenceQueue.poll();
                } while (reference != null);

                if (!refs.isEmpty()) {
                    return new GraalHPyHandleReferenceCleanerAction(refs.toArray(new GraalHPyHandleReference[0]));
                }

                return null;
            });
        }
        return referenceQueue;
    }

    @TruffleBoundary
    @Override
    protected final Store initializeSymbolCache() {
        PythonLanguage language = getContext().getLanguage();
        Shape symbolCacheShape = language.getHPySymbolCacheShape();
        // We will always get an empty shape from the language and we do always add same key-value
        // pairs (in the same order). So, in the end, each context should get the same shape.
        Store s = new Store(symbolCacheShape);
        for (GraalHPyNativeSymbol sym : GraalHPyNativeSymbol.getValues()) {
            DynamicObjectLibrary.getUncached().put(s, sym, PNone.NO_VALUE);
        }
        return s;
    }

    /**
     * Join the reference cleaner thread.
     */
    public void finalizeContext() {
        Thread thread = this.hpyReferenceCleanerThread;
        if (thread != null) {
            if (thread.isAlive() && !thread.isInterrupted()) {
                thread.interrupt();
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}

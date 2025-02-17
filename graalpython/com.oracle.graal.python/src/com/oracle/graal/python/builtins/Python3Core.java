/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndentationError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TabError;
import static com.oracle.graal.python.nodes.BuiltinNames.PRINT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.ArrayModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AtexitModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BinasciiModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.CmathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CollectionsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ContextvarsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CryptModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ErrnoModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FaulthandlerModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FcntlModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FunctoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GcModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyDebugModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyUniversalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.JArrayModuleBuiltins;
import com.oracle.graal.python.builtins.modules.JavaModuleBuiltins;
import com.oracle.graal.python.builtins.modules.LocaleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.LsprofModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MMapModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MultiprocessingModuleBuiltins;
import com.oracle.graal.python.builtins.modules.OperatorModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PolyglotModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixShMemModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixSubprocessModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PwdModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PyExpatModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins;
import com.oracle.graal.python.builtins.modules.QueueModuleBuiltins;
import com.oracle.graal.python.builtins.modules.RandomModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ReadlineModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ResourceModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SREModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SelectModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SignalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SocketModuleBuiltins;
import com.oracle.graal.python.builtins.modules.StringModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysConfigModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TraceModuleBuiltins;
import com.oracle.graal.python.builtins.modules.UnicodeDataModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ZipImportModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ast.AstBuiltins;
import com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2CompressorBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2DecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2ModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeSequenceBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCSimpleTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCStructTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.SimpleCDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructUnionTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructureBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.UnionTypeBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedIOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedIOMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedRWPairBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedRandomBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BytesIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.FileIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOBaseDictBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins;
import com.oracle.graal.python.builtins.modules.io.IncrementalNewlineDecoderBuiltins;
import com.oracle.graal.python.builtins.modules.io.RawIOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.StringIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONEncoderBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONModuleBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMACompressorBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMADecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZlibCompressBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZlibDecompressBuiltins;
import com.oracle.graal.python.builtins.objects.NotImplementedBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bool.BoolBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDebugHandleBuiltins;
import com.oracle.graal.python.builtins.objects.code.CodeBuiltins;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeIterBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DefaultDictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictReprBuiltin;
import com.oracle.graal.python.builtins.objects.dict.DictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictViewBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.EllipsisBuiltins;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltins;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.foreign.ForeignObjectBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptorTypeBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.MemberDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.ForeignIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.PZipBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.SentinelIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.map.MapBuiltins;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.BufferBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins;
import com.oracle.graal.python.builtins.objects.method.AbstractMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.ClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.DecoratedMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.method.StaticmethodBuiltins;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltins;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.SimpleNamespaceBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.posix.DirEntryBuiltins;
import com.oracle.graal.python.builtins.objects.posix.ScandirIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.property.PropertyBuiltins;
import com.oracle.graal.python.builtins.objects.queue.SimpleQueueBuiltins;
import com.oracle.graal.python.builtins.objects.random.RandomBuiltins;
import com.oracle.graal.python.builtins.objects.range.RangeBuiltins;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.reversed.ReversedBuiltins;
import com.oracle.graal.python.builtins.objects.set.BaseSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltins;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.MemoryBIOBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLContextBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLSocketBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltins;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.RLockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.SemLockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleGetterBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.zipimporter.ZipImporterBuiltins;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonCodeSerializer;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.interop.PythonMapScope;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * The core is intended to the immutable part of the interpreter, including most modules and most
 * types.
 */
public final class Python3Core implements ParserErrorCallback {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(Python3Core.class);
    private final String[] coreFiles;

    public static final Pattern MISSING_PARENTHESES_PATTERN = Pattern.compile("^(print|exec) +([^(][^;]*).*");

    private static String[] initializeCoreFiles() {
        // Order matters!
        List<String> coreFiles = new ArrayList<>(Arrays.asList(
                        "object",
                        "sys",
                        "type",
                        "_imp",
                        "function",
                        "_functools",
                        "method",
                        "_frozen_importlib",
                        "__graalpython__",
                        "_weakref",
                        "itertools",
                        "faulthandler",
                        "base_exception",
                        PythonCextBuiltins.PYTHON_CEXT,
                        "bytes",
                        "bytearray",
                        "unicodedata",
                        "_locale",
                        "_sre",
                        "function",
                        "_sysconfig",
                        "termios",
                        "zipimport",
                        "mmap",
                        "java",
                        // TODO: see the encodings initialization before sys_post_init.py is
                        // loaded in initializePython3Core;
                        // once sys_post_init.py is gone, it should not be necessary
                        "sys_post_init",
                        "_contextvars",
                        "pip_hook",
                        "_struct",
                        "_posixshmem"));
        // add service loader defined python file extensions
        if (!ImageInfo.inImageRuntimeCode()) {
            ServiceLoader<PythonBuiltins> providers = ServiceLoader.load(PythonBuiltins.class, Python3Core.class.getClassLoader());
            for (PythonBuiltins builtin : providers) {
                CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
                if (!annotation.pythonFile().isEmpty()) {
                    coreFiles.add(annotation.pythonFile());
                }
            }
        }
        // must be last
        coreFiles.add("final_patches");
        return coreFiles.toArray(new String[coreFiles.size()]);
    }

    private final PythonBuiltins[] builtins;

    private static final boolean hasCoverageTool;
    private static final boolean hasProfilerTool;
    static {
        Class<?> c = null;
        try {
            c = Class.forName("com.oracle.truffle.tools.coverage.CoverageTracker");
        } catch (LinkageError | ClassNotFoundException e) {
        }
        hasCoverageTool = c != null;
        c = null;
        try {
            c = Class.forName("com.oracle.truffle.tools.profiler.CPUSampler");
        } catch (LinkageError | ClassNotFoundException e) {
        }
        hasProfilerTool = c != null;
        c = null;
    }

    private static PythonBuiltins[] initializeBuiltins(boolean nativeAccessAllowed) {
        List<PythonBuiltins> builtins = new ArrayList<>(Arrays.asList(
                        new BuiltinConstructors(),
                        new BuiltinFunctions(),
                        new DecoratedMethodBuiltins(),
                        new ClassmethodBuiltins(),
                        new StaticmethodBuiltins(),
                        new SimpleNamespaceBuiltins(),
                        new PolyglotModuleBuiltins(),
                        new ObjectBuiltins(),
                        new CellBuiltins(),
                        new BoolBuiltins(),
                        new FloatBuiltins(),
                        new BytesBuiltins(),
                        new ByteArrayBuiltins(),
                        new ComplexBuiltins(),
                        new TypeBuiltins(),
                        new IntBuiltins(),
                        new ForeignObjectBuiltins(),
                        new ListBuiltins(),
                        new DictBuiltins(),
                        new DictReprBuiltin(),
                        new DictViewBuiltins(),
                        new DictValuesBuiltins(),
                        new RangeBuiltins(),
                        new SliceBuiltins(),
                        new TupleBuiltins(),
                        new StringBuiltins(),
                        new BaseSetBuiltins(),
                        new SetBuiltins(),
                        new FrozenSetBuiltins(),
                        new IteratorBuiltins(),
                        new ReversedBuiltins(),
                        new PZipBuiltins(),
                        new EnumerateBuiltins(),
                        new MapBuiltins(),
                        new NotImplementedBuiltins(),
                        new EllipsisBuiltins(),
                        new SentinelIteratorBuiltins(),
                        new ForeignIteratorBuiltins(),
                        new GeneratorBuiltins(),
                        new AbstractFunctionBuiltins(),
                        new FunctionBuiltins(),
                        new BuiltinFunctionBuiltins(),
                        new AbstractMethodBuiltins(),
                        new MethodBuiltins(),
                        new BuiltinMethodBuiltins(),
                        new BuiltinClassmethodBuiltins(),
                        new CodeBuiltins(),
                        new FrameBuiltins(),
                        new MappingproxyBuiltins(),
                        new DescriptorBuiltins(),
                        new GetSetDescriptorTypeBuiltins(),
                        new MemberDescriptorBuiltins(),
                        new PropertyBuiltins(),
                        new BaseExceptionBuiltins(),
                        new PosixModuleBuiltins(),
                        new CryptModuleBuiltins(),
                        new ScandirIteratorBuiltins(),
                        new DirEntryBuiltins(),
                        new ImpModuleBuiltins(),
                        new ArrayModuleBuiltins(),
                        new ArrayBuiltins(),
                        new TimeModuleBuiltins(),
                        new ModuleBuiltins(),
                        new MathModuleBuiltins(),
                        new CmathModuleBuiltins(),
                        new MarshalModuleBuiltins(),
                        new RandomModuleBuiltins(),
                        new RandomBuiltins(),
                        new PythonCextBuiltins(),
                        new WeakRefModuleBuiltins(),
                        new ReferenceTypeBuiltins(),
                        new WarningsModuleBuiltins(),

                        // io
                        new IOModuleBuiltins(),
                        new IOBaseBuiltins(),
                        new BufferedIOBaseBuiltins(),
                        new RawIOBaseBuiltins(),
                        new TextIOBaseBuiltins(),
                        new BufferedReaderBuiltins(),
                        new BufferedWriterBuiltins(),
                        new BufferedRandomBuiltins(),
                        new BufferedReaderMixinBuiltins(),
                        new BufferedWriterMixinBuiltins(),
                        new BufferedIOMixinBuiltins(),
                        new FileIOBuiltins(),
                        new TextIOWrapperBuiltins(),
                        new IncrementalNewlineDecoderBuiltins(),
                        new BufferedRWPairBuiltins(),
                        new BytesIOBuiltins(),
                        new StringIOBuiltins(),
                        new IOBaseDictBuiltins(),

                        new StringModuleBuiltins(),
                        new ItertoolsModuleBuiltins(),
                        new FunctoolsModuleBuiltins(),
                        new ErrnoModuleBuiltins(),
                        new CodecsModuleBuiltins(),
                        new CodecsTruffleModuleBuiltins(),
                        new DequeBuiltins(),
                        new DequeIterBuiltins(),
                        new CollectionsModuleBuiltins(),
                        new DefaultDictBuiltins(),
                        new TupleGetterBuiltins(),
                        new JavaModuleBuiltins(),
                        new JArrayModuleBuiltins(),
                        new JSONModuleBuiltins(),
                        new SREModuleBuiltins(),
                        new AstModuleBuiltins(),
                        new SelectModuleBuiltins(),
                        new SocketModuleBuiltins(),
                        new SocketBuiltins(),
                        new SignalModuleBuiltins(),
                        new TracebackBuiltins(),
                        new GcModuleBuiltins(),
                        new AtexitModuleBuiltins(),
                        new FaulthandlerModuleBuiltins(),
                        new UnicodeDataModuleBuiltins(),
                        new LocaleModuleBuiltins(),
                        new SysModuleBuiltins(),
                        new BufferBuiltins(),
                        new MemoryViewBuiltins(),
                        new SuperBuiltins(),
                        new SSLModuleBuiltins(),
                        new SSLContextBuiltins(),
                        new SSLErrorBuiltins(),
                        new SSLSocketBuiltins(),
                        new MemoryBIOBuiltins(),
                        new BinasciiModuleBuiltins(),
                        new PosixShMemModuleBuiltins(),
                        new PosixSubprocessModuleBuiltins(),
                        new ReadlineModuleBuiltins(),
                        new PyExpatModuleBuiltins(),
                        new SysConfigModuleBuiltins(),
                        new OperatorModuleBuiltins(),
                        new ZipImporterBuiltins(),
                        new ZipImportModuleBuiltins(),

                        // zlib
                        new ZLibModuleBuiltins(),
                        new ZlibCompressBuiltins(),
                        new ZlibDecompressBuiltins(),

                        new MMapModuleBuiltins(),
                        new FcntlModuleBuiltins(),
                        new MMapBuiltins(),
                        new SimpleQueueBuiltins(),
                        new QueueModuleBuiltins(),
                        new ThreadModuleBuiltins(),
                        new ThreadBuiltins(),
                        new ThreadLocalBuiltins(),
                        new LockBuiltins(),
                        new RLockBuiltins(),
                        new PwdModuleBuiltins(),
                        new ResourceModuleBuiltins(),
                        new ContextvarsModuleBuiltins(),

                        // lzma
                        new LZMAModuleBuiltins(),
                        new LZMACompressorBuiltins(),
                        new LZMADecompressorBuiltins(),

                        new MultiprocessingModuleBuiltins(),
                        new SemLockBuiltins(),
                        new WarningsModuleBuiltins(),
                        new GraalPythonModuleBuiltins(),

                        // json
                        new JSONScannerBuiltins(),
                        new JSONEncoderBuiltins(),

                        // _ast
                        new AstBuiltins(),

                        // ctypes
                        new CArgObjectBuiltins(),
                        new CDataTypeBuiltins(),
                        new CDataTypeSequenceBuiltins(),
                        new CFieldBuiltins(),
                        new CtypesModuleBuiltins(),
                        new PyCArrayTypeBuiltins(),
                        new PyCFuncPtrBuiltins(),
                        new PyCFuncPtrTypeBuiltins(),
                        new PyCPointerTypeBuiltins(),
                        new PyCSimpleTypeBuiltins(),
                        new PyCStructTypeBuiltins(),
                        new StgDictBuiltins(),
                        new StructUnionTypeBuiltins(),
                        new StructureBuiltins(),
                        new UnionTypeBuiltins(),
                        new SimpleCDataBuiltins(),
                        new PyCArrayBuiltins(),
                        new PyCPointerBuiltins(),
                        new CDataBuiltins(),

                        // _hpy_universal
                        new GraalHPyUniversalModuleBuiltins(),

                        // _hpy_debug
                        new GraalHPyDebugModuleBuiltins(),
                        new GraalHPyDebugHandleBuiltins()));
        if (hasCoverageTool) {
            builtins.add(new TraceModuleBuiltins());
        }
        if (hasProfilerTool) {
            builtins.add(new LsprofModuleBuiltins());
            builtins.add(LsprofModuleBuiltins.newProfilerBuiltins());
        }
        if (nativeAccessAllowed) {
            builtins.add(new BZ2CompressorBuiltins());
            builtins.add(new BZ2DecompressorBuiltins());
            builtins.add(new BZ2ModuleBuiltins());
        }
        if (!ImageInfo.inImageRuntimeCode()) {
            ServiceLoader<PythonBuiltins> providers = ServiceLoader.load(PythonBuiltins.class, Python3Core.class.getClassLoader());
            for (PythonBuiltins builtin : providers) {
                builtins.add(builtin);
            }
        }
        return builtins.toArray(new PythonBuiltins[builtins.size()]);
    }

    // not using EnumMap, HashMap, etc. to allow this to fold away during partial evaluation
    @CompilationFinal(dimensions = 1) private final PythonBuiltinClass[] builtinTypes = new PythonBuiltinClass[PythonBuiltinClassType.VALUES.length];

    private final Map<String, PythonModule> builtinModules = new HashMap<>();
    @CompilationFinal private PythonModule builtinsModule;
    @CompilationFinal private PythonModule sysModule;
    @CompilationFinal private PDict sysModules;
    @CompilationFinal private PMethod importFunc;
    @CompilationFinal private PythonModule importlib;

    @CompilationFinal private PInt pyTrue;
    @CompilationFinal private PInt pyFalse;
    @CompilationFinal private PFloat pyNaN;

    private final PythonParser parser;

    @CompilationFinal private PythonContext singletonContext;
    @CompilationFinal private Object globalScopeObject;

    /*
     * This field cannot be made CompilationFinal since code might get compiled during context
     * initialization.
     */
    private volatile boolean initialized;

    private final PythonObjectFactory objectFactory = PythonObjectFactory.getUncached();

    public Python3Core(PythonParser parser, boolean isNativeSupportAllowed) {
        this.parser = parser;
        this.builtins = initializeBuiltins(isNativeSupportAllowed);
        this.coreFiles = initializeCoreFiles();
    }

    public PythonLanguage getLanguage() {
        return singletonContext.getLanguage();
    }

    @Override
    public PythonContext getContext() {
        return singletonContext;
    }

    public PythonParser getParser() {
        return parser;
    }

    public PythonCodeSerializer getSerializer() {
        return (PythonCodeSerializer) parser;
    }

    /**
     * Checks whether the core is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Load the core library and prepare all builtin classes and modules.
     */
    public void initialize(PythonContext context) {
        singletonContext = context;
        initializeJavaCore();
        initializePython3Core(context.getCoreHomeOrFail());
        assert SpecialMethodSlot.checkSlotOverrides(this);
        initialized = true;
    }

    private void initializeJavaCore() {
        initializeTypes();
        populateBuiltins();
        SpecialMethodSlot.initializeBuiltinsSpecialMethodSlots(this);
        publishBuiltinModules();
        builtinsModule = builtinModules.get(BuiltinNames.BUILTINS);
    }

    private void initializePython3Core(String coreHome) {
        loadFile(BuiltinNames.BUILTINS, coreHome);
        for (String s : coreFiles) {
            // TODO: once sys_post_init.py is gone, this should not be necessary
            if (s.equals("sys_post_init")) {
                importEncoding();
            }
            loadFile(s, coreHome);
        }
        initialized = true;
    }

    private void importEncoding() {
        PythonModule sys = lookupBuiltinModule("sys");
        Object sysPath = sys.getAttribute("path");
        PyObjectCallMethodObjArgs.getUncached().execute(null, sysPath, "insert", 0, getContext().getStdlibHome());
        try {
            AbstractImportNode.importModule("encodings");
        } finally {
            PyObjectCallMethodObjArgs.getUncached().execute(null, sysPath, "pop");
        }
    }

    /**
     * Run post-initialization code that needs a fully working Python environment. This will be run
     * eagerly when the context is initialized on the JVM or a new context is created on SVM, but is
     * omitted when the native image is generated.
     */
    public void postInitialize() {
        if (!ImageInfo.inImageBuildtimeCode() || ImageInfo.inImageRuntimeCode()) {
            initialized = false;

            for (PythonBuiltins builtin : builtins) {
                CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
                if (annotation.isEager()) {
                    builtin.postInitialize(this);
                }
            }

            globalScopeObject = PythonMapScope.createTopScope(getContext());
            getContext().getSharedFinalizer().registerAsyncAction();
            initialized = true;
        }
    }

    @TruffleBoundary
    public PythonModule lookupBuiltinModule(String name) {
        return builtinModules.get(name);
    }

    public PythonBuiltinClass lookupType(PythonBuiltinClassType type) {
        assert builtinTypes[type.ordinal()] != null;
        return builtinTypes[type.ordinal()];
    }

    @TruffleBoundary
    public String[] builtinModuleNames() {
        return builtinModules.keySet().toArray(PythonUtils.EMPTY_STRING_ARRAY);
    }

    public PythonModule getBuiltins() {
        return builtinsModule;
    }

    public PythonModule getSysModule() {
        return sysModule;
    }

    public PDict getSysModules() {
        return sysModules;
    }

    public PythonModule getImportlib() {
        return importlib;
    }

    public void registerImportlib(PythonModule mod) {
        if (importlib != null) {
            throw new IllegalStateException("importlib cannot be registered more than once");
        }
        importlib = mod;
    }

    public PMethod getImportFunc() {
        return importFunc;
    }

    public void registerImportFunc(PMethod func) {
        if (importFunc != null) {
            throw new IllegalStateException("__import__ func cannot be registered more than once");
        }
        importFunc = func;
    }

    @Override
    @TruffleBoundary
    public void warn(PythonBuiltinClassType type, String format, Object... args) {
        WarningsModuleBuiltins.WarnNode.getUncached().warnFormat(null, null, type, 1, format, args);
    }

    /**
     * Returns the stderr object or signals error when stderr is "lost".
     */
    public Object getStderr() {
        try {
            return PyObjectLookupAttr.getUncached().execute(null, sysModule, "stderr");
        } catch (PException e) {
            try {
                getContext().getEnv().err().write("lost sys.stderr\n".getBytes());
            } catch (IOException ioe) {
                // nothing more we can do
            }
            throw e;
        }
    }

    private void publishBuiltinModules() {
        assert sysModules != null;
        for (Entry<String, PythonModule> entry : builtinModules.entrySet()) {
            final PythonModule pythonModule = entry.getValue();
            final PythonBuiltins moduleBuiltins = pythonModule.getBuiltins();
            if (moduleBuiltins != null) {
                CoreFunctions annotation = moduleBuiltins.getClass().getAnnotation(CoreFunctions.class);
                if (annotation.isEager()) {
                    sysModules.setItem(entry.getKey(), pythonModule);
                }
            }
        }
    }

    private PythonBuiltinClass initializeBuiltinClass(PythonBuiltinClassType type) {
        int index = type.ordinal();
        if (builtinTypes[index] == null) {
            if (type.getBase() == null) {
                // object case
                builtinTypes[index] = new PythonBuiltinClass(getLanguage(), type, null);
            } else {
                builtinTypes[index] = new PythonBuiltinClass(getLanguage(), type, initializeBuiltinClass(type.getBase()));
            }
        }
        return builtinTypes[index];
    }

    private void initializeTypes() {
        // create class objects for builtin types
        for (PythonBuiltinClassType builtinClass : PythonBuiltinClassType.VALUES) {
            initializeBuiltinClass(builtinClass);
        }
        // n.b.: the builtin modules and classes and their constructors are initialized first here,
        // so we have the mapping from java classes to python classes and builtin names to modules
        // available.
        for (PythonBuiltins builtin : builtins) {
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                createModule(annotation.defineModule(), builtin);
            }
        }
        // publish builtin types in the corresponding modules
        for (PythonBuiltinClassType builtinClass : PythonBuiltinClassType.VALUES) {
            String module = builtinClass.getPublishInModule();
            if (module != null) {
                PythonModule pythonModule = lookupBuiltinModule(module);
                if (pythonModule != null) {
                    pythonModule.setAttribute(builtinClass.getName(), lookupType(builtinClass));
                }
            }
        }
        // now initialize well-known objects
        pyTrue = PythonObjectFactory.getUncached().createInt(PythonBuiltinClassType.Boolean, BigInteger.ONE);
        pyFalse = PythonObjectFactory.getUncached().createInt(PythonBuiltinClassType.Boolean, BigInteger.ZERO);
        pyNaN = PythonObjectFactory.getUncached().createFloat(Double.NaN);
    }

    private void populateBuiltins() {
        for (PythonBuiltins builtin : builtins) {
            builtin.initialize(this);
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                addBuiltinsTo(builtinModules.get(annotation.defineModule()), builtin);
            }
            for (PythonBuiltinClassType klass : annotation.extendClasses()) {
                addBuiltinsTo(lookupType(klass), builtin);
            }
        }

        // core machinery
        sysModule = builtinModules.get("sys");
        sysModules = (PDict) sysModule.getAttribute("modules");

        PythonModule bootstrapExternal = createModule("importlib._bootstrap_external");
        bootstrapExternal.setAttribute(__PACKAGE__, "importlib");
        addBuiltinModule("_frozen_importlib_external", bootstrapExternal);
        PythonModule bootstrap = createModule("importlib._bootstrap");
        bootstrap.setAttribute(__PACKAGE__, "importlib");
        addBuiltinModule("_frozen_importlib", bootstrap);
    }

    private PythonModule createModule(String name) {
        return createModule(name, null);
    }

    private void addBuiltinModule(String name, PythonModule module) {
        builtinModules.put(name, module);
        if (sysModules != null) {
            sysModules.setItem(name, module);
        }
    }

    private PythonModule createModule(String name, PythonBuiltins moduleBuiltins) {
        PythonModule mod = builtinModules.get(name);
        if (mod == null) {
            mod = factory().createPythonModule(name);
            if (moduleBuiltins != null) {
                mod.setBuiltins(moduleBuiltins);
            }
            addBuiltinModule(name, mod);
        }
        return mod;
    }

    private void addBuiltinsTo(PythonObject obj, PythonBuiltins builtinsForObj) {
        Map<Object, Object> builtinConstants = builtinsForObj.getBuiltinConstants();
        for (Map.Entry<Object, Object> entry : builtinConstants.entrySet()) {
            Object constant = entry.getKey();
            obj.setAttribute(constant, entry.getValue());
        }

        Map<String, BoundBuiltinCallable<?>> builtinFunctions = builtinsForObj.getBuiltinFunctions();
        for (Entry<String, BoundBuiltinCallable<?>> entry : builtinFunctions.entrySet()) {
            String methodName = entry.getKey();
            Object value;
            assert obj instanceof PythonModule || obj instanceof PythonBuiltinClass : "unexpected object while adding builtins";
            if (obj instanceof PythonModule) {
                value = objectFactory.createBuiltinMethod(obj, (PBuiltinFunction) entry.getValue());
            } else {
                value = entry.getValue().boundToObject(((PythonBuiltinClass) obj).getType(), factory());
            }
            obj.setAttribute(methodName, value);
        }

        Map<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> builtinClasses = builtinsForObj.getBuiltinClasses();
        for (Entry<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> entry : builtinClasses.entrySet()) {
            boolean isPublic = entry.getValue().getValue();
            if (isPublic) {
                PythonBuiltinClass pythonClass = entry.getKey();
                obj.setAttribute(GetNameNode.doSlowPath(pythonClass), pythonClass);
            }
        }
    }

    @TruffleBoundary
    private Source getInternalSource(String basename, String prefix) {
        PythonContext ctxt = getContext();
        Env env = ctxt.getEnv();
        String suffix = env.getFileNameSeparator() + basename + PythonLanguage.EXTENSION;
        TruffleFile file = env.getInternalTruffleFile(prefix + suffix);
        String errorMessage;
        try {
            return PythonLanguage.newSource(ctxt, file, basename);
        } catch (IOException e) {
            errorMessage = "Startup failed, could not read core library from " + file + ". Maybe you need to set python.CoreHome and python.StdLibHome.";
        } catch (SecurityException e) {
            errorMessage = "Startup failed, a security exception occurred while reading from " + file + ". Maybe you need to set python.CoreHome and python.StdLibHome.";
        }
        LOGGER.log(Level.SEVERE, errorMessage);
        RuntimeException e = new RuntimeException(errorMessage);
        throw e;
    }

    private void loadFile(String s, String prefix) {
        Supplier<CallTarget> getCode = () -> {
            Source source = getInternalSource(s, prefix);
            return PythonUtils.getOrCreateCallTarget((RootNode) getParser().parse(ParserMode.File, 0, this, source, null, null));
        };
        RootCallTarget callTarget = (RootCallTarget) getLanguage().cacheCode(s, getCode);
        PythonModule mod = lookupBuiltinModule(s);
        if (mod == null) {
            // use an anonymous module for the side-effects
            mod = factory().createPythonModule("__anonymous__");
        }
        GenericInvokeNode.getUncached().execute(callTarget, PArguments.withGlobals(mod));
    }

    public PythonObjectFactory factory() {
        return objectFactory;
    }

    public void setContext(PythonContext context) {
        assert singletonContext == null;
        singletonContext = context;
    }

    public PInt getTrue() {
        return pyTrue;
    }

    public PInt getFalse() {
        return pyFalse;
    }

    public PFloat getNaN() {
        return pyNaN;
    }

    @Override
    public RuntimeException raiseInvalidSyntax(PythonParser.ErrorType type, Source source, SourceSection section, String message, Object... arguments) {
        CompilerDirectives.transferToInterpreter();
        Node location = new Node() {
            @Override
            public SourceSection getSourceSection() {
                return section;
            }
        };
        throw raiseInvalidSyntax(type, location, message, arguments);
    }

    @Override
    @TruffleBoundary
    public RuntimeException raiseInvalidSyntax(PythonParser.ErrorType type, Node location, String message, Object... arguments) {
        PBaseException instance;
        Object cls;
        switch (type) {
            case Indentation:
                cls = IndentationError;
                break;
            case Tab:
                cls = TabError;
                break;
            default:
                cls = SyntaxError;
                break;
        }
        instance = factory().createBaseException(cls, message, arguments);
        SourceSection section = location.getSourceSection();
        Source source = section.getSource();
        String path = source.getPath();
        instance.setAttribute("filename", path != null ? path : source.getName() != null ? source.getName() : "<string>");
        // Not very nice. This counts on the implementation in traceback.py where if the value of
        // text attribute
        // is NONE, then the line is not printed
        instance.setAttribute("text", section.isAvailable() ? source.getCharacters(section.getStartLine()) : PNone.NONE);
        instance.setAttribute("lineno", section.getStartLine());
        instance.setAttribute("offset", section.getStartColumn());
        String msg = "invalid syntax";
        if (type == PythonParser.ErrorType.Print) {
            CharSequence line = source.getCharacters(section.getStartLine());
            line = line.subSequence(line.toString().lastIndexOf(PRINT), line.length());
            Matcher matcher = MISSING_PARENTHESES_PATTERN.matcher(line);
            if (matcher.matches()) {
                String arg = matcher.group(2).trim();
                String maybeEnd = "";
                if (arg.endsWith(",")) {
                    maybeEnd = " end=\" \"";
                }
                msg = (new ErrorMessageFormatter()).format("Missing parentheses in call to 'print'. Did you mean print(%s%s)?", arg, maybeEnd);
            }
        } else if (type == PythonParser.ErrorType.Exec) {
            msg = "Missing parentheses in call to 'exec'";
        } else if (section.getCharIndex() == source.getLength()) {
            msg = "unexpected EOF while parsing";
        } else if (message != null) {
            msg = (new ErrorMessageFormatter()).format(message, arguments);
        }
        instance.setAttribute("msg", msg);
        throw PException.fromObject(instance, location, PythonOptions.isPExceptionWithJavaStacktrace(getLanguage()));
    }

    public Object getTopScopeObject() {
        return globalScopeObject;
    }

    public static final void writeInfo(String message) {
        PythonLanguage.getLogger(Python3Core.class).fine(message);
    }

    public static final void writeInfo(Supplier<String> messageSupplier) {
        PythonLanguage.getLogger(Python3Core.class).fine(messageSupplier);
    }
}

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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BlockingIOError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRWPair;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIOBase;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIncrementalNewlineDecoder;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PTextIOWrapper;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.STRICT;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.nodes.ErrorMessages.BINARY_MODE_DOESN_T_TAKE_AN_S_ARGUMENT;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_HAVE_TEXT_AND_BINARY_MODE_AT_ONCE;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_HAVE_UNBUFFERED_TEXT_IO;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_BUFFERING_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.MODE_U_CANNOT_BE_COMBINED_WITH_X_W_A_OR;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_HAVE_EXACTLY_ONE_OF_CREATE_READ_WRITE_APPEND_MODE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_MODE_S;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeWarning;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "_io")
public class IOModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOModuleBuiltinsFactory.getFactories();
    }

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("SEEK_SET", SEEK_SET);
        builtinConstants.put("SEEK_CUR", SEEK_CUR);
        builtinConstants.put("SEEK_END", SEEK_END);
        builtinConstants.put("DEFAULT_BUFFER_SIZE", DEFAULT_BUFFER_SIZE);
        PythonBuiltinClass unsupportedOpExcType = core.lookupType(IOUnsupportedOperation);
        unsupportedOpExcType.setSuperClass(core.lookupType(OSError), core.lookupType(ValueError));
        builtinConstants.put(IOUnsupportedOperation.getName(), unsupportedOpExcType);
        builtinConstants.put(BlockingIOError.getName(), core.lookupType(BlockingIOError));

        builtinConstants.put("_warn", core.lookupBuiltinModule("_warnings").getAttribute("warn"));
        builtinConstants.put("_os", core.lookupBuiltinModule("posix"));
    }

    @Builtin(name = "_IOBase", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PIOBase)
    @GenerateNodeFactory
    public abstract static class IOBaseNode extends PythonBuiltinNode {
        @Specialization
        public PythonObject create(Object cls) {
            return factory().createPythonObject(cls);
        }
    }

    @Builtin(name = "FileIO", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PFileIO)
    @GenerateNodeFactory
    public abstract static class FileIONode extends PythonBuiltinNode {
        @Specialization
        public PFileIO doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see FileIOBuiltins.InitNode
            return factory().createFileIO(cls);
        }
    }

    @Builtin(name = "BufferedReader", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PBufferedReader)
    @GenerateNodeFactory
    public abstract static class BufferedReaderNode extends PythonBuiltinNode {
        @Specialization
        public PBuffered doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BufferedReaderBuiltins.InitNode
            return factory().createBufferedReader(cls);
        }
    }

    @Builtin(name = "BufferedWriter", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PBufferedWriter)
    @GenerateNodeFactory
    public abstract static class BufferedWriterNode extends PythonBuiltinNode {
        @Specialization
        public PBuffered doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BufferedWriterBuiltins.InitNode
            return factory().createBufferedWriter(cls);
        }
    }

    @Builtin(name = "BufferedRWPair", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PBufferedRWPair)
    @GenerateNodeFactory
    public abstract static class BufferedRWPairNode extends PythonBuiltinNode {
        @Specialization
        public PRWPair doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BufferedRWPairBuiltins.InitNode
            return factory().createRWPair(cls);
        }
    }

    @Builtin(name = "BufferedRandom", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PBufferedRandom)
    @GenerateNodeFactory
    public abstract static class BufferedRandomNode extends PythonBuiltinNode {
        @Specialization
        public PBuffered doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BufferedRandomBuiltins.InitNode
            return factory().createBufferedRandom(cls);
        }
    }

    @Builtin(name = "TextIOWrapper", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PTextIOWrapper)
    @GenerateNodeFactory
    public abstract static class TextIOWrapperNode extends PythonBuiltinNode {
        @Specialization
        public PTextIO doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see TextIOWrapperBuiltins.InitNode
            return factory().createTextIO(cls);
        }
    }

    @Builtin(name = "BytesIO", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PBytesIO)
    @GenerateNodeFactory
    public abstract static class BytesIONode extends PythonBuiltinNode {
        @Specialization
        public PBytesIO doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BytesIONodeBuiltins.InitNode
            PBytesIO bytesIO = factory().createBytesIO(cls);
            bytesIO.setBuf(factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY));
            return bytesIO;
        }
    }

    @Builtin(name = "StringIO", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PStringIO)
    @GenerateNodeFactory
    public abstract static class StringIONode extends PythonBuiltinNode {
        @Specialization
        public PStringIO doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see StringIONodeBuiltins.InitNode
            return factory().createStringIO(cls);
        }
    }

    @Builtin(name = "IncrementalNewlineDecoder", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PIncrementalNewlineDecoder)
    @GenerateNodeFactory
    public abstract static class IncrementalNewlineDecoderNode extends PythonBuiltinNode {
        @Specialization
        public PNLDecoder doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see
            // IncrementalNewlineDecoderBuiltins.InitNode
            return factory().createNLDecoder(cls);
        }
    }

    private static PFileIO createFileIO(VirtualFrame frame, Object file, IONodes.IOMode mode, boolean closefd, Object opener,
                    PythonObjectFactory factory,
                    FileIOBuiltins.FileIOInit initFileIO) {
        /* Create the Raw file stream */
        mode.text = mode.universal = false; // FileIO doesn't recognize those.
        PFileIO fileIO = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        initFileIO.execute(frame, fileIO, file, mode, closefd, opener);
        return fileIO;
    }

    // PEP 578 stub
    @Builtin(name = "open_code", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    public abstract static class IOOpenCodeNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOModuleBuiltinsClinicProviders.IOOpenCodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public PFileIO openCode(VirtualFrame frame, String path,
                        @Cached FileIOBuiltins.FileIOInit initFileIO) {
            return createFileIO(frame, path, IONodes.IOMode.create("rb"), true, PNone.NONE, factory(), initFileIO);
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 1, parameterNames = {"file", "mode", "buffering", "encoding", "errors", "newline", "closefd", "opener"})
    @ArgumentClinic(name = "mode", conversionClass = IONodes.CreateIOModeNode.class, args = "true")
    @ArgumentClinic(name = "buffering", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "closefd", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @ImportStatic({IONodes.class, IONodes.IOMode.class})
    @GenerateNodeFactory
    public abstract static class IOOpenNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOModuleBuiltinsClinicProviders.IOOpenNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "!isBinary(mode)", "bufferingValue != 0"})
        protected Object openText(VirtualFrame frame, Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Shared("f") @Cached FileIOBuiltins.FileIOInit initFileIO,
                        @Shared("b") @Cached IONodes.CreateBufferedIONode createBufferedIO,
                        @Cached TextIOWrapperNodes.TextIOWrapperInitNode initTextIO,
                        @Cached("create(MODE)") SetAttributeNode setAttrNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("c") @Cached PyObjectCallMethodObjArgs callClose,
                        @Shared("e") @Cached ConditionProfile profile) {
            PFileIO fileIO = createFileIO(frame, file, mode, closefd, opener, factory(), initFileIO);
            Object result = fileIO;
            try {
                /* buffering */
                boolean isatty = false;
                int buffering = bufferingValue;
                if (buffering < 0) {
                    // copied from PFileIOBuiltins.IsAttyNode
                    isatty = posixLib.isatty(getPosixSupport(), fileIO.getFD());
                    /*-
                        // CPython way is slow in our case.
                        Object res = libFileIO.lookupAndCallRegularMethod(fileIO, frame, ISATTY);
                        isatty = libIsTrue.isTrue(res, frame);
                    */
                }

                boolean line_buffering;
                if (buffering == 1 || isatty) {
                    buffering = -1;
                    line_buffering = true;
                } else {
                    line_buffering = false;
                }

                if (buffering < 0) {
                    buffering = fileIO.getBlksize();
                }
                if (profile.profile(buffering < 0)) {
                    throw raise(ValueError, INVALID_BUFFERING_SIZE);
                }

                /* if not buffering, returns the raw file object */
                if (buffering == 0) {
                    invalidunbuf(file, mode, bufferingValue, encoding, errors, newline, closefd, opener);
                }

                /* wraps into a buffered file */

                PBuffered buffer = createBufferedIO.execute(frame, fileIO, buffering, factory(), mode);
                result = buffer;

                /* wraps into a TextIOWrapper */
                PTextIO wrapper = factory().createTextIO(PTextIOWrapper);
                initTextIO.execute(frame, wrapper, buffer, encoding,
                                errors == PNone.NONE ? STRICT : (String) errors,
                                newline, line_buffering, false);

                result = wrapper;

                setAttrNode.executeVoid(frame, wrapper, mode.mode);
                return result;
            } catch (PException e) {
                callClose.execute(frame, result, CLOSE);
                throw e;
            }
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "isBinary(mode)", "bufferingValue == 0"})
        protected PFileIO openBinaryNoBuf(VirtualFrame frame, Object file, IONodes.IOMode mode, @SuppressWarnings("unused") int bufferingValue,
                        @SuppressWarnings("unused") PNone encoding,
                        @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") PNone newline,
                        boolean closefd, Object opener,
                        @Shared("f") @Cached FileIOBuiltins.FileIOInit initFileIO) {
            return createFileIO(frame, file, mode, closefd, opener, factory(), initFileIO);
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "isBinary(mode)", "bufferingValue == 1"})
        protected Object openBinaryB1(VirtualFrame frame, Object file, IONodes.IOMode mode, int bufferingValue,
                        @SuppressWarnings("unused") PNone encoding,
                        @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") PNone newline,
                        boolean closefd, Object opener,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Shared("f") @Cached FileIOBuiltins.FileIOInit initFileIO,
                        @Shared("b") @Cached IONodes.CreateBufferedIONode createBufferedIO,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("c") @Cached PyObjectCallMethodObjArgs callClose,
                        @Shared("e") @Cached ConditionProfile profile) {
            warnNode.warnEx(frame, RuntimeWarning, "line buffering (buffering=1) isn't supported in binary mode, the default buffer size will be used", 1);
            return openBinary(frame, file, mode, bufferingValue, encoding, errors, newline, closefd, opener, initFileIO, createBufferedIO, posixLib, callClose, profile);
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "isBinary(mode)", "bufferingValue != 1", "bufferingValue != 0"})
        protected Object openBinary(VirtualFrame frame, Object file, IONodes.IOMode mode, int bufferingValue,
                        @SuppressWarnings("unused") PNone encoding,
                        @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") PNone newline,
                        boolean closefd, Object opener,
                        @Shared("f") @Cached FileIOBuiltins.FileIOInit initFileIO,
                        @Shared("b") @Cached IONodes.CreateBufferedIONode createBufferedIO,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("c") @Cached PyObjectCallMethodObjArgs callClose,
                        @Shared("e") @Cached ConditionProfile profile) {
            PFileIO fileIO = createFileIO(frame, file, mode, closefd, opener, factory(), initFileIO);
            try {
                /* buffering */
                boolean isatty = false;
                int buffering = bufferingValue;
                if (buffering < 0) {
                    // copied from PFileIOBuiltins.IsAttyNode
                    isatty = posixLib.isatty(getPosixSupport(), fileIO.getFD());
                    /*-
                        // CPython way is slow in our case.
                        Object res = libFileIO.lookupAndCallRegularMethod(fileIO, frame, ISATTY);
                        isatty = libIsTrue.isTrue(res, frame);
                    */
                }

                if (buffering == 1 || isatty) {
                    buffering = -1;
                }

                if (buffering < 0) {
                    buffering = fileIO.getBlksize();
                }
                if (profile.profile(buffering < 0)) {
                    throw raise(ValueError, INVALID_BUFFERING_SIZE);
                }

                /* if not buffering, returns the raw file object */
                if (buffering == 0) {
                    return fileIO;
                }

                /* wraps into a buffered file */

                /* if binary, returns the buffered file */
                return createBufferedIO.execute(frame, fileIO, buffering, factory(), mode);
            } catch (PException e) {
                callClose.execute(frame, fileIO, CLOSE);
                throw e;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isUnknown(mode)")
        protected Object unknownMode(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener) {
            throw raise(ValueError, UNKNOWN_MODE_S, mode.mode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isTB(mode)")
        protected Object invalidTB(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener) {
            throw raise(ValueError, CAN_T_HAVE_TEXT_AND_BINARY_MODE_AT_ONCE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isValidUniveral(mode)")
        protected Object invalidUniversal(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener) {
            throw raise(ValueError, MODE_U_CANNOT_BE_COMBINED_WITH_X_W_A_OR);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isXRWA(mode)")
        protected Object invalidxrwa(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener) {
            throw raise(ValueError, MUST_HAVE_EXACTLY_ONE_OF_CREATE_READ_WRITE_APPEND_MODE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isBinary(mode)", "isAnyNotNone(encoding, errors, newline)"})
        protected Object invalidBinary(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener) {
            String s;
            if (encoding != PNone.NONE) {
                s = "encoding";
            } else if (errors != PNone.NONE) {
                s = "errors";
            } else {
                s = "newline";
            }
            throw raise(ValueError, BINARY_MODE_DOESN_T_TAKE_AN_S_ARGUMENT, s);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isBinary(mode)", "bufferingValue == 0"})
        protected Object invalidunbuf(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener) {
            throw raise(ValueError, CAN_T_HAVE_UNBUFFERED_TEXT_IO);
        }

        public static boolean isAnyNotNone(Object encoding, Object errors, Object newline) {
            return encoding != PNone.NONE || errors != PNone.NONE || newline != PNone.NONE;
        }
    }
}

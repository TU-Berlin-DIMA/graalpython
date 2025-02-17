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
package com.oracle.graal.python.builtins.objects.module;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SPEC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltinsClinicProviders.ModuleNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonModule)
public class ModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"self", "name", "doc"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    public abstract static class ModuleNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ModuleNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public PNone module(PythonModule self, String name, Object doc,
                        @Cached WriteAttributeToObjectNode writeName,
                        @Cached WriteAttributeToObjectNode writeDoc,
                        @Cached WriteAttributeToObjectNode writePackage,
                        @Cached WriteAttributeToObjectNode writeLoader,
                        @Cached WriteAttributeToObjectNode writeSpec,
                        @Cached GetOrCreateDictNode getDict) {
            // create dict if missing
            getDict.execute(self);

            // init
            writeName.execute(self, __NAME__, name);
            if (doc != PNone.NO_VALUE) {
                writeDoc.execute(self, __DOC__, doc);
            } else {
                writeDoc.execute(self, __DOC__, PNone.NONE);
            }
            writePackage.execute(self, __PACKAGE__, PNone.NONE);
            writeLoader.execute(self, __LOADER__, PNone.NONE);
            writeSpec.execute(self, __SPEC__, PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class ModuleDirNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object dir(VirtualFrame frame, PythonModule self,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached IsBuiltinClassProfile isDictProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CallNode callNode,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached PyObjectLookupAttr lookup,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hashLib) {
            Object dict = lookup.execute(frame, self, __DICT__);
            if (isDict(dict, isDictProfile)) {
                HashingStorage dictStorage = ((PHashingCollection) dict).getDictStorage();
                Object dirFunc = hashLib.getItem(dictStorage, __DIR__);
                if (dirFunc != null) {
                    return callNode.execute(frame, dirFunc);
                } else {
                    return constructListNode.execute(frame, dict);
                }
            } else {
                String name = getName(self, getDict, hashLib, castToJavaStringNode);
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_DICTIONARY, name);
            }
        }

        private String getName(PythonModule self, GetDictIfExistsNode getDict, HashingStorageLibrary hashLib, CastToJavaStringNode castToJavaStringNode) {
            PDict dict = getDict.execute(self);
            if (dict != null) {
                Object name = hashLib.getItem(dict.getDictStorage(), __NAME__);
                if (name != null) {
                    return castToJavaStringNode.execute(name);
                }
            }
            throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.NAMELESS_MODULE);
        }

        protected static boolean isDict(Object object, IsBuiltinClassProfile profile) {
            return profile.profileObject(object, PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ModuleDictNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isNoValue(none)"}, limit = "1")
        Object doManagedCachedShape(PythonModule self, @SuppressWarnings("unused") PNone none,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @CachedLibrary("self") DynamicObjectLibrary dynamicObjectLibrary) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                if (hasInitialProperties(dynamicObjectLibrary, self)) {
                    return PNone.NONE;
                }
                dict = createDict(self, setDict);
            }
            return dict;
        }

        @Specialization(guards = "isNoValue(none)", replaces = "doManagedCachedShape")
        Object doManaged(PythonModule self, @SuppressWarnings("unused") PNone none,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                if (hasInitialPropertiesUncached(self)) {
                    return PNone.NONE;
                }
                dict = createDict(self, setDict);
            }
            return dict;
        }

        @Specialization(guards = "isNoValue(none)")
        Object doNativeObject(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone none,
                        @Cached GetDictIfExistsNode getDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                doError(self, none);
            }
            return dict;
        }

        @Fallback
        Object doError(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(PythonBuiltinClassType.TypeError, "descriptor '__dict__' for 'module' objects doesn't apply to a '%p' object", self);
        }

        private PDict createDict(PythonModule self, SetDictNode setDict) {
            PDict dict = factory().createDictFixedStorage(self);
            setDict.execute(self, dict);
            return dict;
        }

        @TruffleBoundary
        private static boolean hasInitialPropertiesUncached(PythonModule self) {
            return hasInitialProperties(DynamicObjectLibrary.getUncached(), self);
        }

        private static boolean hasInitialProperties(DynamicObjectLibrary dynamicObjectLibrary, PythonModule self) {
            return hasInitialPropertyCount(dynamicObjectLibrary, self) && initialPropertiesChanged(dynamicObjectLibrary, self);
        }

        private static boolean hasInitialPropertyCount(DynamicObjectLibrary dynamicObjectLibrary, PythonModule self) {
            return dynamicObjectLibrary.getShape(self).getPropertyCount() == PythonModule.INITIAL_MODULE_ATTRS.length;
        }

        @ExplodeLoop
        private static boolean initialPropertiesChanged(DynamicObjectLibrary lib, PythonModule self) {
            for (int i = 0; i < PythonModule.INITIAL_MODULE_ATTRS.length; i++) {
                if (lib.getOrDefault(self, PythonModule.INITIAL_MODULE_ATTRS[i], PNone.NO_VALUE) != PNone.NO_VALUE) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = __GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ModuleGetattritbuteNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getattributeString(VirtualFrame frame, PythonModule self, String key,
                        @Shared("getattr") @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Shared("handleException") @Cached HandleGetattrExceptionNode handleException) {
            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                return handleException.execute(frame, self, key, e);
            }
        }

        @Specialization
        Object getattribute(VirtualFrame frame, PythonModule self, Object keyObj,
                        @Cached CastToJavaStringNode castKeyToStringNode,
                        @Shared("getattr") @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Shared("handleException") @Cached HandleGetattrExceptionNode handleException) {
            String key;
            try {
                key = castKeyToStringNode.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }
            return getattributeString(frame, self, key, objectGetattrNode, handleException);
        }

        protected abstract static class HandleGetattrExceptionNode extends PNodeWithRaise {
            public abstract Object execute(VirtualFrame frame, PythonModule self, String key, PException e);

            @Specialization
            Object getattribute(VirtualFrame frame, PythonModule self, String key, PException e,
                            @Cached IsBuiltinClassProfile isAttrError,
                            @Cached ReadAttributeFromObjectNode readGetattr,
                            @Cached ConditionProfile customGetAttr,
                            @Cached CallNode callNode,
                            @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                            @Cached CastToJavaStringNode castNameToStringNode) {
                e.expect(PythonBuiltinClassType.AttributeError, isAttrError);
                Object getAttr = readGetattr.execute(self, __GETATTR__);
                if (customGetAttr.profile(getAttr != PNone.NO_VALUE)) {
                    return callNode.execute(frame, getAttr, key);
                } else {
                    String moduleName;
                    try {
                        moduleName = castNameToStringNode.execute(readGetattr.execute(self, __NAME__));
                    } catch (CannotCastException ce) {
                        // we just don't have the module name
                        moduleName = null;
                    }
                    if (moduleName != null) {
                        Object moduleSpec = readGetattr.execute(self, __SPEC__);
                        if (moduleSpec != PNone.NO_VALUE) {
                            Object isInitializing = readGetattr.execute(moduleSpec, "_initializing");
                            if (isInitializing != PNone.NO_VALUE && castToBooleanNode.executeBoolean(frame, isInitializing)) {
                                throw raise(AttributeError, ErrorMessages.MODULE_PARTIALLY_INITIALIZED_S_HAS_NO_ATTR_S, moduleName, key);
                            }
                        }
                        throw raise(AttributeError, ErrorMessages.MODULE_S_HAS_NO_ATTR_S, moduleName, key);
                    }
                    throw raise(AttributeError, ErrorMessages.MODULE_HAS_NO_ATTR_S, key);
                }
            }
        }

        @Specialization(guards = "!isPythonModule(self)")
        Object getattribute(Object self, @SuppressWarnings("unused") Object key) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __GETATTRIBUTE__, "module", self);
        }

    }
}

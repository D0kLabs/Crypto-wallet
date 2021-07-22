package com.d0klabs.cryptowalt.data;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class MethodEditor implements Opcode {
    public static boolean PRESERVE_DEBUG = true;
    public static boolean UNIQUE_HANDLERS = false;
    public static boolean OPT_STACK_2 = false;
    private ClassEditor editor;
    private MethodInfo methodInfo;
    private String name;
    private Type type;
    private LinkedList code;
    private LinkedList tryCatches;
    private LinkedList lineNumbers;
    private LocalVariable[] params;
    private int maxStack;
    private int maxLabel;
    private int maxLocals;
    private boolean isDirty;
    private Map locals;
    private Type[] paramTypes;
    public UseMap uMap;
    private boolean isDeleted;

    public MethodEditor(ClassEditor editor, int modifiers, Class returnType, String methodName, Class[] paramTypes, Class[] exceptionTypes) {
        this(editor, modifiers, returnType == null ? null : Type.getType(returnType), methodName, convertTypes(paramTypes), convertTypes(exceptionTypes));
    }

    private static Type[] convertTypes(Class[] classes) {
        if (classes == null) {
            return null;
        } else {
            Type[] types = new Type[classes.length];

            for(int i = 0; i < types.length; ++i) {
                types[i] = Type.getType(classes[i]);
            }

            return types;
        }
    }

    public MethodEditor(ClassEditor editor, int modifiers, Type returnType, String methodName, Type[] paramTypes, Type[] exceptionTypes) {
        this.isDeleted = false;
        this.editor = editor;
        this.name = methodName;
        if (returnType == null) {
            returnType = Type.VOID;
        }

        if (paramTypes == null) {
            paramTypes = new Type[0];
        }

        if (exceptionTypes == null) {
            exceptionTypes = new Type[0];
        }

        ConstantPool cp = editor.constants();
        int nameIndex = cp.getUTF8Index(methodName);
        this.type = Type.getType(paramTypes, returnType);
        int typeIndex = cp.getTypeIndex(this.type);
        int exceptionIndex = cp.getUTF8Index("Exceptions");
        int[] exceptionTypeIndices = new int[exceptionTypes.length];

        int codeIndex;
        for(codeIndex = 0; codeIndex < exceptionTypes.length; ++codeIndex) {
            Type eType = exceptionTypes[codeIndex];
            exceptionTypeIndices[codeIndex] = cp.getTypeIndex(eType);
        }

        codeIndex = cp.getUTF8Index("Code");
        ClassInfo classInfo = editor.classInfo();
        this.methodInfo = classInfo.addNewMethod(modifiers, typeIndex, nameIndex, exceptionIndex, exceptionTypeIndices, codeIndex);
        this.code = new LinkedList();
        this.tryCatches = new LinkedList();
        this.lineNumbers = new LinkedList();
        this.locals = new HashMap();
        if (!this.isStatic()) {
            this.params = new LocalVariable[this.type.stackHeight() + 1];
        } else {
            this.params = new LocalVariable[this.type.stackHeight()];
        }

        this.paramTypes = new Type[this.params.length];
        Type[] indexedParams = this.type().indexedParamTypes();
        int q;
        if (!this.isStatic()) {
            this.paramTypes[0] = this.declaringClass().type();

            for(q = 1; q < this.paramTypes.length; ++q) {
                this.paramTypes[q] = indexedParams[q - 1];
            }
        } else {
            for(q = 0; q < this.paramTypes.length; ++q) {
                this.paramTypes[q] = indexedParams[q];
            }
        }

        for(q = 0; q < this.params.length; ++q) {
            this.params[q] = new LocalVariable((String)null, this.paramTypes[q], q);
        }

        this.maxLocals = this.paramTypes.length;
        this.isDirty = true;
    }

    public MethodEditor(ClassEditor editor, MethodInfo methodInfo) {
        this.isDeleted = false;
        ConstantPool cp = editor.constants();
        this.methodInfo = methodInfo;
        this.editor = editor;
        this.isDirty = false;
        this.maxLabel = 0;
        this.maxLocals = methodInfo.maxLocals();
        this.maxStack = methodInfo.maxStack();
        this.locals = new HashMap();
        int index = methodInfo.nameIndex();
        this.name = (String)cp.constantAt(index);
        index = methodInfo.typeIndex();
        String typeName = (String)cp.constantAt(index);
        this.type = Type.getType(typeName);
        this.code = new LinkedList();
        this.tryCatches = new LinkedList();
        this.lineNumbers = new LinkedList();
        if (!this.isStatic()) {
            this.params = new LocalVariable[this.type.stackHeight() + 1];
        } else {
            this.params = new LocalVariable[this.type.stackHeight()];
        }

        this.paramTypes = new Type[this.params.length];
        Type[] indexedParams = this.type().indexedParamTypes();
        int q;
        if (!this.isStatic()) {
            this.paramTypes[0] = this.declaringClass().type();

            for(q = 1; q < this.paramTypes.length; ++q) {
                this.paramTypes[q] = indexedParams[q - 1];
            }
        } else {
            for(q = 0; q < this.paramTypes.length; ++q) {
                this.paramTypes[q] = indexedParams[q];
            }
        }

        for(q = 0; q < this.params.length; ++q) {
            this.params[q] = new LocalVariable((String)null, this.paramTypes[q], q);
        }

        byte[] array = methodInfo.code();
        if (array != null && array.length != 0) {
            int[] next = new int[array.length];
            int[][] targets = new int[array.length][];
            int[][] lookups = new int[array.length][];
            Label[] label = new Label[array.length + 1];
            int i;
            LocalVariable[][] localVars;
            int start;
            if (PRESERVE_DEBUG && array.length < 65536) {
                MethodInfo.LocalDebugInfo[] locals = methodInfo.locals();
                int max = 0;

                for(i = 0; i < locals.length; ++i) {
                    if (max <= locals[i].index()) {
                        max = locals[i].index() + 1;
                    }
                }

                localVars = new LocalVariable[array.length][max];

                for(i = 0; i < locals.length; ++i) {
                    start = locals[i].startPC();
                    start = start + locals[i].length();
                    String localName = (String)cp.constantAt(locals[i].nameIndex());
                    String localType = (String)cp.constantAt(locals[i].typeIndex());
                    LocalVariable var = new LocalVariable(localName, Type.getType(localType), locals[i].index());

                    for(int pc = start; pc <= start; ++pc) {
                        if (pc < localVars.length) {
                            localVars[pc][locals[i].index()] = var;
                        }
                    }

                    if (start == 0 && locals[i].index() < this.params.length) {
                        this.params[locals[i].index()] = var;
                    }
                }

                MethodInfo.LineNumberDebugInfo[] lineNumbers = methodInfo.lineNumbers();

                for(i = 0; i < lineNumbers.length; ++i) {
                    start = lineNumbers[i].startPC();
                    if (label[start] == null) {
                        label[start] = new Label(start, false);
                    }

                    this.addLineNumberEntry(label[start], lineNumbers[i].lineNumber());
                }
            } else {
                localVars = new LocalVariable[array.length][0];
            }

            label[0] = new Label(0, true);
            int numInst = 0;

            for(i = 0; i < array.length; i = next[i]) {
                next[i] = this.munchCode(array, i, targets, lookups);
                ++numInst;
                if (targets[i] != null) {
                    for(int j = 0; j < targets[i].length; ++j) {
                        if (targets[i][j] < array.length) {
                            label[targets[i][j]] = new Label(targets[i][j], true);
                        }
                    }
                }
            }

            MethodInfo.Catch[] exc = methodInfo.exceptionHandlers();

            for(i = 0; i < exc.length; ++i) {
                start = exc[i].startPC();
                start = exc[i].endPC();
                int handler = exc[i].handlerPC();
                label[start] = new Label(start, true);
                label[start] = new Label(start, true);
                label[handler] = new Label(handler, true);
                Type catchType = (Type)cp.constantAt(exc[i].catchTypeIndex());
                this.addTryCatch(new TryCatch(label[start], label[start], label[handler], catchType));
            }

            for(i = 0; i < array.length; i = next[i]) {
                InstructionVisitor.Instruction inst = new InstructionVisitor.Instruction(array, i, targets[i], lookups[i], localVars[i], cp);
                if (label[i] != null) {
                    this.code.add(label[i]);
                }

                this.code.add(inst);
                if ((inst.isJump() || inst.isReturn() || inst.isJsr() || inst.isRet() || inst.isThrow() || inst.isSwitch()) && next[i] < array.length) {
                    label[next[i]] = new Label(next[i], true);
                }
            }

            label[array.length] = new Label(array.length, true);
            this.code.add(label[array.length]);
            this.maxLabel = array.length + 1;
            if (OPT_STACK_2) {
                this.uMap = new UseMap();
            }

            this.setDirty(false);
        }
    }

    public Type[] exceptions() {
        ConstantPool cp = this.editor.constants();
        int[] indices = this.methodInfo.exceptionTypes();
        Type[] types = new Type[indices.length];

        for(int i = 0; i < indices.length; ++i) {
            types[i] = (Type)cp.constantAt(indices[i]);
        }

        return types;
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
        if (this.isDirty) {
            this.editor.setDirty(true);
        }

    }

    public void delete() {
        this.setDirty(true);
        this.isDeleted = true;
    }

    public Type[] paramTypes() {
        return this.paramTypes;
    }

    public LocalVariable paramAt(int index) {
        if (index < this.params.length && this.params[index] != null) {
            return this.params[index];
        } else {
            LocalVariable local = new LocalVariable(index);
            if (index < this.params.length) {
                this.params[index] = local;
            }

            return local;
        }
    }

    public MethodInfo methodInfo() {
        return this.methodInfo;
    }

    public ClassEditor declaringClass() {
        return this.editor;
    }

    public int maxLocals() {
        return this.maxLocals;
    }

    public boolean isPublic() {
        return (this.methodInfo.modifiers() & 1) != 0;
    }

    public boolean isPrivate() {
        return (this.methodInfo.modifiers() & 2) != 0;
    }

    public boolean isProtected() {
        return (this.methodInfo.modifiers() & 4) != 0;
    }

    public boolean isPackage() {
        return !this.isPublic() && !this.isPrivate() && !this.isProtected();
    }

    public boolean isStatic() {
        return (this.methodInfo.modifiers() & 8) != 0;
    }

    public boolean isFinal() {
        return (this.methodInfo.modifiers() & 16) != 0;
    }

    public boolean isSynchronized() {
        return (this.methodInfo.modifiers() & 32) != 0;
    }

    public boolean isNative() {
        return (this.methodInfo.modifiers() & 256) != 0;
    }

    public boolean isAbstract() {
        return (this.methodInfo.modifiers() & 1024) != 0;
    }

    public boolean isInterface() {
        return this.editor.isInterface();
    }

    public void setPublic(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 1;
            } else {
                mod &= -2;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setPrivate(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 2;
            } else {
                mod &= -3;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setProtected(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 4;
            } else {
                mod &= -5;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setStatic(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 8;
            } else {
                mod &= -9;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setFinal(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 16;
            } else {
                mod &= -17;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setSynchronized(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 32;
            } else {
                mod &= -33;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setNative(boolean flag) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int mod = this.methodInfo.modifiers();
            if (flag) {
                mod |= 256;
            } else {
                mod &= -257;
            }

            this.methodInfo.setModifiers(mod);
            this.setDirty(true);
        }
    }

    public void setAbstract(boolean flag) {
        int mod = this.methodInfo.modifiers();
        if (flag) {
            mod |= 1024;
        } else {
            mod &= -1025;
        }

        this.methodInfo.setModifiers(mod);
        this.setDirty(true);
    }

    private int munchCode(byte[] code, int index, int[][] targets, int[][] lookups) {
        int opcode = InstructionVisitor.Instruction.toUByte(code[index]);
        int next = index + Opcode.opcSize[opcode];
        int target;
        int value;
        int npairs;
        int j;
        int k;
        int[] var10000;
        switch(opcode) {
            case 153:
            case 154:
            case 155:
            case 156:
            case 157:
            case 158:
            case 159:
            case 160:
            case 161:
            case 162:
            case 163:
            case 164:
            case 165:
            case 166:
            case 198:
            case 199:
                target = InstructionVisitor.Instruction.toShort(code[index + 1], code[index + 2]);
                targets[index] = new int[1];
                targets[index][0] = index + target;
                break;
            case 167:
            case 168:
                target = InstructionVisitor.Instruction.toShort(code[index + 1], code[index + 2]);
                targets[index] = new int[1];
                targets[index][0] = index + target;
            case 169:
            case 172:
            case 173:
            case 174:
            case 175:
            case 176:
            case 177:
            case 178:
            case 179:
            case 180:
            case 181:
            case 182:
            case 183:
            case 184:
            case 185:
            case 186:
            case 187:
            case 188:
            case 189:
            case 190:
            case 191:
            case 192:
            case 193:
            case 194:
            case 195:
            case 197:
            default:
                break;
            case 170:
                for(j = index + 1; j % 4 != 0; ++j) {
                }

                target = (short) InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                j += 4;
                value = InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                j += 4;
                npairs = InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                j += 4;
                lookups[index] = new int[2];
                lookups[index][0] = value;
                lookups[index][1] = npairs;
                targets[index] = new int[npairs - value + 2];
                k = 0;
                var10000 = targets[index];
                k = k + 1;
                var10000[k] = index + target;

                for(next = j + (npairs - value + 1) * 4; j < next; targets[index][k++] = index + target) {
                    target = (short) InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                    j += 4;
                }

                return next;
            case 171:
                for(j = index + 1; j % 4 != 0; ++j) {
                }

                target = (short) InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                j += 4;
                npairs = InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                j += 4;
                lookups[index] = new int[npairs];
                targets[index] = new int[npairs + 1];
                k = 0;
                var10000 = targets[index];
                k = k + 1;
                var10000[k] = index + target;

                for(next = j + npairs * 8; j < next; targets[index][k++] = index + target) {
                    value = InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                    j += 4;
                    target = (short) InstructionVisitor.Instruction.toInt(code[j], code[j + 1], code[j + 2], code[j + 3]);
                    j += 4;
                    lookups[index][k - 1] = value;
                }

                return next;
            case 196:
                if (code[index + 1] == -124) {
                    next = index + 6;
                } else {
                    next = index + 4;
                }
                break;
            case 200:
            case 201:
                target = (short) InstructionVisitor.Instruction.toInt(code[index + 1], code[index + 2], code[index + 3], code[index + 4]);
                targets[index] = new int[1];
                targets[index][0] = index + target;
        }

        return next;
    }

    public void clearCode() {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.code.clear();
            this.tryCatches.clear();
            this.maxLocals = 0;
            this.maxStack = 0;
            this.setDirty(true);
        }
    }

    public void clearCode2() {
        this.code.clear();
        this.tryCatches.clear();
        this.maxStack = 0;
        this.setDirty(true);
    }

    public String name() {
        return this.name;
    }

    public boolean isConstructor() {
        return this.name.equals("<init>");
    }

    public Type type() {
        return this.type;
    }

    public NameAndType nameAndType() {
        return new NameAndType(this.name(), this.type());
    }

    public MemberRef memberRef() {
        return new MemberRef(this.declaringClass().type(), this.nameAndType());
    }

    public int codeLength() {
        return this.code.size();
    }

    public void setCode(List v) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.code = new LinkedList(v);
            this.setDirty(true);
        }
    }

    public List code() {
        return this.code;
    }

    public Label firstBlock() {
        Iterator iter = this.code.iterator();

        while(iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Label) {
                Label l = (Label)obj;
                if (l.startsBlock()) {
                    return l;
                }
            }
        }

        return null;
    }

    public Label nextBlock(Label label) {
        boolean seen = false;
        Iterator iter = this.code.iterator();

        while(iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Label) {
                if (seen) {
                    Label l = (Label)obj;
                    if (l.startsBlock()) {
                        return l;
                    }
                } else if (label.equals(obj)) {
                    seen = true;
                }
            }
        }

        return null;
    }

    public void removeCodeAt(int i) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.code.remove(i);
            this.setDirty(true);
        }
    }

    public void insertCodeAt(Object obj, int i) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.code.add(i, obj);
            this.setDirty(true);
        }
    }

    public void replaceCodeAt(Object obj, int i) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.code.set(i, obj);
            this.setDirty(true);
        }
    }

    public Object codeElementAt(int i) {
        return this.code.get(i);
    }

    public void addLineNumberEntry(Label label, int lineNumber) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.lineNumbers.add(new MethodEditor.LineNumberEntry(label, lineNumber));
            this.setDirty(true);
        }
    }

    public int numTryCatches() {
        return this.tryCatches.size();
    }

    public Collection tryCatches() {
        return this.tryCatches;
    }

    public void addTryCatch(TryCatch tryCatch) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {

            this.tryCatches.add(tryCatch);
            this.setDirty(true);
        }
    }

    public LocalVariable newLocal(Type type) {
        int index = this.maxLocals;
        this.maxLocals += type.stackHeight();
        this.setDirty(true);
        LocalVariable local = new LocalVariable(index);
        this.locals.put(new Integer(index), local);
        return local;
    }

    public LocalVariable newLocal(boolean isWide) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            int index = this.maxLocals;
            this.maxLocals += isWide ? 2 : 1;
            this.setDirty(true);
            LocalVariable local = new LocalVariable(index);
            this.locals.put(new Integer(index), local);
            return local;
        }
    }

    public LocalVariable localAt(int index) {
        LocalVariable local = (LocalVariable)this.locals.get(new Integer(index));
        if (local == null) {
            local = new LocalVariable(index);
            this.locals.put(new Integer(index), local);
            if (index >= this.maxLocals) {
                this.maxLocals = index++;
            }
        }

        return local;
    }

    public void addInstruction(int opcodeClass) {
        this.addInstruction(new InstructionVisitor.Instruction(opcodeClass));
    }

    public void addInstruction(int opcodeClass, Object operand) {
        this.addInstruction(new InstructionVisitor.Instruction(opcodeClass, operand));
    }

    public void addInstruction(InstructionVisitor.Instruction inst) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.code.add(inst);
            this.setDirty(true);
        }
    }

    public Label newLabel() {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            this.setDirty(true);
            return new Label(this.maxLabel++);
        }
    }

    public Label newLabelTrue() {
        return new Label(this.maxLabel++, true);
    }

    public void addLabel(Label label) {
        if (this.isDeleted) {
            String s = "Cannot change a field once it has been marked for deletion";
            throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
        } else {
            if (label.index() >= this.maxLabel) {
                this.maxLabel = label.index() + 1;
            }

            this.code.add(label);
            this.setDirty(true);
        }
    }

    public void commit() {
        ConstantPool cp = this.editor.constants();
        if (this.isDeleted) {
            int nameIndex = cp.getUTF8Index(this.name);
            int typeIndex = cp.getTypeIndex(this.type);
            this.editor.classInfo().deleteMethod(nameIndex, typeIndex);
        } else {
            this.methodInfo.setNameIndex(cp.addConstant(1, this.name));
            this.methodInfo.setTypeIndex(cp.addConstant(1, this.type.descriptor()));
            if (this.isNative() || this.isAbstract()) {
                return;
            }

            List vars = new ArrayList();
            List copy = new LinkedList();
            Iterator iter = this.code.iterator();

            while(true) {
                label153:
                while(iter.hasNext()) {
                    Object ce = iter.next();
                    if (ce instanceof Label) {
                        copy.add(ce);
                    } else {
                        InstructionVisitor.Instruction inst = (InstructionVisitor.Instruction)ce;
                        LocalVariable var = null;
                        if (inst.operand() instanceof LocalVariable) {
                            var = (LocalVariable)inst.operand();
                        } else if (inst.operand() instanceof InstructionVisitor.IncOperand) {
                            var = ((InstructionVisitor.IncOperand)inst.operand()).var();
                        }

                        if (var != null && var.name() != null && var.type() != null) {
                            for(int j = vars.size() - 1; j >= 0; --j) {
                                MethodEditor.LocalVarEntry v = (MethodEditor.LocalVarEntry)vars.get(j);
                                if (v.var.equals(var)) {
                                    v.end = this.newLabel();
                                    copy.add(ce);
                                    copy.add(v.end);
                                    continue label153;
                                }

                                if (v.var.index() == var.index()) {
                                    break;
                                }
                            }

                            Label start = this.newLabel();
                            Label end = this.newLabel();
                            vars.add(new MethodEditor.LocalVarEntry(var, start, end));
                            copy.add(start);
                            copy.add(ce);
                            copy.add(end);
                        } else {
                            copy.add(ce);
                        }
                    }
                }

                HashSet seen = new HashSet();
                ArrayList dup = new ArrayList();
                iter = this.tryCatches.iterator();

                while(iter.hasNext()) {
                    TryCatch tc = (TryCatch)iter.next();
                    if (!seen.contains(tc.handler())) {
                        seen.add(tc.handler());
                    } else {
                        dup.add(tc);
                    }
                }

                TryCatch tc;
                if (dup.size() != 0) {
                    ListIterator liter = copy.listIterator();

                    label123:
                    while(true) {
                        Object ce;
                        do {
                            if (!liter.hasNext()) {
                                break label123;
                            }

                            ce = liter.next();
                        } while(!(ce instanceof Label));

                        Iterator d = dup.iterator();

                        while(d.hasNext()) {
                            tc = (TryCatch)d.next();
                            if (tc.handler().equals(ce)) {
                                Label handler2 = this.newLabel();
                                Label code = this.newLabel();
                                InstructionVisitor.Instruction nop = new InstructionVisitor.Instruction(0);
                                liter.add(nop);
                                InstructionVisitor.Instruction jump = new InstructionVisitor.Instruction(167, code);
                                liter.add(jump);
                                liter.add(handler2);
                                nop = new InstructionVisitor.Instruction(0);
                                liter.add(nop);
                                jump = new InstructionVisitor.Instruction(167, code);
                                liter.add(jump);
                                liter.add(code);
                                tc.setHandler(handler2);
                                d.remove();
                            }
                        }
                    }
                }

                CodeArray array = new CodeArray(this, cp, copy);
                byte[] arr = array.array();
                this.methodInfo.setCode(arr);
                this.methodInfo.setMaxLocals(array.maxLocals());
                this.methodInfo.setMaxStack(array.maxStack());
                int start;
                int end;
                if (PRESERVE_DEBUG && arr.length < 65536) {
                    MethodInfo.LocalDebugInfo[] locals = new MethodInfo.LocalDebugInfo[vars.size()];

                    for(int i = 0; i < vars.size(); ++i) {
                        MethodEditor.LocalVarEntry entry = (MethodEditor.LocalVarEntry)vars.get(i);
                        end = array.labelIndex(entry.start);
                        end = array.labelIndex(entry.end);
                        if (end < end) {
                            locals[i] = new MethodInfo.LocalDebugInfo(end, end - end, cp.addConstant(1, entry.var.name()), cp.addConstant(1, entry.var.type().descriptor()), entry.var.index());
                        }
                    }

                    this.methodInfo.setLocals(locals);
                    MethodInfo.LineNumberDebugInfo[] lines = new MethodInfo.LineNumberDebugInfo[this.lineNumbers.size()];
                    start = 0;

                    MethodEditor.LineNumberEntry line;
                    for(iter = this.lineNumbers.iterator(); iter.hasNext(); lines[start++] = new MethodInfo.LineNumberDebugInfo(array.labelIndex(line.label), line.lineNumber)) {
                        line = (MethodEditor.LineNumberEntry)iter.next();
                    }

                    this.methodInfo.setLineNumbers(lines);
                } else {
                    this.methodInfo.setLineNumbers((MethodInfo.LineNumberDebugInfo[])null);
                    this.methodInfo.setLocals((MethodInfo.LocalDebugInfo[])null);
                }

                List c = new LinkedList();
                iter = this.tryCatches.iterator();

                while(iter.hasNext()) {
                    tc = (TryCatch)iter.next();
                    start = array.labelIndex(tc.start());
                    end = array.labelIndex(tc.end());
                    if (start < end) {
                        c.add(new MethodInfo.Catch(start, end, array.labelIndex(tc.handler()), cp.addConstant(7, tc.type())));
                    }
                }

                Object[] a = c.toArray();
                MethodInfo.Catch[] catches = new MethodInfo.Catch[a.length];
                System.arraycopy(a, 0, catches, 0, a.length);
                this.methodInfo.setExceptionHandlers(catches);
                break;
            }
        }
        this.isDirty = false;
    }

    public void print(PrintStream out) {
        out.println(this.name + "." + this.type + (this.isDirty ? " (dirty) " : "") + ":");
        Iterator iter = this.code.iterator();

        while(iter.hasNext()) {
            out.println("    " + iter.next());
        }

        iter = this.tryCatches.iterator();

        while(iter.hasNext()) {
            out.println("    " + iter.next());
        }

    }

    public boolean equals(Object o) {
        if (o instanceof MethodEditor) {
            MethodEditor other = (MethodEditor)o;
            if (!other.declaringClass().equals(this.declaringClass())) {
                return false;
            } else if (!other.name().equals(this.name())) {
                return false;
            } else {
                return other.type().equals(this.type());
            }
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.declaringClass().hashCode() + this.name().hashCode() + this.type().hashCode();
    }

    public String toString() {
        return this.editor.type() + "." + this.name + this.type;
    }

    public UseMap uMap() {
        return this.uMap;
    }

    public void rememberDef(LocalExpr e) {
        if (OPT_STACK_2) {
            this.uMap.add(e, (InstructionVisitor.Instruction)this.code.get(this.code.size() - 1));
        }

    }

    class LineNumberEntry {
        Label label;
        int lineNumber;

        public LineNumberEntry(Label label, int lineNumber) {
            this.label = label;
            this.lineNumber = lineNumber;
        }
    }

    class LocalInfo {
        LocalVariable var;
        Type type;

        public LocalInfo(LocalVariable var, Type type) {
            this.var = var;
            this.type = type;
        }

        public boolean equals(Object obj) {
            return obj != null && obj instanceof MethodEditor.LocalInfo && ((MethodEditor.LocalInfo)obj).var.equals(this.var) && ((MethodEditor.LocalInfo)obj).type.equals(this.type);
        }

        public int hashCode() {
            return this.var.hashCode() ^ this.type.hashCode();
        }
    }

    class LocalVarEntry {
        LocalVariable var;
        Label start;
        Label end;

        public LocalVarEntry(LocalVariable var, Label start, Label end) {
            this.var = var;
            this.start = start;
            this.end = end;
        }
    }
}
class UseMap {
    public Hashtable map = new Hashtable();

    public UseMap() {
    }

    public void add(LocalExpr use, InstructionVisitor.Instruction inst) {
        LeafExpr.Node def = use.def();
        if (def != null) {
            this.map.put(inst, def);
        }

    }

    public boolean hasDef(InstructionVisitor.Instruction inst) {
        return this.map.containsKey(inst);
    }

    public boolean hasSameDef(InstructionVisitor.Instruction a, InstructionVisitor.Instruction b) {
        return this.map.containsKey(a) && this.map.containsKey(b) && this.map.get(a) == this.map.get(b);
    }
}
class TryCatch {
    private Label start;
    private Label end;
    private Label handler;
    private Type type;

    public TryCatch(Label start, Label end, Label handler, Type type) {
        this.start = start;
        this.end = end;
        this.handler = handler;
        this.type = type;
    }

    public Label start() {
        return this.start;
    }

    public Label end() {
        return this.end;
    }

    public Label handler() {
        return this.handler;
    }

    public void setHandler(Label handler) {
        this.handler = handler;
    }

    public Type type() {
        return this.type;
    }

    public String toString() {
        return "try " + this.start + ".." + this.end + " catch (" + this.type + ") " + this.handler;
    }
}



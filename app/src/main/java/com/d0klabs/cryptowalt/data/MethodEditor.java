package com.d0klabs.cryptowalt.data;

import android.widget.Switch;

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
import java.util.Set;
import java.util.Stack;

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
        byte k;
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
public class CodeArray implements InstructionVisitor, Opcode {
    public static boolean DEBUG = Boolean.getBoolean("CodeArray.DEBUG");
    private CodeArray.ByteCell codeTail;
    private int codeLength;
    private Map branches;
    private Map longBranches;
    private Map branchInsts;
    private Map labels;
    private int lastInst;
    private int maxStack;
    private int stackHeight;
    private int maxLocals;
    private ConstantPool constants;
    private MethodEditor method;
    private boolean longBranch;
    private List insts;

    public CodeArray(MethodEditor method, ConstantPool constants, List insts) {
        this.constants = constants;
        this.method = method;
        this.insts = insts;
        this.maxStack = 0;
        this.maxLocals = 0;
        this.longBranch = false;
        this.buildCode();
    }

    private void buildCode() {
        this.codeTail = null;
        this.codeLength = 0;
        this.branches = new HashMap();
        this.longBranches = new HashMap();
        this.branchInsts = new HashMap();
        this.labels = new HashMap();
        this.maxLocals = this.method.type().stackHeight();
        if (!this.method.isStatic()) {
            ++this.maxLocals;
        }

        this.stackHeight = 0;
        Map labelPos = new HashMap();
        int[] heights = new int[this.insts.size()];
        Map retTargets = new HashMap();
        Map retInsts = new HashMap();
        Iterator iter;
        if (DEBUG) {
            System.out.println("Building code for " + this.method.declaringClass().name() + "." + this.method.name());
            iter = this.insts.iterator();

            while(iter.hasNext()) {
                Object o = iter.next();
                System.out.println("  " + o);
            }
        }

        iter = this.insts.iterator();
        int i = 0;

        Label subLabel;
        Label label;
        Object targets;
        while(iter.hasNext()) {
            Object ce = iter.next();
            if (ce instanceof Label) {
                subLabel = (Label)ce;
                this.stackHeight = 0;
                labelPos.put(subLabel, new Integer(i));
                this.addLabel(subLabel);
                heights[i++] = this.stackHeight;
                retTargets.containsKey(subLabel);
            } else {
                if (!(ce instanceof Instruction)) {
                    throw new IllegalArgumentException();
                }

                Instruction inst = (Instruction)ce;
                inst.visit(this);
                if (inst.isJsr()) {
                    heights[i++] = this.stackHeight;
                    Object x = iter.next();
                    label = (Label)inst.operand();
                    Label target = (Label)x;
                    targets = (Set)retTargets.get(label);
                    if (targets == null) {
                        targets = new HashSet();
                        retTargets.put(label, targets);
                    }

                    ((Set)targets).add(target);
                    this.stackHeight = 0;
                    labelPos.put(target, new Integer(i));
                    this.addLabel(target);
                    heights[i++] = this.stackHeight;
                } else {
                    heights[i++] = this.stackHeight;
                }
            }
        }

        boolean foundRet;
        for(Iterator subLabels = retTargets.keySet().iterator(); subLabels.hasNext(); ) {
            subLabel = (Label)subLabels.next();
            int pos = this.insts.indexOf(subLabel);
            foundRet = false;
            ListIterator liter = this.insts.listIterator(pos);

            while(liter.hasNext()) {
                targets = liter.next();
                if (targets instanceof Instruction) {
                    Instruction inst = (Instruction)targets;
                    if (inst.isRet()) {
                        retInsts.put(inst, subLabel);
                        foundRet = true;
                        break;
                    }
                }
            }
        }

        Iterator rets;
        if (DEBUG) {
            System.out.println("Subroutines and return targets:");
            Iterator subs = retTargets.keySet().iterator();

            while(subs.hasNext()) {
                Label sub = (Label)subs.next();
                System.out.print("  " + sub + ": ");
                Set s = (Set)retTargets.get(sub);
                rets = s.iterator();

                while(rets.hasNext()) {
                    Label ret = (Label)rets.next();
                    System.out.print(ret.toString());
                    if (rets.hasNext()) {
                        System.out.print(", ");
                    }
                }

                System.out.println("");
            }
        }

        Set visited = new HashSet();
        Stack stack = new Stack();
        if (this.insts.size() > 0) {
            label = (Label)this.insts.get(0);
            visited.add(label);
            stack.push(new CodeArray.HeightRecord(label, 0));
        }

        rets = this.method.tryCatches().iterator();

        while(rets.hasNext()) {
            TryCatch tc = (TryCatch)rets.next();
            visited.add(tc.handler());
            stack.push(new CodeArray.HeightRecord(tc.handler(), 1));
        }

        while(true) {
            label243:
            while(!stack.isEmpty()) {
                CodeArray.HeightRecord h = (CodeArray.HeightRecord)stack.pop();
                Integer labelIndex = (Integer)labelPos.get(h.label);
                int start = labelIndex;
                int diff = h.height - heights[start];
                heights[start] = h.height;
                ListIterator blockIter = this.insts.listIterator(start + 1);
                i = start;

                while(true) {
                    while(true) {
                        if (!blockIter.hasNext()) {
                            continue label243;
                        }

                        Object ce = blockIter.next();
                        ++i;
                        if (ce instanceof Instruction) {
                            Instruction inst = (Instruction)ce;
                            if (inst.isReturn() || inst.isThrow()) {
                                heights[i] = 0;
                                continue label243;
                            }

                            if (!inst.isConditionalJump()) {
                                if (inst.isGoto() || inst.isJsr()) {
                                    heights[i] += diff;
                                    label = (Label)inst.operand();
                                    if (diff > 0 || !visited.contains(label)) {
                                        visited.add(label);
                                        stack.push(new CodeArray.HeightRecord(label, heights[i]));
                                    }
                                    continue label243;
                                }

                                if (inst.isRet()) {
                                    heights[i] += diff;
                                    subLabel = (Label)retInsts.get(inst);
                                    targets = (Set)retTargets.get(subLabel);
                                    Iterator retIter = targets.iterator();
                                    while(true) {
                                        int idx;
                                        do {
                                            if (!retIter.hasNext()) {
                                                continue label243;
                                            }
                                            label = (Label)retIter.next();
                                            labelIndex = (Integer)labelPos.get(label);
                                            idx = labelIndex;
                                        } while(heights[idx] >= heights[i] && visited.contains(label));

                                        visited.add(label);
                                        stack.push(new CodeArray.HeightRecord(label, heights[i]));
                                    }
                                }

                                if (inst.isSwitch()) {
                                    heights[i] += diff;

                                    Switch sw = (Switch)inst.operand();
                                    label = sw.defaultTarget();
                                    if (diff > 0 || !visited.contains(label)) {
                                        visited.add(label);
                                        stack.push(new CodeArray.HeightRecord(label, heights[i]));
                                    }

                                    Label[] targets = sw.targets();
                                    int j = 0;

                                    while(true) {
                                        if (j >= targets.length) {
                                            continue label243;
                                        }

                                        label = targets[j];
                                        if (diff > 0 || !visited.contains(label)) {
                                            visited.add(label);
                                            stack.push(new CodeArray.HeightRecord(label, heights[i]));
                                        }

                                        ++j;
                                    }
                                }

                                heights[i] += diff;
                            } else {
                                heights[i] += diff;
                                label = (Label)inst.operand();
                                if (diff > 0 || !visited.contains(label)) {
                                    visited.add(label);
                                    stack.push(new CodeArray.HeightRecord(label, heights[i]));
                                }
                            }
                        } else if (ce instanceof Label) {
                            label = (Label)ce;
                            diff = heights[i - 1] - heights[i];
                            if (diff > 0 || !visited.contains(label)) {
                                visited.add(label);
                                heights[i] = heights[i - 1];
                            }
                        }
                    }
                }
            }

            this.maxStack = 0;

            for(i = 0; i < heights.length; ++i) {
                int h = heights[i];
                if (h > this.maxStack) {
                    this.maxStack = h;
                }
            }

            return;
        }
    }

    public int maxLocals() {
        return this.maxLocals;
    }

    public int maxStack() {
        return this.maxStack;
    }

    public int labelIndex(Label label) {
        Integer i = (Integer)this.labels.get(label);
        if (i != null) {
            return i;
        } else {
            throw new IllegalArgumentException("Label " + label + " not found");
        }
    }

    public byte[] array() {
        if (this.branches.size() > 0 && !this.longBranch && this.codeLength >= 65536) {
            this.longBranch = true;
            this.buildCode();
        }

        byte[] c = new byte[this.codeLength];
        int i = this.codeLength;

        for(CodeArray.ByteCell p = this.codeTail; p != null; p = p.prev) {
            --i;
            c[i] = p.value;
        }

        Integer branch;
        int branchIndex;
        Integer inst;
        int instIndex;
        Label label;
        Integer target;
        int diff;
        Iterator e;
        for(e = this.branches.keySet().iterator(); e.hasNext(); c[branchIndex + 1] = (byte)(diff & 255)) {
            branch = (Integer)e.next();
            branchIndex = branch;
            inst = (Integer)this.branchInsts.get(branch);
            instIndex = inst;
            label = (Label)this.branches.get(branch);
            target = (Integer)this.labels.get(label);
            diff = target - instIndex;
            c[branchIndex] = (byte)(diff >>> 8 & 255);
        }

        for(e = this.longBranches.keySet().iterator(); e.hasNext(); c[branchIndex + 3] = (byte)(diff & 255)) {
            branch = (Integer)e.next();
            branchIndex = branch;
            inst = (Integer)this.branchInsts.get(branch);
            instIndex = inst;
            label = (Label)this.longBranches.get(branch);
            target = (Integer)this.labels.get(label);
            diff = target - instIndex;
            c[branchIndex] = (byte)(diff >>> 24 & 255);
            c[branchIndex + 1] = (byte)(diff >>> 16 & 255);
            c[branchIndex + 2] = (byte)(diff >>> 8 & 255);
        }

        return c;
    }

    public void addLabel(Label label) {
        this.labels.put(label, new Integer(this.codeLength));
    }

    public void addLongBranch(Label label) {
        this.branchInsts.put(new Integer(this.codeLength), new Integer(this.lastInst));
        this.longBranches.put(new Integer(this.codeLength), label);
        this.addByte(0);
        this.addByte(0);
        this.addByte(0);
        this.addByte(0);
    }

    public void addBranch(Label label) {
        this.branchInsts.put(new Integer(this.codeLength), new Integer(this.lastInst));
        this.branches.put(new Integer(this.codeLength), label);
        this.addByte(0);
        this.addByte(0);
    }

    public void addOpcode(int opcode) {
        this.lastInst = this.codeLength;
        this.addByte(opcode);
        if (opcode == 170 || opcode == 171) {
            while(this.codeLength % 4 != 0) {
                this.addByte(0);
            }
        }

    }

    public void addByte(int i) {
        CodeArray.ByteCell p = new CodeArray.ByteCell();
        p.value = (byte)(i & 255);
        p.prev = this.codeTail;
        this.codeTail = p;
        ++this.codeLength;
    }

    public void addShort(int i) {
        this.addByte(i >>> 8);
        this.addByte(i);
    }

    public void addInt(int i) {
        this.addByte(i >>> 24);
        this.addByte(i >>> 16);
        this.addByte(i >>> 8);
        this.addByte(i);
    }

    public void visit_nop(Instruction inst) {
        this.addOpcode(0);
        this.stackHeight += 0;
    }

    public void visit_ldc(Instruction inst) {
        Object operand = inst.operand();
        if (operand == null) {
            this.addOpcode(1);
            ++this.stackHeight;
        } else {
            int index;
            if (operand instanceof Integer) {
                index = (Integer)operand;
                switch(index) {
                    case -1:
                        this.addOpcode(2);
                        break;
                    case 0:
                        this.addOpcode(3);
                        break;
                    case 1:
                        this.addOpcode(4);
                        break;
                    case 2:
                        this.addOpcode(5);
                        break;
                    case 3:
                        this.addOpcode(6);
                        break;
                    case 4:
                        this.addOpcode(7);
                        break;
                    case 5:
                        this.addOpcode(8);
                        break;
                    default:
                        if ((byte)index == index) {
                            this.addOpcode(16);
                            this.addByte(index);
                        } else if ((short)index == index) {
                            this.addOpcode(17);
                            this.addShort(index);
                        } else {
                            index = this.constants.addConstant(3, operand);
                            if (index < 256) {
                                this.addOpcode(18);
                                this.addByte(index);
                            } else {
                                this.addOpcode(19);
                                this.addShort(index);
                            }
                        }
                }

                ++this.stackHeight;
            } else if (operand instanceof Float) {
                float v = (Float)operand;
                if (v == 0.0F) {
                    this.addOpcode(11);
                } else if (v == 1.0F) {
                    this.addOpcode(12);
                } else if (v == 2.0F) {
                    this.addOpcode(13);
                } else {
                    index = this.constants.addConstant(4, operand);
                    if (index < 256) {
                        this.addOpcode(18);
                        this.addByte(index);
                    } else {
                        this.addOpcode(19);
                        this.addShort(index);
                    }
                }

                ++this.stackHeight;
            } else {
                int index;
                if (operand instanceof Long) {
                    long v = (Long)operand;
                    if (v == 0L) {
                        this.addOpcode(9);
                    } else if (v == 1L) {
                        this.addOpcode(10);
                    } else {
                        index = this.constants.addConstant(5, operand);
                        this.addOpcode(20);
                        this.addShort(index);
                    }

                    this.stackHeight += 2;
                } else if (operand instanceof Double) {
                    double v = (Double)operand;
                    if (v == 0.0D) {
                        this.addOpcode(14);
                    } else if (v == 1.0D) {
                        this.addOpcode(15);
                    } else {
                        index = this.constants.addConstant(6, operand);
                        this.addOpcode(20);
                        this.addShort(index);
                    }

                    this.stackHeight += 2;
                } else if (operand instanceof String) {
                    index = this.constants.addConstant(8, operand);
                    this.createLDC(index);
                } else {
                    if (!(operand instanceof Type)) {
                        throw new RuntimeException();
                    }

                    index = this.constants.addConstant(7, operand);
                    this.createLDC(index);
                }
            }
        }

    }

    private void createLDC(int index) {
        if (index < 256) {
            this.addOpcode(18);
            this.addByte(index);
        } else {
            this.addOpcode(19);
            this.addShort(index);
        }

        ++this.stackHeight;
    }

    public void visit_iload(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(21);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(21);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(26);
                    break;
                case 1:
                    this.addOpcode(27);
                    break;
                case 2:
                    this.addOpcode(28);
                    break;
                case 3:
                    this.addOpcode(29);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(21);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(21);
                        this.addShort(index);
                    }
            }

            ++this.stackHeight;
        }
    }

    public void visit_lload(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(22);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(22);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(30);
                    break;
                case 1:
                    this.addOpcode(31);
                    break;
                case 2:
                    this.addOpcode(32);
                    break;
                case 3:
                    this.addOpcode(33);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(22);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(22);
                        this.addShort(index);
                    }
            }

            this.stackHeight += 2;
        }
    }

    public void visit_fload(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(23);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(23);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(34);
                    break;
                case 1:
                    this.addOpcode(35);
                    break;
                case 2:
                    this.addOpcode(36);
                    break;
                case 3:
                    this.addOpcode(37);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(23);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(23);
                        this.addShort(index);
                    }
            }

            ++this.stackHeight;
        }
    }

    public void visit_dload(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(24);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(24);
                this.addShort(index);
            }

            this.stackHeight += 2;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(38);
                    break;
                case 1:
                    this.addOpcode(39);
                    break;
                case 2:
                    this.addOpcode(40);
                    break;
                case 3:
                    this.addOpcode(41);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(24);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(24);
                        this.addShort(index);
                    }
            }

            this.stackHeight += 2;
        }
    }

    public void visit_aload(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(25);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(25);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(42);
                    break;
                case 1:
                    this.addOpcode(43);
                    break;
                case 2:
                    this.addOpcode(44);
                    break;
                case 3:
                    this.addOpcode(45);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(25);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(25);
                        this.addShort(index);
                    }
            }

            ++this.stackHeight;
        }
    }

    public void visit_iaload(Instruction inst) {
        this.addOpcode(46);
        --this.stackHeight;
    }

    public void visit_laload(Instruction inst) {
        this.addOpcode(47);
        this.stackHeight += 0;
    }

    public void visit_faload(Instruction inst) {
        this.addOpcode(48);
        --this.stackHeight;
    }

    public void visit_daload(Instruction inst) {
        this.addOpcode(49);
        this.stackHeight += 0;
    }

    public void visit_aaload(Instruction inst) {
        this.addOpcode(50);
        --this.stackHeight;
    }

    public void visit_baload(Instruction inst) {
        this.addOpcode(51);
        --this.stackHeight;
    }

    public void visit_caload(Instruction inst) {
        this.addOpcode(52);
        --this.stackHeight;
    }

    public void visit_saload(Instruction inst) {
        this.addOpcode(53);
        --this.stackHeight;
    }

    public void visit_istore(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(54);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(54);
                this.addShort(index);
            }

            --this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(59);
                    break;
                case 1:
                    this.addOpcode(60);
                    break;
                case 2:
                    this.addOpcode(61);
                    break;
                case 3:
                    this.addOpcode(62);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(54);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(54);
                        this.addShort(index);
                    }
            }

            --this.stackHeight;
        }
    }

    public void visit_lstore(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(55);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(55);
                this.addShort(index);
            }

            this.stackHeight -= 2;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(63);
                    break;
                case 1:
                    this.addOpcode(64);
                    break;
                case 2:
                    this.addOpcode(65);
                    break;
                case 3:
                    this.addOpcode(66);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(55);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(55);
                        this.addShort(index);
                    }
            }

            this.stackHeight -= 2;
        }
    }

    public void visit_fstore(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(56);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(56);
                this.addShort(index);
            }

            --this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(67);
                    break;
                case 1:
                    this.addOpcode(68);
                    break;
                case 2:
                    this.addOpcode(69);
                    break;
                case 3:
                    this.addOpcode(70);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(56);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(56);
                        this.addShort(index);
                    }
            }

            --this.stackHeight;
        }
    }

    public void visit_dstore(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(57);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(57);
                this.addShort(index);
            }

            this.stackHeight -= 2;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(71);
                    break;
                case 1:
                    this.addOpcode(72);
                    break;
                case 2:
                    this.addOpcode(73);
                    break;
                case 3:
                    this.addOpcode(74);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(57);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(57);
                        this.addShort(index);
                    }
            }

            this.stackHeight -= 2;
        }
    }

    public void visit_astore(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(58);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(58);
                this.addShort(index);
            }

            --this.stackHeight;
        } else {
            switch(index) {
                case 0:
                    this.addOpcode(75);
                    break;
                case 1:
                    this.addOpcode(76);
                    break;
                case 2:
                    this.addOpcode(77);
                    break;
                case 3:
                    this.addOpcode(78);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(58);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(58);
                        this.addShort(index);
                    }
            }

            --this.stackHeight;
        }
    }

    public void visit_iastore(Instruction inst) {
        this.addOpcode(79);
        this.stackHeight -= 3;
    }

    public void visit_lastore(Instruction inst) {
        this.addOpcode(80);
        this.stackHeight -= 4;
    }

    public void visit_fastore(Instruction inst) {
        this.addOpcode(81);
        this.stackHeight -= 3;
    }

    public void visit_dastore(Instruction inst) {
        this.addOpcode(82);
        this.stackHeight -= 4;
    }

    public void visit_aastore(Instruction inst) {
        this.addOpcode(83);
        this.stackHeight -= 3;
    }

    public void visit_bastore(Instruction inst) {
        this.addOpcode(84);
        this.stackHeight -= 3;
    }

    public void visit_castore(Instruction inst) {
        this.addOpcode(85);
        this.stackHeight -= 3;
    }

    public void visit_sastore(Instruction inst) {
        this.addOpcode(86);
        this.stackHeight -= 3;
    }

    public void visit_pop(Instruction inst) {
        this.addOpcode(87);
        --this.stackHeight;
    }

    public void visit_pop2(Instruction inst) {
        this.addOpcode(88);
        this.stackHeight -= 2;
    }

    public void visit_dup(Instruction inst) {
        this.addOpcode(89);
        ++this.stackHeight;
    }

    public void visit_dup_x1(Instruction inst) {
        this.addOpcode(90);
        ++this.stackHeight;
    }

    public void visit_dup_x2(Instruction inst) {
        this.addOpcode(91);
        ++this.stackHeight;
    }

    public void visit_dup2(Instruction inst) {
        this.addOpcode(92);
        this.stackHeight += 2;
    }

    public void visit_dup2_x1(Instruction inst) {
        this.addOpcode(93);
        this.stackHeight += 2;
    }

    public void visit_dup2_x2(Instruction inst) {
        this.addOpcode(94);
        this.stackHeight += 2;
    }

    public void visit_swap(Instruction inst) {
        this.addOpcode(95);
    }

    public void visit_iadd(Instruction inst) {
        this.addOpcode(96);
        --this.stackHeight;
    }

    public void visit_ladd(Instruction inst) {
        this.addOpcode(97);
        this.stackHeight -= 2;
    }

    public void visit_fadd(Instruction inst) {
        this.addOpcode(98);
        --this.stackHeight;
    }

    public void visit_dadd(Instruction inst) {
        this.addOpcode(99);
        this.stackHeight -= 2;
    }

    public void visit_isub(Instruction inst) {
        this.addOpcode(100);
        --this.stackHeight;
    }

    public void visit_lsub(Instruction inst) {
        this.addOpcode(101);
        this.stackHeight -= 2;
    }

    public void visit_fsub(Instruction inst) {
        this.addOpcode(102);
        --this.stackHeight;
    }

    public void visit_dsub(Instruction inst) {
        this.addOpcode(103);
        this.stackHeight -= 2;
    }

    public void visit_imul(Instruction inst) {
        this.addOpcode(104);
        --this.stackHeight;
    }

    public void visit_lmul(Instruction inst) {
        this.addOpcode(105);
        this.stackHeight -= 2;
    }

    public void visit_fmul(Instruction inst) {
        this.addOpcode(106);
        --this.stackHeight;
    }

    public void visit_dmul(Instruction inst) {
        this.addOpcode(107);
        this.stackHeight -= 2;
    }

    public void visit_idiv(Instruction inst) {
        this.addOpcode(108);
        --this.stackHeight;
    }

    public void visit_ldiv(Instruction inst) {
        this.addOpcode(109);
        this.stackHeight -= 2;
    }

    public void visit_fdiv(Instruction inst) {
        this.addOpcode(110);
        --this.stackHeight;
    }

    public void visit_ddiv(Instruction inst) {
        this.addOpcode(111);
        this.stackHeight -= 2;
    }

    public void visit_irem(Instruction inst) {
        this.addOpcode(112);
        --this.stackHeight;
    }

    public void visit_lrem(Instruction inst) {
        this.addOpcode(113);
        this.stackHeight -= 2;
    }

    public void visit_frem(Instruction inst) {
        this.addOpcode(114);
        --this.stackHeight;
    }

    public void visit_drem(Instruction inst) {
        this.addOpcode(115);
        this.stackHeight -= 2;
    }

    public void visit_ineg(Instruction inst) {
        this.addOpcode(116);
        this.stackHeight += 0;
    }

    public void visit_lneg(Instruction inst) {
        this.addOpcode(117);
        this.stackHeight += 0;
    }

    public void visit_fneg(Instruction inst) {
        this.addOpcode(118);
        this.stackHeight += 0;
    }

    public void visit_dneg(Instruction inst) {
        this.addOpcode(119);
        this.stackHeight += 0;
    }

    public void visit_ishl(Instruction inst) {
        this.addOpcode(120);
        --this.stackHeight;
    }

    public void visit_lshl(Instruction inst) {
        this.addOpcode(121);
        --this.stackHeight;
    }

    public void visit_ishr(Instruction inst) {
        this.addOpcode(122);
        --this.stackHeight;
    }

    public void visit_lshr(Instruction inst) {
        this.addOpcode(123);
        --this.stackHeight;
    }

    public void visit_iushr(Instruction inst) {
        this.addOpcode(124);
        --this.stackHeight;
    }

    public void visit_lushr(Instruction inst) {
        this.addOpcode(125);
        --this.stackHeight;
    }

    public void visit_iand(Instruction inst) {
        this.addOpcode(126);
        --this.stackHeight;
    }

    public void visit_land(Instruction inst) {
        this.addOpcode(127);
        this.stackHeight -= 2;
    }

    public void visit_ior(Instruction inst) {
        this.addOpcode(128);
        --this.stackHeight;
    }

    public void visit_lor(Instruction inst) {
        this.addOpcode(129);
        this.stackHeight -= 2;
    }

    public void visit_ixor(Instruction inst) {
        this.addOpcode(130);
        --this.stackHeight;
    }

    public void visit_lxor(Instruction inst) {
        this.addOpcode(131);
        this.stackHeight -= 2;
    }

    public void visit_iinc(Instruction inst) {
        IncOperand operand = (IncOperand)inst.operand();
        int index = operand.var().index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        int incr = operand.incr();
        if (index < 256 && (byte)incr == incr) {
            this.addOpcode(132);
            this.addByte(index);
            this.addByte(incr);
        } else {
            this.addOpcode(196);
            this.addByte(132);
            this.addShort(index);
            this.addShort(incr);
        }

        this.stackHeight += 0;
    }

    public void visit_i2l(Instruction inst) {
        this.addOpcode(133);
        ++this.stackHeight;
    }

    public void visit_i2f(Instruction inst) {
        this.addOpcode(134);
        this.stackHeight += 0;
    }

    public void visit_i2d(Instruction inst) {
        this.addOpcode(135);
        ++this.stackHeight;
    }

    public void visit_l2i(Instruction inst) {
        this.addOpcode(136);
        --this.stackHeight;
    }

    public void visit_l2f(Instruction inst) {
        this.addOpcode(137);
        --this.stackHeight;
    }

    public void visit_l2d(Instruction inst) {
        this.addOpcode(138);
        this.stackHeight += 0;
    }

    public void visit_f2i(Instruction inst) {
        this.addOpcode(139);
        this.stackHeight += 0;
    }

    public void visit_f2l(Instruction inst) {
        this.addOpcode(140);
        ++this.stackHeight;
    }

    public void visit_f2d(Instruction inst) {
        this.addOpcode(141);
        ++this.stackHeight;
    }

    public void visit_d2i(Instruction inst) {
        this.addOpcode(142);
        --this.stackHeight;
    }

    public void visit_d2l(Instruction inst) {
        this.addOpcode(143);
        this.stackHeight += 0;
    }

    public void visit_d2f(Instruction inst) {
        this.addOpcode(144);
        --this.stackHeight;
    }

    public void visit_i2b(Instruction inst) {
        this.addOpcode(145);
        this.stackHeight += 0;
    }

    public void visit_i2c(Instruction inst) {
        this.addOpcode(146);
        this.stackHeight += 0;
    }

    public void visit_i2s(Instruction inst) {
        this.addOpcode(147);
        this.stackHeight += 0;
    }

    public void visit_lcmp(Instruction inst) {
        this.addOpcode(148);
        this.stackHeight -= 3;
    }

    public void visit_fcmpl(Instruction inst) {
        this.addOpcode(149);
        --this.stackHeight;
    }

    public void visit_fcmpg(Instruction inst) {
        this.addOpcode(150);
        --this.stackHeight;
    }

    public void visit_dcmpl(Instruction inst) {
        this.addOpcode(151);
        this.stackHeight -= 3;
    }

    public void visit_dcmpg(Instruction inst) {
        this.addOpcode(152);
        this.stackHeight -= 3;
    }

    public void visit_ifeq(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(154);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(153);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifne(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(153);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(154);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_iflt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(156);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(155);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifge(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(155);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(156);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifgt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(158);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(157);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifle(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(157);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(158);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_if_icmpeq(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(160);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(159);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmpne(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(159);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(160);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmplt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(162);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(161);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmpge(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(161);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(162);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmpgt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(164);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(163);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmple(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(163);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(164);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_acmpeq(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(166);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(165);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_acmpne(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(165);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(166);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_goto(Instruction inst) {
        if (this.longBranch) {
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
        } else {
            this.addOpcode(167);
            this.addBranch((Label)inst.operand());
        }

        this.stackHeight += 0;
    }

    public void visit_jsr(Instruction inst) {
        if (this.longBranch) {
            this.addOpcode(201);
            this.addLongBranch((Label)inst.operand());
        } else {
            this.addOpcode(168);
            this.addBranch((Label)inst.operand());
        }

        ++this.stackHeight;
    }

    public void visit_ret(Instruction inst) {
        int index = ((LocalVariable)inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (index < 256) {
            this.addOpcode(169);
            this.addByte(index);
        } else {
            this.addOpcode(196);
            this.addByte(169);
            this.addShort(index);
        }

        this.stackHeight += 0;
    }

    public void visit_switch(Instruction inst) {
        Switch sw = (Switch)inst.operand();
        int[] values = sw.values();
        Label[] targets = sw.targets();
        if (values.length == 0) {
            if (this.longBranch) {
                this.addOpcode(87);
                this.addOpcode(200);
                this.addLongBranch(sw.defaultTarget());
            } else {
                this.addOpcode(87);
                this.addOpcode(167);
                this.addBranch(sw.defaultTarget());
            }
        } else {
            int i;
            if (sw.hasContiguousValues()) {
                this.addOpcode(170);
                this.addLongBranch(sw.defaultTarget());
                this.addInt(values[0]);
                this.addInt(values[values.length - 1]);

                for(i = 0; i < targets.length; ++i) {
                    this.addLongBranch(targets[i]);
                }
            } else {
                this.addOpcode(171);
                this.addLongBranch(sw.defaultTarget());
                this.addInt(values.length);

                for(i = 0; i < targets.length; ++i) {
                    this.addInt(values[i]);
                    this.addLongBranch(targets[i]);
                }
            }
        }

        --this.stackHeight;
    }

    public void visit_ireturn(Instruction inst) {
        this.addOpcode(172);
        this.stackHeight = 0;
    }

    public void visit_lreturn(Instruction inst) {
        this.addOpcode(173);
        this.stackHeight = 0;
    }

    public void visit_freturn(Instruction inst) {
        this.addOpcode(174);
        this.stackHeight = 0;
    }

    public void visit_dreturn(Instruction inst) {
        this.addOpcode(175);
        this.stackHeight = 0;
    }

    public void visit_areturn(Instruction inst) {
        this.addOpcode(176);
        this.stackHeight = 0;
    }

    public void visit_return(Instruction inst) {
        this.addOpcode(177);
        this.stackHeight = 0;
    }

    public void visit_getstatic(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(178);
        this.addShort(index);
        Type type = ((MemberRef)inst.operand()).nameAndType().type();
        this.stackHeight += type.stackHeight();
    }

    public void visit_putstatic(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(179);
        this.addShort(index);
        Type type = ((MemberRef)inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight();
    }

    public void visit_putstatic_nowb(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(205);
        this.addShort(index);
        Type type = ((MemberRef)inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight();
    }

    public void visit_getfield(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(180);
        this.addShort(index);
        Type type = ((MemberRef)inst.operand()).nameAndType().type();
        this.stackHeight += type.stackHeight() - 1;
    }

    public void visit_putfield(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(181);
        this.addShort(index);
        Type type = ((MemberRef)inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight() + 1;
    }

    public void visit_putfield_nowb(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(204);
        this.addShort(index);
        Type type = ((MemberRef)inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight() + 1;
    }

    public void visit_invokevirtual(Instruction inst) {
        int index = this.constants.addConstant(10, inst.operand());
        this.addOpcode(182);
        this.addShort(index);
        MemberRef method = (MemberRef)inst.operand();
        Type type = method.nameAndType().type();
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight() - 1;
    }

    public void visit_invokespecial(Instruction inst) {
        int index = this.constants.addConstant(10, inst.operand());
        this.addOpcode(183);
        this.addShort(index);
        MemberRef method = (MemberRef)inst.operand();
        Type type = method.nameAndType().type();
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight() - 1;
    }

    public void visit_invokestatic(Instruction inst) {
        int index = this.constants.addConstant(10, inst.operand());
        this.addOpcode(184);
        this.addShort(index);
        MemberRef method = (MemberRef)inst.operand();
        Type type = method.nameAndType().type();
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight();
    }

    public void visit_invokeinterface(Instruction inst) {
        int index = this.constants.addConstant(11, inst.operand());
        MemberRef method = (MemberRef)this.constants.constantAt(index);
        Type type = method.nameAndType().type();
        this.addOpcode(185);
        this.addShort(index);
        this.addByte(type.stackHeight() + 1);
        this.addByte(0);
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight() - 1;
    }

    public void visit_new(Instruction inst) {
        int index = this.constants.addConstant(7, inst.operand());
        this.addOpcode(187);
        this.addShort(index);
        ++this.stackHeight;
    }

    public void visit_newarray(Instruction inst) {
        Type type = (Type)inst.operand();
        if (type.isReference()) {
            int index = this.constants.addConstant(7, type);
            this.addOpcode(189);
            this.addShort(index);
        } else {
            this.addOpcode(188);
            this.addByte(type.typeCode());
        }

        this.stackHeight += 0;
    }

    public void visit_arraylength(Instruction inst) {
        this.addOpcode(190);
        this.stackHeight += 0;
    }

    public void visit_athrow(Instruction inst) {
        this.addOpcode(191);
        this.stackHeight = 0;
    }

    public void visit_checkcast(Instruction inst) {
        int index = this.constants.addConstant(7, inst.operand());
        this.addOpcode(192);
        this.addShort(index);
        this.stackHeight += 0;
    }

    public void visit_instanceof(Instruction inst) {
        int index = this.constants.addConstant(7, inst.operand());
        this.addOpcode(193);
        this.addShort(index);
        this.stackHeight += 0;
    }

    public void visit_monitorenter(Instruction inst) {
        this.addOpcode(194);
        --this.stackHeight;
    }

    public void visit_monitorexit(Instruction inst) {
        this.addOpcode(195);
        --this.stackHeight;
    }

    public void visit_multianewarray(Instruction inst) {
        MultiArrayOperand operand = (MultiArrayOperand)inst.operand();
        Type type = operand.type();
        int dim = operand.dimensions();
        int index = this.constants.addConstant(7, type);
        this.addOpcode(197);
        this.addShort(index);
        this.addByte(dim);
        this.stackHeight += 1 - dim;
    }

    public void visit_ifnull(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(199);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(198);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifnonnull(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(198);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label)inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(199);
            this.addBranch((Label)inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_rc(Instruction inst) {
        Integer operand = (Integer)inst.operand();
        this.addOpcode(237);
        this.addByte(operand);
        this.stackHeight += 0;
    }

    public void visit_aswizzle(Instruction inst) {
        this.addOpcode(240);
        this.stackHeight -= 2;
    }

    public void visit_aswrange(Instruction inst) {
        this.addOpcode(241);
        this.stackHeight -= 3;
    }

    public void visit_aupdate(Instruction inst) {
        Integer operand = (Integer)inst.operand();
        this.addOpcode(238);
        this.addByte(operand);
        this.stackHeight += 0;
    }

    public void visit_supdate(Instruction inst) {
        Integer operand = (Integer)inst.operand();
        this.addOpcode(239);
        this.addByte(operand);
        this.stackHeight += 0;
    }

    class ByteCell {
        byte value;
        CodeArray.ByteCell prev;

        ByteCell() {
        }
    }

    class HeightRecord {
        Label label;
        int height;

        public HeightRecord(Label label, int height) {
            this.label = label;
            this.height = height;
        }
    }
}


package com.d0klabs.cryptowalt.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class ClassEditor {
    private ConstantPool constants;
    private ClassInfo classInfo;
    private Type type;
    private Type superclass;
    private Type[] interfaces;
    private EditorContext context;
    private boolean dirty;
    public ClassEditor(EditorContext context, ClassInfo classInfo) {
        this.context = context;
        this.classInfo = classInfo;
        this.dirty = false;
        this.constants = new ConstantPool(classInfo.constants());
        int index = classInfo.classIndex();
        this.type = (Type)this.constants.constantAt(index);
        index = classInfo.superclassIndex();
        this.superclass = (Type)this.constants.constantAt(index);
        int[] ifs = classInfo.interfaceIndices();
        this.interfaces = new Type[ifs.length];

        for(int i = 0; i < ifs.length; ++i) {
            this.interfaces[i] = (Type)this.constants.constantAt(ifs[i]);
        }
        this.setDirty(false);
    }

    public ClassEditor(EditorContext context, int modifiers, String className, Type superType, Type[] interfaces) {
        if (className == null) {
            String s = "Cannot have a null class name";
            throw new IllegalArgumentException("Cannot have a null class name");
        } else {
            if (superType == null) {
                superType = Type.OBJECT;
            }

            if (interfaces == null) {
                interfaces = new Type[0];
            }

            this.context = context;
            this.superclass = superType;
            this.interfaces = interfaces;
            ConstantPool cp = new ConstantPool();
            this.constants = cp;
            this.type = Type.getType(Type.classDescriptor(className));
            int classNameIndex = cp.getClassIndex(this.type);
            int superTypeIndex = cp.getClassIndex(superType);
            int[] interfaceIndexes = new int[interfaces.length];

            for(int i = 0; i < interfaces.length; ++i) {
                interfaceIndexes[i] = cp.getClassIndex(interfaces[i]);
            }

            this.classInfo = context.newClassInfo(modifiers, classNameIndex, superTypeIndex, interfaceIndexes, cp.getConstantsList());
            this.dirty = true;
        }
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public String name() {
        return this.classInfo().name();
    }

    public EditorContext context() {
        return this.context;
    }

    public ClassInfo classInfo() {
        return this.classInfo;
    }

    public boolean isPublic() {
        return (this.classInfo.modifiers() & 1) != 0;
    }

    public boolean isPrivate() {
        return (this.classInfo.modifiers() & 2) != 0;
    }

    public boolean isProtected() {
        return (this.classInfo.modifiers() & 4) != 0;
    }

    public boolean isStatic() {
        return (this.classInfo.modifiers() & 8) != 0;
    }

    public boolean isFinal() {
        return (this.classInfo.modifiers() & 16) != 0;
    }

    public boolean isSuper() {
        return (this.classInfo.modifiers() & 32) != 0;
    }

    public boolean isAbstract() {
        return (this.classInfo.modifiers() & 1024) != 0;
    }

    public boolean isInterface() {
        return (this.classInfo.modifiers() & 512) != 0;
    }

    public void setPublic(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 1;
        } else {
            modifiers &= -2;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setPrivate(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 2;
        } else {
            modifiers &= -3;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setProtected(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 4;
        } else {
            modifiers &= -5;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setStatic(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 8;
        } else {
            modifiers &= -9;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setFinal(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 16;
        } else {
            modifiers &= -17;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setSuper(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 32;
        } else {
            modifiers &= -33;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setAbstract(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 1024;
        } else {
            modifiers &= -1025;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setInterface(boolean flag) {
        int modifiers = this.classInfo.modifiers();
        if (flag) {
            modifiers |= 512;
        } else {
            modifiers &= -513;
        }

        this.classInfo.setModifiers(modifiers);
        this.setDirty(true);
    }

    public void setType(Type type) {
        this.type = type;
        this.setDirty(true);
    }

    public Type type() {
        return this.type;
    }

    public Type superclass() {
        return this.superclass;
    }

    public void addInterface(Class interfaceClass) {
        if (!interfaceClass.isInterface()) {
            String s = "Cannot add non-interface type: " + interfaceClass.getName();
            throw new IllegalArgumentException(s);
        } else {
            this.addInterface(Type.getType(interfaceClass));
        }
    }

    public void addInterface(Type interfaceType) {
        Type[] interfaces = new Type[this.interfaces.length + 1];

        for(int i = 0; i < this.interfaces.length; ++i) {
            interfaces[i] = this.interfaces[i];
        }

        interfaces[interfaces.length - 1] = interfaceType;
        this.setInterfaces(interfaces);
    }

    public void setInterfaces(Type[] interfaces) {
        this.interfaces = interfaces;
        this.setDirty(true);
    }

    public Type[] interfaces() {
        return this.interfaces;
    }

    public int modifiers() {
        return this.classInfo.modifiers();
    }

    public FieldInfo[] fields() {
        return this.classInfo.fields();
    }

    public MethodInfo[] methods() {
        return this.classInfo.methods();
    }

    public ConstantPool constants() {
        return this.constants;
    }

    public void commit() {
        this.commitOnly((Set)null, (Set)null);
    }

    public void commitOnly(Set methods, Set fields) {
        this.classInfo.setClassIndex(this.constants.addConstant(7, this.type));
        this.classInfo.setSuperclassIndex(this.constants.addConstant(7, this.superclass));
        int[] ifs = new int[this.interfaces.length];

        for(int i = 0; i < ifs.length; ++i) {
            ifs[i] = this.constants.addConstant(7, this.interfaces[i]);
        }

        this.classInfo.setInterfaceIndices(ifs);
        this.classInfo.setConstants(this.constants.constants());
        this.classInfo.commitOnly(methods, fields);
        this.setDirty(false);
    }

    public void visit(EditorVisitor visitor) {
        visitor.visitClassEditor(this);
        EditorContext context = this.context();
        FieldInfo[] fields = this.fields();

        for(int i = 0; i < fields.length; ++i) {
            EditorContext.FieldEditor fieldEditor = context.editField(fields[i]);
            visitor.visitFieldEditor(fieldEditor);
            context.release(fields[i]);
        }

        ArrayList regularMethods = new ArrayList();
        MethodInfo[] methods = this.methods();

        for(int i = 0; i < methods.length; ++i) {
            MethodEditor methodEditor = context.editMethod(methods[i]);
            if (methodEditor.name().charAt(0) != '<') {
                regularMethods.add(methods[i]);
            } else {
                visitor.visitMethodEditor(methodEditor);
            }

            context.release(methods[i]);
        }

        Iterator iter = regularMethods.iterator();

        while(iter.hasNext()) {
            MethodInfo info = (MethodInfo)iter.next();
            MethodEditor me = context.editMethod(info);
            visitor.visitMethodEditor(me);
            context.release(info);
        }

    }

    public boolean equals(Object o) {
        if (o instanceof ClassEditor) {
            ClassEditor other = (ClassEditor)o;
            return other.type().equals(this.type());
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.name().hashCode();
    }

    public String toString() {
        return this.type().toString();
    }

    public void setSuperclass(Type newSuperclass) {
        this.superclass = newSuperclass;
    }
}


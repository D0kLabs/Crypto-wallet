package com.d0klabs.cryptowalt.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
public class ConstantPool {
    private Map constantIndices = new HashMap();
    ResizeableArrayList constants;
    ResizeableArrayList resolved;

    public ConstantPool(ClassInfo.Constant[] c) {
        this.constants = new ResizeableArrayList(c.length);
        this.resolved = new ResizeableArrayList(c.length);

        for(int i = 0; i < c.length; ++i) {
            this.constants.add(c[i]);
            this.resolved.add((Object)null);
            if (c[i] != null) {
                this.constantIndices.put(c[i], new Integer(i));
            }
        }

    }

    public ConstantPool() {
        this.constants = new ResizeableArrayList();
        this.resolved = new ResizeableArrayList();
    }

    public Object constantAt(int idx) {
        if (idx == 0) {
            return null;
        } else {
            Object value = this.resolved.get(idx);
            if (value != null) {
                return value;
            } else {
                ClassInfo.Constant c = (ClassInfo.Constant)this.constants.get(idx);
                if (c == null) {
                    return null;
                } else {
                    value = c.value();
                    if (value == null) {
                        return null;
                    } else {
                        int[] v;
                        String name;
                        int index;
                        switch(c.tag()) {
                            case 7:
                                index = (Integer)value;
                                name = (String)this.constantAt(index);
                                value = Type.getType(Type.classDescriptor(name));
                                break;
                            case 8:
                                index = (Integer)value;
                                value = this.constantAt(index);
                                break;
                            case 9:
                            case 10:
                            case 11:
                                v = (int[])value;
                                Type clazz = (Type)this.constantAt(v[0]);
                                NameAndType nameAndType = (NameAndType)this.constantAt(v[1]);
                                value = new MemberRef(clazz, nameAndType);
                                break;
                            case 12:
                                v = (int[])value;
                                name = (String)this.constantAt(v[0]);
                                String type = (String)this.constantAt(v[1]);
                                value = new NameAndType(name, Type.getType(type));
                        }

                        this.resolved.ensureSize(idx + 1);
                        this.resolved.set(idx, value);
                        return value;
                    }
                }
            }
        }
    }

    public int numConstants() {
        return this.constants.size();
    }

    public int constantTag(int index) {
        if (index > 0 && index < this.constants.size()) {
            ClassInfo.Constant c = (ClassInfo.Constant)this.constants.get(index);
            if (c != null) {
                return c.tag();
            }
        }

        return 1;
    }

    public int constantIndex(int tag, Object value) {
        return this.addConstant(tag, value);
    }

    public int getClassIndex(Class c) {
        return this.addConstant(7, Type.getType(c));
    }

    public int getIntegerIndex(Integer i) {
        return this.addConstant(3, i);
    }

    public int getFloatIndex(Float f) {
        return this.addConstant(4, f);
    }

    public int getLongIndex(Long l) {
        return this.addConstant(5, l);
    }

    public int getDoubleIndex(Double d) {
        return this.addConstant(6, d);
    }

    public int getClassIndex(Type type) {
        this.getTypeIndex(type);
        return this.addConstant(7, type);
    }

    public int getTypeIndex(Type type) {
        return this.addConstant(1, type.descriptor());
    }

    public int getStringIndex(String s) {
        return this.addConstant(8, s);
    }

    public int getMemberRefIndex(MemberRef ref) {
        return this.addConstant(9, ref);
    }

    public int getNameAndTypeIndex(NameAndType nat) {
        return this.addConstant(12, nat);
    }

    public int getUTF8Index(String s) {
        return this.addConstant(1, s);
    }

    public int addConstant(int tag, Object value) {
        if (value == null) {
            return 0;
        } else {
            ClassInfo.Constant c;
            int[] v;
            int index;
            switch(tag) {
                case 1:
                    String s = (String)value;
                    c = new ClassInfo.Constant(tag, s.intern());
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                default:
                    c = new ClassInfo.Constant(tag, value);
                    break;
                case 7:
                    index = this.addConstant(1, ((Type)value).className());
                    c = new ClassInfo.Constant(7, new Integer(index));
                    break;
                case 8:
                    index = this.addConstant(1, value);
                    c = new ClassInfo.Constant(8, new Integer(index));
                    break;
                case 9:
                case 10:
                case 11:
                    v = new int[]{this.addConstant(7, ((MemberRef)value).declaringClass()), this.addConstant(12, ((MemberRef)value).nameAndType())};
                    c = new ClassInfo.Constant(tag, v);
                    break;
                case 12:
                    v = new int[]{this.addConstant(1, ((NameAndType)value).name()), this.addConstant(1, ((NameAndType)value).type().descriptor())};
                    c = new ClassInfo.Constant(tag, v);
            }

            index = (Integer)this.constantIndices.get(c);
            if (((Integer) index) == null) {
                index = new Integer(this.constants.size());
                this.constantIndices.put(c, index);
                this.constants.add(c);
                this.resolved.add(value);
                if (tag == 5 || tag == 6) {
                    this.constants.add((Object)null);
                    this.resolved.add((Object)null);
                }
            }

            return index;
        }
    }

    public ClassInfo.Constant[] constants() {
        Object[] a = this.constants.toArray();
        ClassInfo.Constant[] array = new ClassInfo.Constant[a.length];
        System.arraycopy(a, 0, array, 0, a.length);
        return array;
    }

    public List getConstantsList() {
        return Collections.unmodifiableList(this.constants);
    }
}


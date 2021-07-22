package com.d0klabs.cryptowalt.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantPool extends InstructionVisitor.ConstantPool {
    private Map constantIndices = new HashMap();
    ResizeableArrayList constants;
    ResizeableArrayList resolved;

    public ConstantPool(ClassInfo.Constant[] c) {
        this.constants = new ResizeableArrayList(c.length);
        this.resolved = new ResizeableArrayList(c.length);

        for (int i = 0; i < c.length; ++i) {
            this.constants.add(c[i]);
            this.resolved.add((Object) null);
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
                ClassInfo.Constant c = (ClassInfo.Constant) this.constants.get(idx);
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
                        switch (c.tag()) {
                            case 7:
                                index = (Integer) value;
                                name = (String) this.constantAt(index);
                                value = Type.getType(Type.classDescriptor(name));
                                break;
                            case 8:
                                index = (Integer) value;
                                value = this.constantAt(index);
                                break;
                            case 9:
                            case 10:
                            case 11:
                                v = (int[]) value;
                                Type clazz = (Type) this.constantAt(v[0]);
                                NameAndType nameAndType = (NameAndType) this.constantAt(v[1]);
                                value = new MemberRef(clazz, nameAndType);
                                break;
                            case 12:
                                v = (int[]) value;
                                name = (String) this.constantAt(v[0]);
                                String type = (String) this.constantAt(v[1]);
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
            ClassInfo.Constant c = (ClassInfo.Constant) this.constants.get(index);
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
            switch (tag) {
                case 1:
                    String s = (String) value;
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
                    index = this.addConstant(1, ((Type) value).className());
                    c = new ClassInfo.Constant(7, new Integer(index));
                    break;
                case 8:
                    index = this.addConstant(1, value);
                    c = new ClassInfo.Constant(8, new Integer(index));
                    break;
                case 9:
                case 10:
                case 11:
                    v = new int[]{this.addConstant(7, ((MemberRef) value).declaringClass()), this.addConstant(12, ((MemberRef) value).nameAndType())};
                    c = new ClassInfo.Constant(tag, v);
                    break;
                case 12:
                    v = new int[]{this.addConstant(1, ((NameAndType) value).name()), this.addConstant(1, ((NameAndType) value).type().descriptor())};
                    c = new ClassInfo.Constant(tag, v);
            }

            index = (Integer) this.constantIndices.get(c);
            if (((Integer) index) == null) {
                index = new Integer(this.constants.size());
                this.constantIndices.put(c, index);
                this.constants.add(c);
                this.resolved.add(value);
                if (tag == 5 || tag == 6) {
                    this.constants.add((Object) null);
                    this.resolved.add((Object) null);
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

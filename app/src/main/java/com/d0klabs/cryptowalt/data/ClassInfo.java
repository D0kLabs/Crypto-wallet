package com.d0klabs.cryptowalt.data;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Set;

public interface ClassInfo {
    ClassInfoLoader loader();

    String name();

    int classIndex();

    int superclassIndex();

    int[] interfaceIndices();

    void setClassIndex(int var1);

    void setSuperclassIndex(int var1);

    void setInterfaceIndices(int[] var1);

    void setModifiers(int var1);

    int modifiers();

    FieldInfo[] fields();

    MethodInfo[] methods();

    void setMethods(MethodInfo[] var1);

    Constant[] constants();

    void setConstants(Constant[] var1);

    void commit();

    void commitOnly(Set var1, Set var2);

    FieldInfo addNewField(int var1, int var2, int var3);

    FieldInfo addNewField(int var1, int var2, int var3, int var4, int var5);

    void deleteField(int var1);

    void deleteMethod(int var1, int var2);

    MethodInfo addNewMethod(int var1, int var2, int var3, int var4, int[] var5, int var6);

    void print(PrintStream var1);

    void print(PrintWriter var1);

    String toString();

    public final class Constant {
        private int tag;
        private Object value;
        public static final byte CLASS = 7;
        public static final byte FIELD_REF = 9;
        public static final byte METHOD_REF = 10;
        public static final byte STRING = 8;
        public static final byte INTEGER = 3;
        public static final byte FLOAT = 4;
        public static final byte LONG = 5;
        public static final byte DOUBLE = 6;
        public static final byte INTERFACE_METHOD_REF = 11;
        public static final byte NAME_AND_TYPE = 12;
        public static final byte UTF8 = 1;

        public Constant(int tag, Object value) {
            this.tag = tag;
            this.value = value;
        }

        public final int tag() {
            return this.tag;
        }

        public final Object value() {
            return this.value;
        }

        public int hashCode() {
            switch(this.tag) {
                case 1:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                    return this.tag ^ this.value.hashCode();
                case 2:
                default:
                    return this.tag;
                case 9:
                case 10:
                case 11:
                case 12:
                    return this.tag ^ ((int[])this.value)[0] ^ ((int[])this.value)[1];
            }
        }

        public boolean equals(Object other) {
            if (!(other instanceof Constant)) {
                return false;
            } else {
                Constant c = (Constant)other;
                if (this.tag != c.tag) {
                    return false;
                } else {
                    switch(this.tag) {
                        case 1:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                            return this.value.equals(c.value);
                        case 2:
                        default:
                            return false;
                        case 9:
                        case 10:
                        case 11:
                        case 12:
                            return ((int[])this.value)[0] == ((int[])c.value)[0] && ((int[])this.value)[1] == ((int[])c.value)[1];
                    }
                }
            }
        }

        public String toString() {
            switch(this.tag) {
                case 1:
                    StringBuffer sb = new StringBuffer();
                    String s = (String)this.value;

                    for(int i = 0; i < s.length(); ++i) {
                        char c = s.charAt(i);
                        if (!Character.isWhitespace(c) && (' ' > c || c > '~')) {
                            sb.append("\\u");
                            sb.append(Integer.toHexString(c));
                        } else {
                            sb.append(c);
                        }

                        if (sb.length() > 50) {
                            sb.append("...");
                            break;
                        }
                    }

                    return "Utf8 '" + sb.toString() + "'";
                case 2:
                default:
                    return "unknown constant";
                case 3:
                    return "Integer " + this.value.toString();
                case 4:
                    return "Float " + this.value.toString();
                case 5:
                    return "Long " + this.value.toString();
                case 6:
                    return "Double " + this.value.toString();
                case 7:
                    return "Class " + this.value.toString();
                case 8:
                    return "String " + this.value.toString();
                case 9:
                    return "Fieldref " + ((int[])this.value)[0] + " " + ((int[])this.value)[1];
                case 10:
                    return "Methodref " + ((int[])this.value)[0] + " " + ((int[])this.value)[1];
                case 11:
                    return "InterfaceMethodref " + ((int[])this.value)[0] + " " + ((int[])this.value)[1];
                case 12:
                    return "NameandType " + ((int[])this.value)[0] + " " + ((int[])this.value)[1];
            }
        }
    }

}

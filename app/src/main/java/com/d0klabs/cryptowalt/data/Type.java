package com.d0klabs.cryptowalt.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class Type {
    public static final char ARRAY_CHAR = '[';
    public static final char BOOLEAN_CHAR = 'Z';
    public static final char BYTE_CHAR = 'B';
    public static final char CHARACTER_CHAR = 'C';
    public static final char CLASS_CHAR = 'L';
    public static final char DOUBLE_CHAR = 'D';
    public static final char FLOAT_CHAR = 'F';
    public static final char INTEGER_CHAR = 'I';
    public static final char LONG_CHAR = 'J';
    public static final char SHORT_CHAR = 'S';
    public static final char VOID_CHAR = 'V';
    public static final char ADDRESS_CHAR = 'A';
    public static final int BOOLEAN_CODE = 4;
    public static final int CHARACTER_CODE = 5;
    public static final int FLOAT_CODE = 6;
    public static final int DOUBLE_CODE = 7;
    public static final int BYTE_CODE = 8;
    public static final int SHORT_CODE = 9;
    public static final int INTEGER_CODE = 10;
    public static final int LONG_CODE = 11;
    public static final Type OBJECT = getType("Ljava/lang/Object;");
    public static final Type STRING = getType("Ljava/lang/String;");
    public static final Type CLASS = getType("Ljava/lang/Class;");
    public static final Type THROWABLE = getType("Ljava/lang/Throwable;");
    public static final Type CLONEABLE = getType("Ljava/lang/Cloneable;");
    public static final Type SERIALIZABLE = getType("Ljava/lang/Serializable;");
    public static final Type NULL = getType("Lnull!;");
    public static final Type BOOLEAN = getType('Z');
    public static final Type CHARACTER = getType('C');
    public static final Type FLOAT = getType('F');
    public static final Type DOUBLE = getType('D');
    public static final Type BYTE = getType('B');
    public static final Type SHORT = getType('S');
    public static final Type INTEGER = getType('I');
    public static final Type LONG = getType('J');
    public static final Type VOID = getType('V');
    public static final Type ADDRESS = getType('A');
    public static boolean PRINT_TRUNCATED = false;
    private static Map types = new TreeMap();
    String desc;
    Type[] paramTypes;
    Type returnType;
    private static Comparator comparator = null;
    private static final Comparator printComparator = null;

    public static Type getType(String desc) {
        Type type = (Type)types.get(desc);
        if (type == null) {
            type = new Type(desc);
            types.put(desc, type);
        }

        return type;
    }

    public static Type getType(Class c) {
        if (c.equals(Boolean.TYPE)) {
            return BOOLEAN;
        } else if (c.equals(Character.TYPE)) {
            return CHARACTER;
        } else if (c.equals(Float.TYPE)) {
            return FLOAT;
        } else if (c.equals(Double.TYPE)) {
            return DOUBLE;
        } else if (c.equals(Byte.TYPE)) {
            return BYTE;
        } else if (c.equals(Short.TYPE)) {
            return SHORT;
        } else if (c.equals(Integer.TYPE)) {
            return INTEGER;
        } else if (c.equals(Long.TYPE)) {
            return LONG;
        } else if (c.equals(Void.TYPE)) {
            return VOID;
        } else {
            String name = c.getName().replace('.', '/');
            if (!c.isArray()) {
                name = "L" + name + ";";
            }

            return getType(name);
        }
    }

    public static Type getType(Type[] paramTypes, Type returnType) {
        StringBuffer sb = new StringBuffer("(");

        for(int i = 0; i < paramTypes.length; ++i) {
            sb.append(paramTypes[i].descriptor());
        }

        sb.append(")");
        sb.append(returnType.descriptor());
        return getType(sb.toString());
    }

    private Type(String desc) {
        ArrayList types = new ArrayList();
        String t = "";
        boolean method = false;
        this.desc = desc.intern();
        int state = 0;
        int i = 0;
        if (desc.charAt(i) == '(') {
            method = true;
            ++i;
        }

        for(; i < desc.length(); ++i) {
            switch(state) {
                case 0:
                    switch(desc.charAt(i)) {
                        case ')':
                            if (!method) {
                                String s = "Invalid char in type descriptor: )\nDescriptor was: " + desc;
                                throw new IllegalArgumentException(s);
                            }
                            continue;
                        case 'B':
                        case 'C':
                        case 'D':
                        case 'F':
                        case 'I':
                        case 'J':
                        case 'S':
                        case 'V':
                        case 'Z':
                            types.add(t + desc.charAt(i));
                            t = "";
                            continue;
                        case 'L':
                            t = t + desc.charAt(i);
                            state = 1;
                            continue;
                        case '[':
                            t = t + desc.charAt(i);
                            continue;
                        default:
                            throw new IllegalArgumentException("Invalid char in type descriptor (" + desc + "): " + desc.charAt(i));
                    }
                case 1:
                    t = t + desc.charAt(i);
                    if (desc.charAt(i) == ';') {
                        types.add(t);
                        t = "";
                        state = 0;
                    }
            }
        }

        if (method) {
            int sizeM1 = types.size() - 1;
            this.returnType = getType((String)types.get(sizeM1));
            this.paramTypes = new Type[sizeM1];

            for(i = 0; i < sizeM1; ++i) {
                String s = (String)types.get(i);
                if (s != null) {
                    this.paramTypes[i] = getType(s);
                }
            }
        } else if (types.size() != 1) {
            throw new IllegalArgumentException("More than one type in the type descriptor: " + desc);
        }

    }

    public static Type getType(int typeCode) {
        String desc = null;
        switch(typeCode) {
            case 4:
                desc = "Z";
                break;
            case 5:
                desc = "C";
                break;
            case 6:
                desc = "F";
                break;
            case 7:
                desc = "D";
                break;
            case 8:
                desc = "B";
                break;
            case 9:
                desc = "S";
                break;
            case 10:
                desc = "I";
                break;
            case 11:
                desc = "J";
                break;
            default:
                throw new IllegalArgumentException("Invalid type code: " + typeCode);
        }

        Type type = (Type)types.get(desc);
        if (type == null) {
            type = new Type();
            type.setDesc(desc);
            types.put(desc, type);
        }

        return type;
    }

    private Type() {
        this.desc = null;
    }

    private void setDesc(String desc) {
        this.desc = desc;
    }

    public static Type getType(char typeChar) {
        String desc = null;
        switch(typeChar) {
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'V':
            case 'Z':
                desc = "" + typeChar;
                desc = desc.intern();
                Type type = (Type)types.get(desc);
                if (type == null) {
                    type = new Type();
                    type.setDesc(desc);
                    types.put(desc, type);
                }

                return type;
            default:
                throw new IllegalArgumentException("Invalid type descriptor: " + typeChar);
        }
    }

    public int typeCode() {
        if (this.desc.length() == 1) {
            switch(this.desc.charAt(0)) {
                case 'B':
                    return 8;
                case 'C':
                    return 5;
                case 'D':
                    return 7;
                case 'F':
                    return 6;
                case 'I':
                    return 10;
                case 'J':
                    return 11;
                case 'S':
                    return 9;
                case 'Z':
                    return 4;
            }
        }

        throw new IllegalArgumentException("Invalid type descriptor: " + this.desc);
    }

    public String shortName() {
        if (this.isIntegral()) {
            return "I";
        } else if (this.isReference()) {
            return "R";
        } else {
            return this.desc;
        }
    }

    public Type simple() {
        if (this.isIntegral()) {
            return INTEGER;
        } else {
            return this.isReference() ? OBJECT : this;
        }
    }

    public String descriptor() {
        return this.desc;
    }

    public boolean isMethod() {
        return this.returnType != null;
    }

    public boolean isNull() {
        return this.equals(NULL);
    }

    public boolean isVoid() {
        return this.equals(VOID);
    }

    public boolean isPrimitive() {
        return !this.isReference() && !this.isMethod() && !this.isVoid();
    }

    public boolean isIntegral() {
        return this.desc.charAt(0) == 'B' || this.desc.charAt(0) == 'S' || this.desc.charAt(0) == 'I' || this.desc.charAt(0) == 'C' || this.desc.charAt(0) == 'Z';
    }

    public boolean isArray() {
        return this.desc.charAt(0) == '[';
    }

    public boolean isObject() {
        return this.desc.charAt(0) == 'L';
    }

    public boolean isWide() {
        return this.desc.charAt(0) == 'J' || this.desc.charAt(0) == 'D';
    }

    public boolean isAddress() {
        return this.desc.charAt(0) == 'A';
    }

    public boolean isReference() {
        return this.desc.charAt(0) == '[' || this.desc.charAt(0) == 'L';
    }

    public static String classDescriptor(String name) {
        if (name.endsWith(".class")) {
            name = name.substring(0, name.lastIndexOf(46));
        }

        name = name.replace('.', '/');
        return name.charAt(0) == '[' ? name : 'L' + name + ";";
    }

    public String className() {
        return this.desc.charAt(0) != '[' && !this.isPrimitive() ? this.desc.substring(1, this.desc.lastIndexOf(59)) : this.desc;
    }

    public String qualifier() {
        int index = this.desc.lastIndexOf(47);
        return index >= 0 ? this.desc.substring(1, index) : this.desc.substring(1, this.desc.lastIndexOf(59));
    }

    public int dimensions() {
        for(int i = 0; i < this.desc.length(); ++i) {
            if (this.desc.charAt(i) != '[') {
                return i;
            }
        }

        throw new IllegalArgumentException(this.desc + " does not have an element type.");
    }

    public Type arrayType() {
        return getType("[" + this.desc);
    }

    public Type arrayType(int dimensions) {
        String d;
        for(d = ""; dimensions-- > 0; d = d + '[') {
        }

        return getType(d + this.desc);
    }

    public Type elementType(int dimensions) {
        for(int i = 0; i < dimensions; ++i) {
            if (this.desc.charAt(i) != '[') {
                throw new IllegalArgumentException(this.desc + " is not an array");
            }
        }

        return getType(this.desc.substring(dimensions));
    }

    public Type elementType() {
        return this.elementType(1);
    }

    public Type returnType() {
        return this.returnType;
    }

    public Type[] indexedParamTypes() {
        if (this.paramTypes == null) {
            return null;
        } else {
            ArrayList p = new ArrayList(this.paramTypes.length * 2);

            for(int i = 0; i < this.paramTypes.length; ++i) {
                p.add(this.paramTypes[i]);
                if (this.paramTypes[i].isWide()) {
                    p.add((Object)null);
                }
            }

            Object[] a = p.toArray();
            Type[] types = new Type[a.length];
            System.arraycopy(a, 0, types, 0, a.length);
            return types;
        }
    }

    public Type[] paramTypes() {
        return this.paramTypes;
    }

    public int stackHeight() {
        if (this.isVoid()) {
            return 0;
        } else if (this.isWide()) {
            return 2;
        } else if (this.paramTypes != null) {
            int numParams = 0;

            for(int i = 0; i < this.paramTypes.length; ++i) {
                if (this.paramTypes[i].isWide()) {
                    ++numParams;
                }

                ++numParams;
            }

            return numParams;
        } else {
            return 1;
        }
    }

    public int hashCode() {
        return this.desc.hashCode();
    }

    public boolean equals(Object obj) {
        return obj != null && obj instanceof Type && ((Type)obj).desc.equals(this.desc);
    }

    public static Comparator comparator() {
        if (comparator != null) {
            return comparator;
        } else {
            comparator = new Comparator() {
                public int compare(Object o1, Object o2) {
                    if (!(o1 instanceof Type)) {
                        throw new IllegalArgumentException(o1 + " not a Type");
                    } else if (!(o2 instanceof Type)) {
                        throw new IllegalArgumentException(o2 + " not a Type");
                    } else {
                        Type t1 = (Type)o1;
                        Type t2 = (Type)o2;
                        String d1 = t1.descriptor();
                        String d2 = t2.descriptor();
                        return d1.compareToIgnoreCase(d2);
                    }
                }

                public boolean equals(Object o) {
                    return o == this;
                }
            };
            return comparator;
        }
    }

    public static Comparator printComparator() {
        if (printComparator != null) {
            return printComparator;
        } else {
            comparator = new Comparator() {
                public int compare(Object o1, Object o2) {
                    if (!(o1 instanceof Type)) {
                        throw new IllegalArgumentException(o1 + " not a Type");
                    } else if (!(o2 instanceof Type)) {
                        throw new IllegalArgumentException(o2 + " not a Type");
                    } else {
                        Type t1 = (Type)o1;
                        Type t2 = (Type)o2;
                        String d1 = t1.toString();
                        String d2 = t2.toString();
                        return d1.compareToIgnoreCase(d2);
                    }
                }

                public boolean equals(Object o) {
                    return o == this;
                }
            };
            return comparator;
        }
    }

    public static String truncatedName(Type type) {
        if (type == null) {
            return "";
        } else if (!type.isPrimitive() && !type.isVoid() && !type.isNull()) {
            if (type.isArray()) {
                return "[" + truncatedName(type.elementType());
            } else if (type.isMethod()) {
                StringBuffer sb = new StringBuffer();
                sb.append('(');
                Type[] params = type.indexedParamTypes();

                for(int i = 0; i < params.length; ++i) {
                    sb.append(truncatedName(params[i]));
                    if (i < params.length - 1) {
                        sb.append(',');
                    }
                }

                sb.append(')');
                sb.append(truncatedName(type.returnType()));
                return sb.toString();
            } else {
                String longName = type.className();
                int lastSlash = longName.lastIndexOf(47);
                if (lastSlash == -1) {
                    lastSlash = longName.lastIndexOf(46);
                }

                String className = longName.substring(lastSlash + 1);
                if (className.length() <= 8) {
                    return className;
                } else {
                    StringBuffer caps = new StringBuffer();
                    int nameLength = className.length();

                    int capsLength =0;
                    for(capsLength = 0; capsLength < nameLength; ++capsLength) {
                        char c = className.charAt(capsLength);
                        if (Character.isUpperCase(c)) {
                            caps.append(c);
                        }
                    }

                    int indexLastCap;
                    if (caps.length() <= 8) {
                        if (caps.length() <= 0) {
                            return className.substring(0, 8);
                        } else {
                            char lastCap = caps.charAt(caps.length() - 1);
                            indexLastCap = className.lastIndexOf(lastCap);
                            capsLength = caps.length();
                            int end;
                            if (capsLength + (nameLength - indexLastCap) > 8) {
                                end = indexLastCap + 1 + (8 - capsLength);
                            } else {
                                end = nameLength;
                            }

                            String endOfName = className.substring(indexLastCap + 1, end);
                            caps.append(endOfName);
                            return caps.toString();
                        }
                    } else {
                        capsLength = caps.length();

                        for(indexLastCap = 4; indexLastCap < capsLength - 4; ++indexLastCap) {
                            caps.deleteCharAt(indexLastCap);
                        }

                        return caps.toString();
                    }
                }
            }
        } else {
            return type.desc;
        }
    }

    public String toString() {
        return PRINT_TRUNCATED ? truncatedName(this) : this.desc;
    }

    public static void main(String[] args) {
        truncatedName(getType("(D)V"));

        for(int i = 0; i < args.length; ++i) {
            System.out.println("Truncated name of " + args[i] + ": " + truncatedName(getType(args[i])));
        }

    }
}


package com.d0klabs.cryptowalt.data;

public interface FieldInfo {
    ClassInfo declaringClass();

    int nameIndex();

    int typeIndex();

    void setNameIndex(int var1);

    void setTypeIndex(int var1);

    void setModifiers(int var1);

    int modifiers();

    int constantValue();

    void setConstantValue(int var1);
}

package com.d0klabs.cryptowalt.data;

public interface EditorVisitor {
    void visitClassEditor(ClassEditor var1);

    void visitMethodEditor(MethodEditor var1);

    void visitFieldEditor(EditorContext.FieldEditor var1);
}

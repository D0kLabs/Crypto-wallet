package com.d0klabs.cryptowalt.data;

public interface MethodInfo {
    ClassInfo declaringClass();

    int nameIndex();

    int typeIndex();

    void setNameIndex(int var1);

    void setTypeIndex(int var1);

    void setModifiers(int var1);

    int modifiers();

    int[] exceptionTypes();

    int maxStack();

    void setMaxStack(int var1);

    int maxLocals();

    void setMaxLocals(int var1);

    byte[] code();

    void setCode(byte[] var1);

    LineNumberDebugInfo[] lineNumbers();

    void setLineNumbers(LineNumberDebugInfo[] var1);

    LocalDebugInfo[] locals();

    void setLocals(LocalDebugInfo[] var1);

    Catch[] exceptionHandlers();

    void setExceptionHandlers(Catch[] var1);

    Object clone();

    public class Catch {
        private int startPC;
        private int endPC;
        private int handlerPC;
        private int catchType;

        public Catch(int startPC, int endPC, int handlerPC, int catchType) {
            this.startPC = startPC;
            this.endPC = endPC;
            this.handlerPC = handlerPC;
            this.catchType = catchType;
        }

        public int startPC() {
            return this.startPC;
        }

        public void setStartPC(int pc) {
            this.startPC = pc;
        }

        public int endPC() {
            return this.endPC;
        }

        public void setEndPC(int pc) {
            this.endPC = pc;
        }

        public int handlerPC() {
            return this.handlerPC;
        }

        public void setHandlerPC(int pc) {
            this.handlerPC = pc;
        }

        public int catchTypeIndex() {
            return this.catchType;
        }

        public void setCatchTypeIndex(int index) {
            this.catchType = index;
        }

        public Object clone() {
            return new Catch(this.startPC, this.endPC, this.handlerPC, this.catchType);
        }

        public String toString() {
            return "(try-catch " + this.startPC + " " + this.endPC + " " + this.handlerPC + " " + this.catchType + ")";
        }
    }

    public class LocalDebugInfo {
        private int startPC;
        private int length;
        private int nameIndex;
        private int typeIndex;
        private int index;

        public LocalDebugInfo(int startPC, int length, int nameIndex, int typeIndex, int index) {
            this.startPC = startPC;
            this.length = length;
            this.nameIndex = nameIndex;
            this.typeIndex = typeIndex;
            this.index = index;
        }

        public int startPC() {
            return this.startPC;
        }

        public int length() {
            return this.length;
        }

        public int nameIndex() {
            return this.nameIndex;
        }

        public int typeIndex() {
            return this.typeIndex;
        }

        public int index() {
            return this.index;
        }

        public Object clone() {
            return new LocalDebugInfo(this.startPC, this.length, this.nameIndex, this.typeIndex, this.index);
        }

        public String toString() {
            return "(local #" + this.index + " pc=" + this.startPC + ".." + (this.startPC + this.length) + " name=" + this.nameIndex + " desc=" + this.typeIndex + ")";
        }
    }
    public class LineNumberDebugInfo {
        private int startPC;
        private int lineNumber;

        public LineNumberDebugInfo(int startPC, int lineNumber) {
            this.startPC = startPC;
            this.lineNumber = lineNumber;
        }

        public int startPC() {
            return this.startPC;
        }

        public int lineNumber() {
            return this.lineNumber;
        }

        public String toString() {
            return "(line #" + this.lineNumber + " pc=" + this.startPC + ")";
        }

        public Object clone() {
            return new LineNumberDebugInfo(this.startPC, this.lineNumber);
        }
    }
}

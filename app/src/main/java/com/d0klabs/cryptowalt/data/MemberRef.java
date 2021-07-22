package com.d0klabs.cryptowalt.data;

public class MemberRef {
    private Type declaringClass;
    private NameAndType nameAndType;

    public MemberRef(Type declaringClass, NameAndType nameAndType) {
        this.declaringClass = declaringClass;
        this.nameAndType = nameAndType;
    }

    public Type declaringClass() {
        return this.declaringClass;
    }

    public String name() {
        return this.nameAndType.name();
    }

    public Type type() {
        return this.nameAndType.type();
    }

    public NameAndType nameAndType() {
        return this.nameAndType;
    }

    public String toString() {
        String className = this.declaringClass.toString();
        return "<" + (this.type().isMethod() ? "Method" : "Field") + " " + className + "." + this.name() + " " + this.type() + ">";
    }

    public boolean equals(Object obj) {
        return obj instanceof MemberRef && ((MemberRef) obj).declaringClass.equals(this.declaringClass) && ((MemberRef) obj).nameAndType.equals(this.nameAndType);
    }

    public int hashCode() {
        return this.declaringClass.hashCode() ^ this.nameAndType.hashCode();
    }
}

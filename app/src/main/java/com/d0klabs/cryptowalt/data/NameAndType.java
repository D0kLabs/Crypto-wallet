package com.d0klabs.cryptowalt.data;

public class NameAndType {
    private String name;
    private Type type;

    public NameAndType(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return this.name;
    }

    public Type type() {
        return this.type;
    }

    public String toString() {
        return "<NameandType " + this.name + " " + this.type + ">";
    }

    public boolean equals(Object obj) {
        return obj instanceof NameAndType && ((NameAndType) obj).name.equals(this.name) && ((NameAndType) obj).type.equals(this.type);
    }

    public int hashCode() {
        return this.name.hashCode() ^ this.type.hashCode();
    }
}

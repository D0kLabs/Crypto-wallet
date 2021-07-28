package com.d0klabs.cryptowalt.data;

public class LocalExpr extends VarExpr implements LeafExpr {
    boolean fromStack;

    public LocalExpr(int index, boolean fromStack, Type type) {
        super(index, type);
        this.fromStack = fromStack;
    }

    public LocalExpr(int index, Type type) {
        this(index, false, type);
    }

    public boolean fromStack() {
        return this.fromStack;
    }

    public boolean isReturnAddress() {
        return this.type().equals(Type.ADDRESS);
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitLocalExpr(this);
    }

    public boolean equalsExpr(Expr other) {
        return other instanceof LocalExpr && ((LocalExpr) other).type.simple().equals(this.type.simple()) && ((LocalExpr) other).fromStack == this.fromStack && ((LocalExpr) other).index == this.index;
    }

    public int exprHashCode() {
        return 13 + (this.fromStack ? 0 : 1) + this.index + this.type.simple().hashCode();
    }

    public Object clone() {
        return this.copyInto(new LocalExpr(this.index, this.fromStack, this.type));
    }
}

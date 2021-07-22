package com.d0klabs.cryptowalt.data;

public class ArrayRefExpr extends LeafExpr.MemRefExpr {
    LeafExpr.Expr array;
    LeafExpr.Expr index;
    Type elementType;

    public ArrayRefExpr(LeafExpr.Expr array, LeafExpr.Expr index, Type elementType, Type type) {
        super(type);
        this.array = array;
        this.index = index;
        this.elementType = elementType;
        array.setParent(this);
        index.setParent(this);
    }

    public LeafExpr.Expr array() {
        return this.array;
    }

    public LeafExpr.Expr index() {
        return this.index;
    }

    public Type elementType() {
        return this.elementType;
    }

    public void visitForceChildren(LeafExpr.TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.index.visit(visitor);
            this.array.visit(visitor);
        } else {
            this.array.visit(visitor);
            this.index.visit(visitor);
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitArrayRefExpr(this);
    }

    public int exprHashCode() {
        return 4 + this.array.exprHashCode() ^ this.index.exprHashCode();
    }

    public boolean equalsExpr(LeafExpr.Expr other) {
        return other != null && other instanceof ArrayRefExpr && ((ArrayRefExpr) other).array.equalsExpr(this.array) && ((ArrayRefExpr) other).index.equalsExpr(this.index);
    }

    public Object clone() {
        return this.copyInto(new ArrayRefExpr((LeafExpr.Expr) this.array.clone(), (LeafExpr.Expr) this.index.clone(), this.elementType, this.type));
    }
}

package com.d0klabs.cryptowalt.data;

public class FieldExpr extends LeafExpr.MemRefExpr {
    LeafExpr.Expr object;
    MemberRef field;

    public FieldExpr(LeafExpr.Expr object, MemberRef field, Type type) {
        super(type);
        this.object = object;
        this.field = field;
        object.setParent(this);
    }

    public LeafExpr.Expr object() {
        return this.object;
    }

    public MemberRef field() {
        return this.field;
    }

    public void visitForceChildren(LeafExpr.TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.object.visit(visitor);
        } else {
            this.object.visit(visitor);
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitFieldExpr(this);
    }

    public int exprHashCode() {
        return 11 + this.object.exprHashCode() ^ this.type.simple().hashCode();
    }

    public boolean equalsExpr(LeafExpr.Expr other) {
        return other != null && other instanceof FieldExpr && ((FieldExpr) other).field.equals(this.field) && ((FieldExpr) other).object.equalsExpr(this.object);
    }

    public Object clone() {
        return this.copyInto(new FieldExpr((LeafExpr.Expr) this.object.clone(), this.field, this.type));
    }
}

package com.d0klabs.cryptowalt.data;

public class CallStaticExpr extends CallExpr {
    public CallStaticExpr(LeafExpr.Expr[] params, MemberRef method, Type type) {
        super(params, method, type);
    }

    public void visitForceChildren(LeafExpr.TreeVisitor visitor) {
        int i;
        if (visitor.reverse()) {
            for (i = this.params.length - 1; i >= 0; --i) {
                this.params[i].visit(visitor);
            }
        } else {
            for (i = 0; i < this.params.length; ++i) {
                this.params[i].visit(visitor);
            }
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitCallStaticExpr(this);
    }

    public int exprHashCode() {
        int v = 6;

        for (int i = 0; i < this.params.length; ++i) {
            v ^= this.params[i].exprHashCode();
        }

        return v;
    }

    public boolean equalsExpr(LeafExpr.Expr other) {
        return false;
    }

    public Object clone() {
        LeafExpr.Expr[] p = new LeafExpr.Expr[this.params.length];

        for (int i = 0; i < this.params.length; ++i) {
            p[i] = (LeafExpr.Expr) this.params[i].clone();
        }

        return this.copyInto(new CallStaticExpr(p, this.method, this.type));
    }
}

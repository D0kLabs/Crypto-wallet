package com.d0klabs.cryptowalt.data;

public class CallMethodExpr extends CallExpr {
    public static final int VIRTUAL = 0;
    public static final int NONVIRTUAL = 1;
    public static final int INTERFACE = 2;
    LeafExpr.Expr receiver;
    int kind;

    public CallMethodExpr(int kind, LeafExpr.Expr receiver, LeafExpr.Expr[] params, MemberRef method, Type type) {
        super(params, method, type);
        this.receiver = receiver;
        this.kind = kind;
        receiver.setParent(this);
    }

    public int kind() {
        return this.kind;
    }

    public LeafExpr.Expr receiver() {
        return this.receiver;
    }

    public void visitForceChildren(LeafExpr.TreeVisitor visitor) {
        int i;
        if (visitor.reverse()) {
            for (i = this.params.length - 1; i >= 0; --i) {
                this.params[i].visit(visitor);
            }

            this.receiver.visit(visitor);
        } else {
            this.receiver.visit(visitor);

            for (i = 0; i < this.params.length; ++i) {
                this.params[i].visit(visitor);
            }
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitCallMethodExpr(this);
    }

    public int exprHashCode() {
        int v = 5 + this.kind ^ this.receiver.exprHashCode();

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

        return this.copyInto(new CallMethodExpr(this.kind, (LeafExpr.Expr) this.receiver.clone(), p, this.method, this.type));
    }
}

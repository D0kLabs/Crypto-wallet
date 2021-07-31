package com.d0klabs.cryptowalt.data;

public class IfCmpStmt extends LeafExpr.IfStmt {
    LeafExpr.Expr left;
    LeafExpr.Expr right;
    private java.lang.Object Object;

    public IfCmpStmt(int comparison, LeafExpr.Expr left, LeafExpr.Expr right, Block trueTarget, Block falseTarget) {
        super(comparison, trueTarget, falseTarget);
        this.left = left;
        this.right = right;
        left.setParent(this);
        right.setParent(this);
    }

    public LeafExpr.Expr left() {
        return this.left;
    }

    public LeafExpr.Expr right() {
        return this.right;
    }

    public void visitForceChildren(LeafExpr.TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.right.visit(visitor);
            this.left.visit(visitor);
        } else {
            this.left.visit(visitor);
            this.right.visit(visitor);
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitIfCmpStmt(this);
    }

    public Object clone() {
        return this.copyInto(new IfCmpStmt(this.comparison, (LeafExpr.Expr) this.left.clone(), (LeafExpr.Expr) this.right.clone(), this.trueTarget, this.falseTarget));
    }

    public Block[] targets() {
        return new Block[0];
    }

    public int[] values() {
        return new int[0];
    }

    public Object expr() {
        return Object;
    }

    public LeafExpr.Expr index() {
        return null;
    }
}

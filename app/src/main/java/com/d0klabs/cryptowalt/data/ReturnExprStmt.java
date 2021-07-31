package com.d0klabs.cryptowalt.data;

public class ReturnExprStmt extends JumpStmt {
    LeafExpr.Expr expr;

    public ReturnExprStmt(LeafExpr.Expr expr) {
        super(stmt.follow());
        this.expr = expr;
        expr.setParent(this);
    }

    public LeafExpr.Expr expr() {
        return this.expr;
    }

    public void visitForceChildren(LeafExpr.TreeVisitor visitor) {
        this.expr.visit(visitor);
    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitReturnExprStmt(this);
    }

    public Object clone() {
        return this.copyInto(new ReturnExprStmt((LeafExpr.Expr) this.expr.clone()));
    }
}

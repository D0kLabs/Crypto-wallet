package com.d0klabs.cryptowalt.data;

public class ExprStmt extends LeafExpr.Stmt {
    LeafExpr.Expr expr;

    public ExprStmt(LeafExpr.Expr expr) {
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
        visitor.visitExprStmt(this);
    }

    public Object clone() {
        return this.copyInto(new ExprStmt((LeafExpr.Expr) this.expr.clone()));
    }
}

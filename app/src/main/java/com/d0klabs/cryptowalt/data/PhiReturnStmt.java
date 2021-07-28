package com.d0klabs.cryptowalt.data;

import java.util.ArrayList;
import java.util.Collection;

public class PhiReturnStmt extends PhiStmt{
    Subroutine sub;

    LeafExpr.Expr operand;

    /**
     * Constructor.
     *
     * @param target
     *            Local variable to which the result of this phi statement is to
     *            be assigned.
     * @param sub
     *            The subroutine from which we are returning.
     */
    public PhiReturnStmt(final VarExpr target, final Subroutine sub) {
        super(target);
        this.sub = sub;
        this.operand = (VarExpr) target.clone();
        operand.setParent(this);
        operand.setDef(null);
    }

    public void visitForceChildren(final LeafExpr.TreeVisitor visitor) {
        operand.visit(visitor);
    }

    public void visit(final LeafExpr.TreeVisitor visitor) {
        visitChildren(visitor);
    }

    /**
     * Returns the subroutine associated with this <tt>PhiReturnStmt</tt>.
     */
    public Subroutine sub() {
        return sub;
    }

    /**
     * Returns a collection containing the operands to the phi statement. In
     * this case the collection contains the one operand.
     */
    public Collection operands() {
        final ArrayList v = new ArrayList();
        v.add(operand);
        return v;
    }

    /**
     * Returns the operand of this <tt>PhiReturnStmt</tt> statement. A
     * <tt>PhiReturnStmt</tt> has only one operand because the block that
     * begins an exception handler may have only one incoming edge (critical
     * edges were split).
     */
    public LeafExpr.Expr operand() {
        return operand;
    }

    public String toString() {
        return "" + target() + " := Phi-Return(" + operand + ", " + sub + ")";
    }
}

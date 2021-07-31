package com.d0klabs.cryptowalt.data;

import java.util.Hashtable;

public class Type1Visitor extends AscendVisitor {
    LeafExpr.Node turningPoint;
    boolean found;

    public Type1Visitor(Hashtable defInfoMap, Hashtable useInfoMap) {
        super(defInfoMap, useInfoMap);
    }

    public void search(LocalExpr start) {
        this.start = start;
        this.previous = this.start;
        this.found = false;
        start.parent().visit(this);
        if (!this.found) {
            if (this.turningPoint != null) {
                (new Type1UpVisitor(this.defInfoMap, this.useInfoMap)).search(this.turningPoint, start);
            } else {
                DefInformation var10000 = (DefInformation) this.defInfoMap.get(start.def());
                var10000.type1s += 3;
            }
        }

    }

    public void check(LeafExpr.Node node) {
        if (node instanceof LeafExpr.Expr && ((LeafExpr.Expr) node).type().isWide()) {
            this.turningPoint = null;
        } else {
            this.turningPoint = node;
            if (node instanceof StoreExpr) {
                this.check(((StoreExpr) node).expr());
            } else if (!(node instanceof LocalExpr) && node instanceof LeafExpr.Expr) {
                this.found = (new Type1DownVisitor(this.useInfoMap, this.defInfoMap)).search(node, this.start);
            }

        }
    }

    @Override
    public void visitTree(Tree tree) {

    }
}

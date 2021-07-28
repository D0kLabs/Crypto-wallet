package com.d0klabs.cryptowalt.data;

import com.d0klabs.cryptowalt.data.LeafExpr.DefExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.Expr;
import com.d0klabs.cryptowalt.data.LeafExpr.IfZeroStmt;
import com.d0klabs.cryptowalt.data.LeafExpr.MemExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.MemRefExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.Node;
import com.d0klabs.cryptowalt.data.LeafExpr.StaticFieldExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.Stmt;
import com.d0klabs.cryptowalt.data.LeafExpr.Tree;
import com.d0klabs.cryptowalt.data.LeafExpr.TreeVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class Block extends GraphNode {
    public static final int NON_HEADER = 0;
    public static final int IRREDUCIBLE = 1;
    public static final int REDUCIBLE = 2;
    FlowGraph graph;
    Label label;
    LeafExpr.Tree tree;
    Block domParent;
    Block pdomParent;
    Set domChildren;
    Set pdomChildren;
    Set domFrontier;
    Set pdomFrontier;
    int blockType;
    Block header;
    StackOptimizer stackOptimizer;

    Block(Label label, FlowGraph graph) {
        this.label = label;
        this.graph = graph;
        this.tree = null;
        this.header = null;
        this.blockType = 0;
        label.setStartsBlock(true);
        this.domParent = null;
        this.pdomParent = null;
        this.domChildren = new HashSet();
        this.pdomChildren = new HashSet();
        this.domFrontier = new HashSet();
        this.pdomFrontier = new HashSet();
        this.stackOptimizer = new StackOptimizer(this);
    }

    public StackOptimizer stackOptimizer() {
        return this.stackOptimizer;
    }

    public LeafExpr.Tree tree() {
        return this.tree;
    }

    public void setTree(LeafExpr.Tree tree) {
        this.tree = tree;
    }

    public FlowGraph graph() {
        return this.graph;
    }

    public Label label() {
        return this.label;
    }

    public void visitChildren(LeafExpr.TreeVisitor visitor) {
        if (this.tree != null) {
            this.tree.visit(visitor);
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitBlock(this);
    }

    void setBlockType(int blockType) {
        this.blockType = blockType;
        if (FlowGraph.DEBUG) {
            System.out.println("    Set block type " + this);
        }

    }

    int blockType() {
        return this.blockType;
    }

    public void setHeader(Block header) {
        this.header = header;
        if (FlowGraph.DEBUG) {
            System.out.println("    Set header " + this);
        }

    }

    public Block header() {
        return this.header;
    }

    public String toString() {
        String s = "<block " + this.label + " hdr=";
        if (this.header != null) {
            s = s + this.header.label();
        } else {
            s = s + "null";
        }

        switch(this.blockType) {
            case 0:
            default:
                break;
            case 1:
                s = s + " irred";
                break;
            case 2:
                s = s + " red";
        }

        if (this == this.graph.source()) {
            return s + " source>";
        } else if (this == this.graph.init()) {
            return s + " init>";
        } else {
            return this == this.graph.sink() ? s + " sink>" : s + ">";
        }
    }

    Collection domChildren() {
        return this.domChildren;
    }

    Block domParent() {
        return this.domParent;
    }

    void setDomParent(Block block) {
        if (this.domParent != null) {
            this.domParent.domChildren.remove(this);
        }

        this.domParent = block;
        if (this.domParent != null) {
            this.domParent.domChildren.add(this);
        }

    }

    public boolean dominates(Block block) {
        for(Block p = block; p != null; p = p.domParent()) {
            if (p == this) {
                return true;
            }
        }

        return false;
    }

    Collection pdomChildren() {
        return this.pdomChildren;
    }

    Block pdomParent() {
        return this.pdomParent;
    }

    void setPdomParent(Block block) {
        if (this.pdomParent != null) {
            this.pdomParent.pdomChildren.remove(this);
        }

        this.pdomParent = block;
        if (this.pdomParent != null) {
            this.pdomParent.pdomChildren.add(this);
        }

    }

    public boolean postdominates(Block block) {
        for(Block p = block; p != null; p = p.pdomParent()) {
            if (p == this) {
                return true;
            }
        }

        return false;
    }

    Collection domFrontier() {
        return this.domFrontier;
    }

    Collection pdomFrontier() {
        return this.pdomFrontier;
    }
}

class StackOptimizer {
    static boolean DEBUG = false;
    Hashtable defInfoMap;
    Hashtable useInfoMap;
    Block owningBlock;

    public StackOptimizer(Block owningBlock) {
        this.owningBlock = owningBlock;
        this.defInfoMap = new Hashtable();
        this.useInfoMap = new Hashtable();
    }

    public static void optimizeCFG(FlowGraph cfg) {
        List blocks = cfg.preOrder();
        Iterator it = blocks.iterator();

        while(it.hasNext()) {
            ((Block)it.next()).stackOptimizer().optimize();
        }

    }

    public void optimize() {
        Vector LEs = (new LEGatherer()).getLEs(this.owningBlock);

        for(int i = 0; i < LEs.size(); ++i) {
            LocalExpr current = (LocalExpr)LEs.elementAt(i);
            this.useInfoMap.put(current, new UseInformation());
            DefInformation DI;
            if (current.isDef()) {
                DI = new DefInformation(current.uses.size());
                this.defInfoMap.put(current, DI);
            } else if (current.def() != null) {
                DI = (DefInformation)this.defInfoMap.get(current.def());
                if (DI != null) {
                    ++DI.usesFound;
                    if (current.parent() instanceof ArithExpr && current.parent().parent() instanceof StoreExpr && (((ArithExpr)current.parent()).left() instanceof ConstantExpr && ((ArithExpr)current.parent()).left().type().isIntegral() || ((ArithExpr)current.parent()).right() instanceof ConstantExpr && ((ArithExpr)current.parent()).left().type().isIntegral()) && ((StoreExpr)current.parent().parent()).target() instanceof LocalExpr && ((LocalExpr)((StoreExpr)current.parent().parent()).target()).index() == current.index()) {
                        DI.type1s += 3;
                    } else if (current.parent() instanceof StoreExpr && current.parent().parent() instanceof ExprStmt && ((StoreExpr)current.parent()).target() instanceof LocalExpr && ((LocalExpr)((StoreExpr)current.parent()).target()).index() == current.index()) {
                        DI.type1s += 3;
                    } else if (!(new Type0Visitor(this.defInfoMap, this.useInfoMap)).search(current) && DI.type1s < 3) {
                        if (current.type().isWide()) {
                            DI.type1s += 3;
                        } else {
                            (new Type1Visitor(this.defInfoMap, this.useInfoMap)).search(current);
                        }
                    }
                }
            }
        }

    }

    public boolean shouldStore(LocalExpr expr) {
        if (expr == null) {
            return true;
        } else {
            DefInformation DI = (DefInformation)this.defInfoMap.get(expr);
            if (DI == null) {
                if (DEBUG) {
                    System.err.println("Error in StackOptimizer.shouldStore: parameter not found in defInfoMap:");
                    System.err.println(expr.toString());
                }

                return true;
            } else {
                return DI.type1s > 2 || DI.usesFound < DI.uses;
            }
        }
    }

    public int dups(LocalExpr expr) {
        int toReturn = 0;
        UseInformation UI = (UseInformation)this.useInfoMap.get(expr);
        if (UI == null) {
            return toReturn;
        } else {
            toReturn = toReturn + (UI.type0s - UI.type0_x1s - UI.type0_x2s);
            if (expr.isDef() && !this.shouldStore(expr) || !expr.isDef() && !this.shouldStore((LocalExpr)expr.def())) {
                toReturn += UI.type1s - UI.type1_x1s - UI.type1_x2s;
            }

            return toReturn;
        }
    }

    public int dup_x1s(LocalExpr expr) {
        int toReturn = 0;
        UseInformation UI = (UseInformation)this.useInfoMap.get(expr);
        if (UI == null) {
            return toReturn;
        } else {
            toReturn = toReturn + UI.type0_x1s;
            if (expr.isDef() && !this.shouldStore(expr) || !expr.isDef() && !this.shouldStore((LocalExpr)expr.def())) {
                toReturn += UI.type1_x1s;
            }
            return toReturn;
        }
    }

    public int dup_x2s(LocalExpr expr) {
        int toReturn = 0;
        UseInformation UI = (UseInformation)this.useInfoMap.get(expr);
        if (UI == null) {

            return toReturn;
        } else {
            toReturn = toReturn + UI.type0_x2s;
            if (expr.isDef() && !this.shouldStore(expr) || !expr.isDef() && !this.shouldStore((LocalExpr)expr.def())) {
                toReturn += UI.type1_x2s;
            }

            return toReturn;
        }
    }

    public boolean onStack(LocalExpr expr) {
        if (expr.isDef()) {
            return false;
        } else {
            UseInformation UI = (UseInformation)this.useInfoMap.get(expr);
            if (UI == null) {

                return false;
            } else if (UI.type == 0) {
                return true;
            } else {
                return UI.type == 1 && !this.shouldStore((LocalExpr)expr.def());
            }
        }
    }

    public boolean shouldSwap(LocalExpr expr) {
        UseInformation UI = (UseInformation)this.useInfoMap.get(expr);
        if (UI == null) {
            if (DEBUG) {
                System.err.println("Error in StackOptimizer.onStack: parameter not found in useInfoMap");
            }

            return false;
        } else {
            return this.onStack(expr) && UI.type == 1;
        }
    }

    public void infoDisplay(LocalExpr expr) {
        UseInformation UI = (UseInformation)this.useInfoMap.get(expr);
        DefInformation DI = (DefInformation)this.defInfoMap.get(expr);
        if (expr.parent().parent().parent() != null && expr.parent().parent().parent().parent() != null) {
            System.err.println(expr.parent().parent().parent().toString() + "-" + expr.parent().parent().parent().parent().toString());
        }


    }
}
class UseInformation {
    int type = 2;
    int type0s = 0;
    int type1s = 0;
    int type0_x1s = 0;
    int type0_x2s = 0;
    int type1_x1s = 0;
    int type1_x2s = 0;

    public UseInformation() {
    }
}
class LEGatherer extends LeafExpr.TreeVisitor {
    Vector LEs;

    public LEGatherer() {
    }

    Vector getLEs(Block b) {
        this.LEs = new Vector();
        this.visitBlock(b);
        return this.LEs;
    }

    public void visitLocalExpr(LocalExpr expr) {
        this.LEs.addElement(expr);
    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }
}
class DefInformation {
    int type1s = 0;
    int uses;
    int usesFound;

    public DefInformation(int uses) {
        this.uses = uses;
        this.usesFound = 0;
    }
}
class Type0DownVisitor extends DescendVisitor {
    public Type0DownVisitor(Hashtable useInfoMap, Hashtable defInfoMap) {
        super(useInfoMap, defInfoMap);
    }

    public void visitLocalExpr(LocalExpr expr) {
        if (expr.index() == this.start.index() && expr.def() == this.start.def()) {
            ((UseInformation)this.useInfoMap.get(this.start)).type = 0;
            UseInformation ui = (UseInformation)this.useInfoMap.get(expr);
            ++ui.type0s;
            if (this.exchangeFactor == 1) {
                ++ui.type0_x1s;
            }

            if (this.exchangeFactor == 2) {
                ++ui.type0_x2s;
            }

            this.found = true;
        }

    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }
}
abstract class DescendVisitor extends TreeVisitor {
    Hashtable useInfoMap;
    Hashtable defInfoMap;
    boolean found;
    Node beginNode;
    LocalExpr start;
    int exchangeFactor;

    public DescendVisitor(Hashtable useInfoMap, Hashtable defInfoMap) {
        this.useInfoMap = useInfoMap;
        this.defInfoMap = defInfoMap;
    }

    public boolean search(Node beginNode, LocalExpr start) {
        this.beginNode = beginNode;
        this.start = start;
        this.exchangeFactor = 0;
        this.found = false;
        beginNode.visit(this);
        return this.found;
    }

    public void visitExprStmt(ExprStmt stmt) {
        stmt.expr().visit(this);
    }

    public void visitIfStmt(LeafExpr.IfStmt stmt) {
        if (stmt instanceof IfCmpStmt) {
            this.visitIfCmpStmt((IfCmpStmt)stmt);
        } else if (stmt instanceof IfZeroStmt) {
            this.visitIfZeroStmt((IfZeroStmt)stmt);
        }

    }

    public void visitIfCmpStmt(IfCmpStmt stmt) {
        stmt.left().visit(this);
        if (!this.found) {
            ++this.exchangeFactor;
            if (stmt.left().type().isWide()) {
                ++this.exchangeFactor;
            }

            if (this.exchangeFactor < 3) {
                stmt.right().visit(this);
            }
        }

    }

    public void visitIfZeroStmt(LeafExpr.IfZeroStmt stmt) {
        stmt.expr().visit(this);
    }

    public void visitInitStmt(InitStmt stmt) {
    }

    public void visitGotoStmt(LeafExpr.GotoStmt stmt) {
    }

    public void visitLabelStmt(LeafExpr.LabelStmt stmt) {
    }

    public void visitMonitorStmt(LeafExpr.MonitorStmt stmt) {
    }

    public void visitPhiStmt(PhiStmt stmt) {
        if (stmt instanceof PhiCatchStmt) {
            this.visitPhiCatchStmt((PhiCatchStmt)stmt);
        } else if (stmt instanceof PhiJoinStmt) {
            this.visitPhiJoinStmt((PhiJoinStmt)stmt);
        }

    }

    public void visitCatchExpr(CatchExpr expr) {
    }

    public void visitDefExpr(LeafExpr.DefExpr expr) {
        if (expr instanceof MemExpr) {
            this.visitMemExpr((MemExpr)expr);
        }

    }

    public void visitStackManipStmt(StackManipStmt stmt) {
    }

    public void visitPhiCatchStmt(PhiCatchStmt stmt) {
    }

    public void visitPhiJoinStmt(PhiJoinStmt stmt) {
    }

    public void visitRetStmt(RetStmt stmt) {
    }

    public void visitReturnExprStmt(ReturnExprStmt stmt) {
    }

    public void visitReturnStmt(ReturnStmt stmt) {
    }

    public void visitAddressStoreStmt(AddressStoreStmt stmt) {
    }

    public void visitStoreExpr(StoreExpr expr) {
        LeafExpr.MemExpr target = expr.target();
        if (target instanceof ArrayRefExpr) {
            ((ArrayRefExpr)target).array().visit(this);
            if (!this.found) {
                ++this.exchangeFactor;
                if (this.exchangeFactor < 3) {
                    ((ArrayRefExpr)target).index().visit(this);
                    if (!this.found) {
                        ++this.exchangeFactor;
                        if (this.exchangeFactor < 3) {
                            expr.expr().visit(this);
                        }
                    }
                }
            }
        } else if (target instanceof FieldExpr) {
            ((FieldExpr)target).object().visit(this);
            if (!this.found) {
                ++this.exchangeFactor;
                if (this.exchangeFactor < 3) {
                    expr.expr().visit(this);
                }
            }
        } else if (target instanceof StaticFieldExpr) {
            expr.expr.visit(this);
        } else if (target instanceof LocalExpr) {
            expr.expr.visit(this);
        }

    }

    public void visitJsrStmt(JsrStmt stmt) {
    }

    public void visitSwitchStmt(SwitchStmt stmt) {
    }

    public void visitThrowStmt(ThrowStmt stmt) {
    }

    public void visitStmt(Stmt stmt) {
    }

    public void visitSCStmt(SCStmt stmt) {
    }

    public void visitSRStmt(SRStmt stmt) {
    }

    public void visitArithExpr(ArithExpr expr) {
        expr.left().visit(this);
        if (!this.found) {
            ++this.exchangeFactor;
            if (expr.left().type().isWide()) {
                ++this.exchangeFactor;
            }

            if (this.exchangeFactor < 3) {
                expr.right().visit(this);
            }
        }

    }

    public void visitArrayLengthExpr(ArrayLengthExpr expr) {
        expr.array().visit(this);
    }

    public void visitMemExpr(MemExpr expr) {
        if (expr instanceof LocalExpr) {
            this.visitLocalExpr((LocalExpr)expr);
        }

    }

    public void visitMemRefExpr(MemRefExpr expr) {
    }

    public void visitArrayRefExpr(ArrayRefExpr expr) {
    }

    public void visitCallExpr(CallExpr expr) {
        if (expr instanceof CallMethodExpr) {
            this.visitCallMethodExpr((CallMethodExpr)expr);
        } else if (expr instanceof CallStaticExpr) {
            this.visitCallStaticExpr((CallStaticExpr)expr);
        }

    }

    public void visitCallMethodExpr(CallMethodExpr expr) {
        expr.receiver().visit(this);
        Expr[] params = expr.params();
        if (!this.found && this.exchangeFactor < 2 && params.length > 0) {
            ++this.exchangeFactor;
            params[0].visit(this);
        }

    }

    public void visitCallStaticExpr(CallStaticExpr expr) {
        Expr[] params = expr.params();
        if (params.length > 0) {
            params[0].visit(this);
        }

        if (!this.found && this.exchangeFactor < 2 && params.length > 1) {
            ++this.exchangeFactor;
            params[1].visit(this);
        }

    }

    public void visitCastExpr(CastExpr expr) {
        expr.expr().visit(this);
    }

    public void visitConstantExpr(ConstantExpr expr) {
    }

    public void visitFieldExpr(FieldExpr expr) {
        expr.object.visit(this);
    }

    public void visitInstanceOfExpr(InstanceOfExpr expr) {
        expr.expr().visit(this);
    }

    public abstract void visitLocalExpr(LocalExpr var1);

    public void visitNegExpr(NegExpr expr) {
        expr.expr().visit(this);
    }

    public void visitNewArrayExpr(NewArrayExpr expr) {
        expr.size().visit(this);
    }

    public void visitNewExpr(NewExpr expr) {
    }

    public void visitNewMultiArrayExpr(NewMultiArrayExpr expr) {
        Expr[] dims = expr.dimensions();
        if (dims.length > 0) {
            dims[0].visit(this);
        }

        if (!this.found && this.exchangeFactor < 2 && dims.length > 1) {
            ++this.exchangeFactor;
            dims[1].visit(this);
        }

    }

    public void visitCheckExpr(CheckExpr expr) {
        if (expr instanceof ZeroCheckExpr) {
            this.visitZeroCheckExpr((ZeroCheckExpr)expr);
        } else if (expr instanceof RCExpr) {
            this.visitRCExpr((RCExpr)expr);
        } else if (expr instanceof UCExpr) {
            this.visitUCExpr((UCExpr)expr);
        }

    }

    public void visitZeroCheckExpr(ZeroCheckExpr expr) {
    }

    public void visitRCExpr(RCExpr expr) {
    }

    public void visitUCExpr(UCExpr expr) {
    }

    public void visitReturnAddressExpr(ReturnAddressExpr expr) {
    }

    public void visitShiftExpr(ShiftExpr expr) {
    }

    public void visitVarExpr(VarExpr expr) {
        if (expr instanceof LocalExpr) {
            this.visitLocalExpr((LocalExpr)expr);
        }

    }

    public void visitStaticFieldExpr(LeafExpr.StaticFieldExpr expr) {
    }

    public void visitExpr(LeafExpr.Expr expr) {
    }
}
class Type0Visitor extends AscendVisitor {
    boolean found;
    static boolean DEBUG = false;

    public Type0Visitor(Hashtable defInfoMap, Hashtable useInfoMap) {
        super(defInfoMap, useInfoMap);
    }

    public boolean search(LocalExpr start) {
        this.start = start;
        this.previous = this.start;
        this.found = false;
        this.start.parent().visit(this);
        return this.found;
    }

    public void check(Node node) {
        if (node instanceof ExprStmt) {
            this.check(((ExprStmt)node).expr());
        }

        if (!this.found && node instanceof Stmt) {
            this.found = (new Type0DownVisitor(this.useInfoMap, this.defInfoMap)).search(node, this.start);
        } else if (node instanceof StoreExpr) {
            StoreExpr n = (StoreExpr)node;
            if (!(n.target() instanceof LocalExpr) || !(n.expr() instanceof LocalExpr) || ((LocalExpr)n.target()).index() != ((LocalExpr)n.expr()).index()) {
                this.check(n.target());
            }
        } else if (node instanceof InitStmt) {
            LocalExpr[] targets = ((InitStmt)node).targets();
            if (targets.length > 0) {
                this.check(targets[targets.length - 1]);
            }
        } else if (node instanceof LocalExpr && ((LocalExpr)node).index() == this.start.index() && ((LocalExpr)node).def() == this.start.def()) {
            ((UseInformation)this.useInfoMap.get(this.start)).type = 0;
            ++((UseInformation)this.useInfoMap.get(node)).type0s;
            this.found = true;
        }

    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }
}
abstract class AscendVisitor extends TreeVisitor {
    Hashtable defInfoMap;
    Hashtable useInfoMap;
    LocalExpr start;
    Node previous;
    Vector visited;

    public AscendVisitor(Hashtable defInfoMap, Hashtable useInfoMap) {
        this.defInfoMap = defInfoMap;
        this.useInfoMap = useInfoMap;
        this.visited = new Vector();
    }

    public abstract void check(Node var1);

    public void visitTree(Tree tree) {
        ListIterator iter = tree.stmts().listIterator(tree.stmts().lastIndexOf(this.previous));
        if (iter.hasPrevious()) {
            Stmt p = (Stmt)iter.previous();
            this.check(p);
        }

    }

    public void visitExprStmt(ExprStmt stmt) {
        this.previous = stmt;
        stmt.parent().visit(this);
    }

    public void visitIfCmpStmt(IfCmpStmt stmt) {
        if (stmt.right() == this.previous) {
            this.check(stmt.left());
        } else if (stmt.left() == this.previous) {
            this.previous = stmt;
            stmt.parent().visit(this);
        }

    }

    public void visitIfZeroStmt(IfZeroStmt stmt) {
        this.previous = stmt;
        stmt.parent.visit(this);
    }

    public void visitInitStmt(InitStmt stmt) {
        LocalExpr[] targets = stmt.targets();

        for(int i = 0; i < targets.length; ++i) {
            if (targets[i] == this.previous) {
                if (i <= 0) {
                    break;
                }

                this.check(targets[i - 1]);
            }
        }

    }

    public void visitGotoStmt(LeafExpr.GotoStmt stmt) {
    }

    public void visitLabelStmt(LeafExpr.LabelStmt stmt) {
    }

    public void visitMonitorStmt(LeafExpr.MonitorStmt stmt) {
        this.previous = stmt;
        stmt.parent().visit(this);
    }

    public void visitPhiStmt(PhiStmt stmt) {
        if (stmt instanceof PhiCatchStmt) {
            this.visitPhiCatchStmt((PhiCatchStmt)stmt);
        } else if (stmt instanceof PhiJoinStmt) {
            this.visitPhiJoinStmt((PhiJoinStmt)stmt);
        }

    }

    public void visitCatchExpr(CatchExpr expr) {
    }

    public void visitDefExpr(DefExpr expr) {
        if (expr instanceof MemExpr) {
            this.visitMemExpr((MemExpr)expr);
        }

    }

    public void visitStackManipStmt(StackManipStmt stmt) {
    }

    public void visitPhiCatchStmt(PhiCatchStmt stmt) {
    }

    public void visitPhiJoinStmt(PhiJoinStmt stmt) {
    }

    public void visitRetStmt(RetStmt stmt) {
    }

    public void visitReturnExprStmt(ReturnExprStmt stmt) {
        this.previous = stmt;
        stmt.parent.visit(this);
    }

    public void visitReturnStmt(ReturnStmt stmt) {
    }

    public void visitAddressStoreStmt(AddressStoreStmt stmt) {
    }

    public void visitStoreExpr(StoreExpr expr) {
        if (!(expr.target() instanceof LocalExpr) && !(expr.target() instanceof StaticFieldExpr)) {
            if (expr.target() instanceof ArrayRefExpr) {
                if (this.previous == expr.expr()) {
                    this.check(((ArrayRefExpr)expr.target()).index());
                } else if (this.previous == expr.target()) {
                    this.previous = expr;
                    expr.parent.visit(this);
                }
            } else if (expr.target() instanceof FieldExpr) {
                if (this.previous == expr.expr()) {
                    this.check(expr.target());
                } else if (this.previous == expr.target()) {
                    this.previous = expr;
                    expr.parent.visit(this);
                }
            }
        } else if (this.previous == expr.expr()) {
            this.previous = expr;
            expr.parent.visit(this);
        }

    }

    public void visitJsrStmt(JsrStmt stmt) {
    }

    public void visitSwitchStmt(SwitchStmt stmt) {
        if (this.previous == stmt.index()) {
            this.previous = stmt;
            stmt.parent.visit(this);
        }

    }

    public void visitThrowStmt(ThrowStmt stmt) {
    }

    public void visitStmt(Stmt stmt) {
    }

    public void visitSCStmt(SCStmt stmt) {
    }

    public void visitSRStmt(SRStmt stmt) {
    }

    public void visitArithExpr(ArithExpr expr) {
        if (this.previous == expr.left()) {
            this.previous = expr;
            expr.parent.visit(this);
        } else if (this.previous == expr.right()) {
            this.check(expr.left());
        }

    }

    public void visitArrayLengthExpr(ArrayLengthExpr expr) {
    }

    public void visitMemExpr(MemExpr expr) {
        if (expr instanceof MemRefExpr) {
            this.visitMemRefExpr((MemRefExpr)expr);
        } else if (expr instanceof VarExpr) {
            this.visitVarExpr((VarExpr)expr);
        }

    }

    public void visitMemRefExpr(MemRefExpr expr) {
        if (expr instanceof FieldExpr) {
            this.visitFieldExpr((FieldExpr)expr);
        } else if (expr instanceof StaticFieldExpr) {
            this.visitStaticFieldExpr((StaticFieldExpr)expr);
        } else if (expr instanceof ArrayRefExpr) {
            this.visitArrayRefExpr((ArrayRefExpr)expr);
        }

    }

    public void visitArrayRefExpr(ArrayRefExpr expr) {
        if (this.previous == expr.array()) {
            this.previous = expr;
            expr.parent().visit(this);
        } else if (this.previous == expr.index()) {
            this.check(expr.array());
        }

    }

    public void visitCallExpr(CallExpr expr) {
        if (expr instanceof CallMethodExpr) {
            this.visitCallMethodExpr((CallMethodExpr)expr);
        }

        if (expr instanceof CallStaticExpr) {
            this.visitCallStaticExpr((CallStaticExpr)expr);
        }

    }

    public void visitCallMethodExpr(CallMethodExpr expr) {
        if (this.previous == expr.receiver()) {
            this.previous = expr;
            expr.parent.visit(this);
        } else {
            Expr[] params = expr.params();

            for(int i = 0; i < params.length; ++i) {
                if (params[i] == this.previous) {
                    if (i > 0) {
                        this.check(params[i - 1]);
                    } else {
                        this.check(expr.receiver());
                    }
                }
            }
        }

    }

    public void visitCallStaticExpr(CallStaticExpr expr) {
        Expr[] params = expr.params();

        for(int i = 0; i < params.length; ++i) {
            if (params[i] == this.previous) {
                if (i > 0) {
                    this.check(params[i - 1]);
                } else {
                    this.previous = expr;
                    expr.parent().visit(this);
                }
                break;
            }
        }

    }

    public void visitCastExpr(CastExpr expr) {
        this.previous = expr;
        expr.parent.visit(this);
    }

    public void visitConstantExpr(ConstantExpr expr) {
    }

    public void visitFieldExpr(FieldExpr expr) {
        if (this.previous == expr.object()) {
            this.previous = expr;
            expr.parent.visit(this);
        }

    }

    public void visitInstanceOfExpr(InstanceOfExpr expr) {
        if (this.previous == expr.expr()) {
            this.previous = expr;
            expr.parent.visit(this);
        }

    }

    public void visitLocalExpr(LocalExpr expr) {
    }

    public void visitNegExpr(NegExpr expr) {
        if (this.previous == expr.expr()) {
            this.previous = expr;
            expr.parent.visit(this);
        }

    }

    public void visitNewArrayExpr(NewArrayExpr expr) {
        if (this.previous == expr.size()) {
            this.previous = expr;
            expr.parent.visit(this);
        }

    }

    public void visitNewExpr(NewExpr expr) {
    }

    public void visitNewMultiArrayExpr(NewMultiArrayExpr expr) {
        Expr[] dims = expr.dimensions;

        for(int i = 0; i < dims.length; ++i) {
            if (dims[i] == this.previous) {
                if (i > 0) {
                    this.check(dims[i - 1]);
                } else {
                    this.previous = expr;
                    expr.parent().visit(this);
                }
            }
        }

    }

    public void visitCheckExpr(CheckExpr expr) {
        if (expr instanceof ZeroCheckExpr) {
            this.visitZeroCheckExpr((ZeroCheckExpr)expr);
        } else if (expr instanceof RCExpr) {
            this.visitRCExpr((RCExpr)expr);
        } else if (expr instanceof UCExpr) {
            this.visitUCExpr((UCExpr)expr);
        }

    }

    public void visitZeroCheckExpr(ZeroCheckExpr expr) {
    }

    public void visitRCExpr(RCExpr expr) {
    }

    public void visitUCExpr(UCExpr expr) {
    }

    public void visitReturnAddressExpr(ReturnAddressExpr expr) {
    }

    public void visitShiftExpr(ShiftExpr expr) {
        if (this.previous == expr.expr()) {
            this.previous = expr;
            expr.parent().visit(this);
        } else if (this.previous == expr.bits()) {
            this.check(expr.expr());
        }

    }

    public void visitStackExpr(StackExpr expr) {
    }

    public void visitVarExpr(VarExpr expr) {
        if (expr instanceof LocalExpr) {
            this.visitLocalExpr((LocalExpr)expr);
        }

        if (expr instanceof StackExpr) {
            this.visitStackExpr((StackExpr)expr);
        }

    }

    public void visitStaticFieldExpr(StaticFieldExpr expr) {
    }

    public void visitExpr(Expr expr) {
    }

    public void visitNode(Node node) {
    }
}
class StackExpr extends VarExpr {
    public StackExpr(int index, Type type) {
        super(index, type);
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitStackExpr(this);
    }

    public int exprHashCode() {
        return 20 + this.index + this.type.simple().hashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other instanceof StackExpr && ((StackExpr)other).type.simple().equals(this.type.simple()) && ((StackExpr)other).index == this.index;
    }

    public Object clone() {
        return this.copyInto(new StackExpr(this.index, this.type));
    }
}
class ShiftExpr extends Expr {
    int dir;
    Expr expr;
    Expr bits;
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int UNSIGNED_RIGHT = 2;

    public ShiftExpr(int dir, Expr expr, Expr bits, Type type) {
        super(operand.index(), type);
        this.dir = dir;
        this.expr = expr;
        this.bits = bits;
        expr.setParent(this);
        bits.setParent(this);
    }

    public int dir() {
        return this.dir;
    }

    public Expr expr() {
        return this.expr;
    }

    public Expr bits() {
        return this.bits;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.bits.visit(visitor);
            this.expr.visit(visitor);
        } else {
            this.expr.visit(visitor);
            this.bits.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitShiftExpr(this);
    }

    public int exprHashCode() {
        return 19 + this.dir ^ this.expr.exprHashCode() ^ this.bits.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof ShiftExpr && ((ShiftExpr)other).dir == this.dir && ((ShiftExpr)other).expr.equalsExpr(this.expr) && ((ShiftExpr)other).bits.equalsExpr(this.bits);
    }

    public Object clone() {
        return this.copyInto(new ShiftExpr(this.dir, (Expr)this.expr.clone(), (Expr)this.bits.clone(), this.type));
    }
}
class ReturnAddressExpr extends Expr {
    public ReturnAddressExpr(Type type) {
        super(operand.index(), type);
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitReturnAddressExpr(this);
    }

    public int exprHashCode() {
        return 18;
    }

    public boolean equalsExpr(Expr other) {
        return false;
    }

    public Object clone() {
        return this.copyInto(new ReturnAddressExpr(this.type));
    }
}
class UCExpr extends CheckExpr {
    public static final int POINTER = 1;
    public static final int SCALAR = 2;
    int kind;

    public UCExpr(Expr expr, int kind, Type type) {
        super(expr, type);
        this.kind = kind;
    }

    public int kind() {
        return this.kind;
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitUCExpr(this);
    }

    public boolean equalsExpr(Expr other) {
        return other instanceof UCExpr && super.equalsExpr(other) && ((UCExpr)other).kind == this.kind;
    }

    public Object clone() {
        return this.copyInto(new UCExpr((Expr)this.expr.clone(), this.kind, this.type));
    }
}
abstract class CheckExpr extends Expr {
    Expr expr;

    public CheckExpr(Expr expr, Type type) {
        super(operand.index(), type);
        this.expr = expr;
        expr.setParent(this);
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.expr.visit(visitor);
        } else {
            this.expr.visit(visitor);
        }

    }

    public Expr expr() {
        return this.expr;
    }

    public int exprHashCode() {
        return 9 + this.expr.exprHashCode() ^ this.type.simple().hashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof CheckExpr && ((CheckExpr)other).expr.equalsExpr(this.expr);
    }
}
class RCExpr extends CheckExpr {
    public RCExpr(Expr expr, Type type) {
        super(expr, type);
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitRCExpr(this);
    }

    public boolean equalsExpr(Expr other) {
        return other instanceof RCExpr && super.equalsExpr(other);
    }

    public Object clone() {
        return this.copyInto(new RCExpr((Expr)this.expr.clone(), this.type));
    }
}
class ZeroCheckExpr extends CheckExpr {
    public ZeroCheckExpr(Expr expr, Type type) {
        super(expr, type);
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitZeroCheckExpr(this);
    }

    public boolean equalsExpr(Expr other) {
        return other instanceof ZeroCheckExpr && super.equalsExpr(other);
    }

    public Object clone() {
        return this.copyInto(new ZeroCheckExpr((Expr)this.expr.clone(), this.type));
    }
}
class NewMultiArrayExpr extends Expr {
    Expr[] dimensions;
    Type elementType;

    public NewMultiArrayExpr(Expr[] dimensions, Type elementType, Type type) {
        super(operand.index(), type);
        this.elementType = elementType;
        this.dimensions = dimensions;

        for(int i = 0; i < dimensions.length; ++i) {
            dimensions[i].setParent(this);
        }

    }

    public Expr[] dimensions() {
        return this.dimensions;
    }

    public Type elementType() {
        return this.elementType;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        int i;
        if (visitor.reverse()) {
            for(i = this.dimensions.length - 1; i >= 0; --i) {
                this.dimensions[i].visit(visitor);
            }
        } else {
            for(i = 0; i < this.dimensions.length; ++i) {
                this.dimensions[i].visit(visitor);
            }
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitNewMultiArrayExpr(this);
    }

    public int exprHashCode() {
        int v = 17;

        for(int i = 0; i < this.dimensions.length; ++i) {
            v ^= this.dimensions[i].hashCode();
        }

        return v;
    }

    public boolean equalsExpr(Expr other) {
        return false;
    }

    public Object clone() {
        Expr[] d = new Expr[this.dimensions.length];

        for(int i = 0; i < this.dimensions.length; ++i) {
            d[i] = (Expr)this.dimensions[i].clone();
        }

        return this.copyInto(new NewMultiArrayExpr(d, this.elementType, this.type));
    }
}
class NewExpr extends Expr {
    Type objectType;

    public NewExpr(Type objectType, Type type) {
        super(operand.index(), type);
        this.objectType = objectType;
    }

    public Type objectType() {
        return this.objectType;
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitNewExpr(this);
    }

    public int exprHashCode() {
        return 16 + this.objectType.hashCode();
    }

    public boolean equalsExpr(Expr other) {
        return false;
    }

    public Object clone() {
        return this.copyInto(new NewExpr(this.objectType, this.type));
    }
}
class NewArrayExpr extends Expr {
    Expr size;
    Type elementType;

    public NewArrayExpr(Expr size, Type elementType, Type type) {
        super(operand.index(), type);
        this.size = size;
        this.elementType = elementType;
        size.setParent(this);
    }

    public Expr size() {
        return this.size;
    }

    public Type elementType() {
        return this.elementType;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.size.visit(visitor);
        } else {
            this.size.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitNewArrayExpr(this);
    }

    public int exprHashCode() {
        return 15 + this.size.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return false;
    }

    public Object clone() {
        return this.copyInto(new NewArrayExpr((Expr)this.size.clone(), this.elementType, this.type));
    }
}
class NegExpr extends Expr {
    Expr expr;

    public NegExpr(Expr expr, Type type) {
        super(operand.index(), type);
        this.expr = expr;
        expr.setParent(this);
    }

    public Expr expr() {
        return this.expr;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.expr.visit(visitor);
        } else {
            this.expr.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitNegExpr(this);
    }

    public int exprHashCode() {
        return 14 + this.expr.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof NegExpr && ((NegExpr)other).expr.equalsExpr(this.expr);
    }

    public Object clone() {
        return this.copyInto(new NegExpr((Expr)this.expr.clone(), this.type));
    }
}
class InstanceOfExpr extends CondExpr {
    Expr expr;
    Type checkType;

    public InstanceOfExpr(Expr expr, Type checkType, Type type) {
        super(type);
        this.expr = expr;
        this.checkType = checkType;
        expr.setParent(this);
    }

    public Expr expr() {
        return this.expr;
    }

    public Type checkType() {
        return this.checkType;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.expr.visit(visitor);
        } else {
            this.expr.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitInstanceOfExpr(this);
    }

    public int exprHashCode() {
        return 12 + this.expr.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof InstanceOfExpr && ((InstanceOfExpr)other).checkType.equals(this.checkType) && ((InstanceOfExpr)other).expr.equalsExpr(this.expr);
    }

    public Object clone() {
        return this.copyInto(new InstanceOfExpr((Expr)this.expr.clone(), this.checkType, this.type));
    }
}
abstract class CondExpr extends Expr {
    public CondExpr(Type type) {
        super(operand.index(), type);
    }
}

class StoreExpr extends Expr {
    MemExpr target;
    Expr expr;

    public StoreExpr(MemExpr target, Expr expr, Type type) {
        super(operand.index(), type);
        this.target = target;
        this.expr = expr;
        target.setParent(this);
        expr.setParent(this);
    }

    public DefExpr[] defs() {
        return new DefExpr[]{this.target};
    }

    public MemExpr target() {
        return this.target;
    }

    public Expr expr() {
        return this.expr;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.target.visitOnly(visitor);
            this.expr.visit(visitor);
            this.target.visitChildren(visitor);
        } else {
            this.target.visitChildren(visitor);
            this.expr.visit(visitor);
            this.target.visitOnly(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitStoreExpr(this);
    }

    public int exprHashCode() {
        return 22 + this.target.exprHashCode() ^ this.expr.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other instanceof StoreExpr && ((StoreExpr)other).target.equalsExpr(this.target) && ((StoreExpr)other).expr.equalsExpr(this.expr);
    }

    public Object clone() {
        return this.copyInto(new StoreExpr((MemExpr)this.target.clone(), (Expr)this.expr.clone(), this.type));
    }
}
class Type1UpVisitor extends AscendVisitor {
    Node turningPoint;
    boolean found;

    Type1UpVisitor(Hashtable defInfoMap, Hashtable useInfoMap) {
        super(defInfoMap, useInfoMap);
    }

    public void search(Node turningPoint, LocalExpr start) {
        this.found = false;
        this.start = start;
        this.previous = turningPoint;
        this.turningPoint = turningPoint;
        if (turningPoint.parent() != null && !(turningPoint.parent() instanceof Tree)) {
            turningPoint.parent().visit(this);
        }

        if (!this.found) {
            DefInformation var10000 = (DefInformation)this.defInfoMap.get(start.def());
            var10000.type1s += 3;
        }

    }

    public void check(Node node) {
        if (node instanceof ExprStmt) {
            this.check(((ExprStmt)node).expr());
        } else if (node instanceof StoreExpr) {
            this.check(((StoreExpr)node).target());
        } else if (node instanceof LocalExpr && ((LocalExpr)node).index() == this.start.index() && ((LocalExpr)node).def() == this.start.def()) {
            ((UseInformation)this.useInfoMap.get(this.start)).type = 1;
            ++((UseInformation)this.useInfoMap.get(node)).type1s;
            ++((DefInformation)this.defInfoMap.get(this.start.def())).type1s;
            this.found = true;
            return;
        }

    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }
}
class Type1DownVisitor extends DescendVisitor {
    public Type1DownVisitor(Hashtable useInfoMap, Hashtable defInfoMap) {
        super(useInfoMap, defInfoMap);
    }

    public void visitLocalExpr(LocalExpr expr) {
        if (expr.index() == this.start.index() && expr.def() == this.start.def()) {
            ((UseInformation)this.useInfoMap.get(this.start)).type = 1;
            UseInformation ui = (UseInformation)this.useInfoMap.get(expr);
            ++ui.type1s;
            if (this.exchangeFactor == 1) {
                ++ui.type1_x1s;
            }

            if (this.exchangeFactor == 2) {
                ++ui.type1_x2s;
            }

            ++((DefInformation)this.defInfoMap.get(expr.def())).type1s;
            this.found = true;
        }

    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }
}
class ConstantExpr extends Expr implements LeafExpr {
    Object value;

    public ConstantExpr(Object value, Type type) {
        super(operand.index(), type);
        this.value = value;
    }

    public Object value() {
        return this.value;
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitConstantExpr(this);
    }

    public int exprHashCode() {
        return this.value != null ? 10 + this.value.hashCode() : 10;
    }

    public boolean equalsExpr(Expr other) {
        if (!(other instanceof ConstantExpr)) {
            return false;
        } else if (this.value == null) {
            return ((ConstantExpr)other).value == null;
        } else {
            return ((ConstantExpr)other).value == null ? false : ((ConstantExpr)other).value.equals(this.value);
        }
    }

    public Object clone() {
        return this.copyInto(new ConstantExpr(this.value, this.type));
    }
}
class CastExpr extends Expr {
    Expr expr;
    Type castType;

    public CastExpr(Expr expr, Type type) {
        this(expr, type, type);
    }

    public CastExpr(Expr expr, Type castType, Type type) {
        super(operand.index(), type);
        this.expr = expr;
        this.castType = castType;
        expr.setParent(this);
    }

    public Expr expr() {
        return this.expr;
    }

    public Type castType() {
        return this.castType;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.expr.visit(visitor);
        } else {
            this.expr.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitCastExpr(this);
    }

    public int exprHashCode() {
        return 7 + this.expr.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof CastExpr && ((CastExpr)other).castType.equals(this.castType) && ((CastExpr)other).expr.equalsExpr(this.expr);
    }

    public Object clone() {
        return this.copyInto(new CastExpr((Expr)this.expr.clone(), this.castType, this.type));
    }
}

abstract class CallExpr extends Expr {
    Expr[] params;
    MemberRef method;
    public int voltaPos;

    public CallExpr(Expr[] params, MemberRef method, Type type) {
        super(operand.index(), type);
        this.params = params;
        this.method = method;

        for(int i = 0; i < params.length; ++i) {
            params[i].setParent(this);
        }

    }

    public MemberRef method() {
        return this.method;
    }

    public Expr[] params() {
        return this.params;
    }
}

class ArrayLengthExpr extends Expr {
    Expr array;

    public ArrayLengthExpr(Expr array, Type type) {
        super(operand.index(), type);
        this.array = array;
        array.setParent(this);
    }

    public Expr array() {
        return this.array;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.array.visit(visitor);
        } else {
            this.array.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitArrayLengthExpr(this);
    }

    public int exprHashCode() {
        return 3 + this.array.exprHashCode() ^ this.type.simple().hashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof ArrayLengthExpr && ((ArrayLengthExpr)other).array.equalsExpr(this.array);
    }

    public Object clone() {
        return this.copyInto(new ArrayLengthExpr((Expr)this.array.clone(), this.type));
    }
}
class ArithExpr extends Expr {
    char operation;
    Expr left;
    Expr right;
    public static final char ADD = '+';
    public static final char SUB = '-';
    public static final char DIV = '/';
    public static final char MUL = '*';
    public static final char REM = '%';
    public static final char AND = '&';
    public static final char IOR = '|';
    public static final char XOR = '^';
    public static final char CMP = '?';
    public static final char CMPL = '<';
    public static final char CMPG = '>';

    public ArithExpr(char operation, Expr left, Expr right, Type type) {
        super(operand.index(), type);
        this.operation = operation;
        this.left = left;
        this.right = right;
        left.setParent(this);
        right.setParent(this);
    }

    public int operation() {
        return this.operation;
    }

    public Expr left() {
        return this.left;
    }

    public Expr right() {
        return this.right;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.right.visit(visitor);
            this.left.visit(visitor);
        } else {
            this.left.visit(visitor);
            this.right.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitArithExpr(this);
    }

    public int exprHashCode() {
        return 1 + this.operation ^ this.left.exprHashCode() ^ this.right.exprHashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof ArithExpr && ((ArithExpr)other).operation == this.operation && ((ArithExpr)other).left.equalsExpr(this.left) && ((ArithExpr)other).right.equalsExpr(this.right);
    }

    public Object clone() {
        return this.copyInto(new ArithExpr(this.operation, (Expr)this.left.clone(), (Expr)this.right.clone(), this.type));
    }
}
class SRStmt extends Stmt {
    Expr array;
    Expr start;
    Expr end;

    public SRStmt(Expr a, Expr s, Expr t) {
        this.array = a;
        this.start = s;
        this.end = t;
        this.array.setParent(this);
        this.start.setParent(this);
        this.end.setParent(this);
    }

    public Expr array() {
        return this.array;
    }

    public Expr start() {
        return this.start;
    }

    public Expr end() {
        return this.end;
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitSRStmt(this);
    }

    public Object clone() {
        return this.copyInto(new SRStmt((Expr)this.array.clone(), (Expr)this.start.clone(), (Expr)this.end.clone()));
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.end.visit(visitor);
            this.start.visit(visitor);
            this.array.visit(visitor);
        } else {
            this.array.visit(visitor);
            this.start.visit(visitor);
            this.end.visit(visitor);
        }

    }
}
class SCStmt extends Stmt {
    Expr array;
    Expr index;
    boolean redundant;

    public SCStmt(Expr a, Expr i) {
        this.array = a;
        this.index = i;
        this.redundant = false;
        this.array.setParent(this);
        this.index.setParent(this);
    }

    public Expr array() {
        return this.array;
    }

    public Expr index() {
        return this.index;
    }

    public boolean redundant() {
        return this.redundant;
    }

    public void set_redundant(boolean val) {
        this.redundant = val;
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitSCStmt(this);
    }

    public Object clone() {
        return this.copyInto(new SCStmt((Expr)this.array.clone(), (Expr)this.index.clone()));
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.index.visit(visitor);
            this.array.visit(visitor);
        } else {
            this.array.visit(visitor);
            this.index.visit(visitor);
        }

    }
}
class ThrowStmt extends JumpStmt {
    Expr expr;

    public ThrowStmt(Expr expr) {
        this.expr = expr;
        expr.setParent(this);
    }

    public Expr expr() {
        return this.expr;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        this.expr.visit(visitor);
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitThrowStmt(this);
    }

    public Object clone() {
        return this.copyInto(new ThrowStmt((Expr)this.expr.clone()));
    }
}
abstract class JumpStmt extends Stmt {
    Set catchTargets = new HashSet();

    public JumpStmt() {
    }

    public Collection catchTargets() {
        return this.catchTargets;
    }

    protected Node copyInto(Node node) {
        ((JumpStmt)node).catchTargets.addAll(this.catchTargets);
        return super.copyInto(node);
    }
}
class SwitchStmt extends JumpStmt {
    Expr index;
    Block defaultTarget;
    Block[] targets;
    int[] values;

    public SwitchStmt(Expr index, Block defaultTarget, Block[] targets, int[] values) {
        this.index = index;
        this.defaultTarget = defaultTarget;
        this.targets = targets;
        this.values = values;
        index.setParent(this);
    }

    public Expr index() {
        return this.index;
    }

    public void setDefaultTarget(Block block) {
        this.defaultTarget = block;
    }

    public Block defaultTarget() {
        return this.defaultTarget;
    }

    public Block[] targets() {
        return this.targets;
    }

    public int[] values() {
        return this.values;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        this.index.visit(visitor);
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitSwitchStmt(this);
    }

    public Object clone() {
        Block[] t = new Block[this.targets.length];
        System.arraycopy(this.targets, 0, t, 0, this.targets.length);
        int[] v = new int[this.values.length];
        System.arraycopy(this.values, 0, v, 0, this.values.length);
        return this.copyInto(new SwitchStmt((Expr)this.index.clone(), this.defaultTarget, t, v));
    }
}
class JsrStmt extends JumpStmt {
    Subroutine sub;
    Block follow;

    public JsrStmt(Subroutine sub, Block follow) {
        this.sub = sub;
        this.follow = follow;
    }

    public void setFollow(Block follow) {
        this.follow = follow;
    }

    public Block follow() {
        return this.follow;
    }

    public Subroutine sub() {
        return this.sub;
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitJsrStmt(this);
    }

    public Object clone() {
        return this.copyInto(new JsrStmt(this.sub, this.follow));
    }
}
class AddressStoreStmt extends Stmt {
    Subroutine sub;

    public AddressStoreStmt(Subroutine sub) {
        this.sub = sub;
    }

    public Subroutine sub() {
        return this.sub;
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitAddressStoreStmt(this);
    }

    public Object clone() {
        return this.copyInto(new AddressStoreStmt(this.sub));
    }
}
class ReturnStmt extends JumpStmt {
    public ReturnStmt() {
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitReturnStmt(this);
    }

    public Object clone() {
        return this.copyInto(new ReturnStmt());
    }
}

class RetStmt extends JumpStmt {
    Subroutine sub;

    public RetStmt(Subroutine sub) {
        this.sub = sub;
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitRetStmt(this);
    }

    public Subroutine sub() {
        return this.sub;
    }

    public Object clone() {
        return this.copyInto(new RetStmt(this.sub));
    }
}
class PhiJoinStmt extends PhiStmt {
    Map operands;
    Block block;

    public PhiJoinStmt(VarExpr target, Block block) {
        super(target);
        this.block = block;
        this.operands = new HashMap();
        Iterator preds = block.graph().preds(block).iterator();

        while(preds.hasNext()) {
            Block pred = (Block)preds.next();
            VarExpr operand = (VarExpr)target.clone();
            this.operands.put(pred, operand);
            operand.setParent(this);
            operand.setDef((DefExpr)null);
        }

    }

    public void setOperandAt(Block block, Expr expr) {
        Expr operand = (Expr)this.operands.get(block);
        if (operand != null) {
            operand.cleanup();
        }

        if (expr != null) {
            this.operands.put(block, expr);
            expr.setParent(this);
        } else {
            this.operands.remove(block);
        }

    }

    public Expr operandAt(Block block) {
        return (Expr)this.operands.get(block);
    }

    public int numOperands() {
        return this.block.graph().preds(this.block).size();
    }

    public Collection preds() {
        return this.block.graph().preds(this.block);
    }

    public Collection operands() {
        if (this.operands != null) {
            this.operands.keySet().retainAll(this.preds());
            return this.operands.values();
        } else {
            return new ArrayList();
        }
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.target.visit(visitor);
        }

        Iterator e = this.operands().iterator();

        while(e.hasNext()) {
            Expr operand = (Expr)e.next();
            operand.visit(visitor);
        }

        if (!visitor.reverse()) {
            this.target.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitPhiJoinStmt(this);
    }
}
class PhiCatchStmt extends PhiStmt {
    ArrayList operands = new ArrayList();

    public PhiCatchStmt(LocalExpr target) {
        super(target);
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.target.visit(visitor);
        }

        for(int i = 0; i < this.operands.size(); ++i) {
            LocalExpr expr = (LocalExpr)this.operands.get(i);
            expr.visit(visitor);
        }

        if (!visitor.reverse()) {
            this.target.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitPhiCatchStmt(this);
    }

    public boolean hasOperandDef(LocalExpr def) {
        for(int i = 0; i < this.operands.size(); ++i) {
            LocalExpr expr = (LocalExpr)this.operands.get(i);
            if (expr.def() == def) {
                return true;
            }
        }

        return false;
    }

    public void addOperand(LocalExpr operand) {
        for(int i = 0; i < this.operands.size(); ++i) {
            LocalExpr expr = (LocalExpr)this.operands.get(i);
        }

        this.operands.add(operand);
        operand.setParent(this);
    }

    public Collection operands() {
        if (this.operands == null) {
            return new ArrayList();
        } else {
            for(int i = 0; i < this.operands.size(); ++i) {
                LocalExpr ei = (LocalExpr)this.operands.get(i);

                for(int j = this.operands.size() - 1; j > i; --j) {
                    LocalExpr ej = (LocalExpr)this.operands.get(j);
                    if (ei.def() == ej.def()) {
                        ej.cleanup();
                        this.operands.remove(j);
                    }
                }
            }

            return this.operands;
        }
    }

    public int numOperands() {
        return this.operands.size();
    }

    public void setOperandAt(int i, Expr expr) {
        Expr old = (Expr)this.operands.get(i);
        old.cleanup();
        this.operands.set(i, expr);
        expr.setParent(this);
    }

    public Expr operandAt(int i) {
        return (Expr)this.operands.get(i);
    }
}
class StackManipStmt extends Stmt {
    StackExpr[] target;
    StackExpr[] source;
    int kind;
    public static final int SWAP = 0;
    public static final int DUP = 1;
    public static final int DUP_X1 = 2;
    public static final int DUP_X2 = 3;
    public static final int DUP2 = 4;
    public static final int DUP2_X1 = 5;
    public static final int DUP2_X2 = 6;

    public StackManipStmt(StackExpr[] target, StackExpr[] source, int kind) {
        this.kind = kind;
        this.target = target;

        int i;
        for(i = 0; i < target.length; ++i) {
            this.target[i].setParent(this);
        }

        this.source = source;

        for(i = 0; i < source.length; ++i) {
            this.source[i].setParent(this);
        }
    }

    public StackManipStmt(LeafExpr.StackExpr[] t, LeafExpr.StackExpr[] s, int kind) {
    }

    public DefExpr[] defs() {
        return this.target;
    }

    public StackExpr[] target() {
        return this.target;
    }

    public StackExpr[] source() { return this.source;}

    public int kind() {
        return this.kind;
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitStackManipStmt(this);
    }

    public void visitForceChildren(TreeVisitor visitor) {
        int i;
        if (visitor.reverse()) {
            for(i = this.target.length - 1; i >= 0; --i) {
                this.target[i].visit(visitor);
            }

            for(i = this.source.length - 1; i >= 0; --i) {
                this.source[i].visit(visitor);
            }
        } else {
            for(i = 0; i < this.source.length; ++i) {
                this.source[i].visit(visitor);
            }

            for(i = 0; i < this.target.length; ++i) {
                this.target[i].visit(visitor);
            }
        }

    }

    public Object clone() {
        LeafExpr.StackExpr[] t = new LeafExpr.StackExpr[this.target.length];

        for(int i = 0; i < this.target.length; ++i) {
            t[i] = (LeafExpr.StackExpr) this.target[i].clone();
        }

        LeafExpr.StackExpr[] s = new LeafExpr.StackExpr[this.source.length];

        for(int i = 0; i < this.source.length; ++i) {
            s[i] = (LeafExpr.StackExpr)this.source[i].clone();
        }

        return this.copyInto(new StackManipStmt(t, s, this.kind));
    }
}
class CatchExpr extends Expr {
    Type catchType;

    public CatchExpr(Type catchType, Type type) {
        super(operand.index(), type);
        this.catchType = catchType;
    }

    public void visitForceChildren(TreeVisitor visitor) {
    }

    public void visit(TreeVisitor visitor) {
        visitor.visitCatchExpr(this);
    }

    public Type catchType() {
        return this.catchType;
    }

    public int exprHashCode() {
        return 8 + this.type.simple().hashCode() ^ this.catchType.hashCode();
    }

    public boolean equalsExpr(Expr other) {
        if (other instanceof CatchExpr) {
            CatchExpr c = (CatchExpr)other;
            if (this.catchType != null) {
                return this.catchType.equals(c.catchType);
            } else {
                return c.catchType == null;
            }
        } else {
            return false;
        }
    }

    public Object clone() {
        return this.copyInto(new CatchExpr(this.catchType, this.type));
    }
}
abstract class PhiStmt extends Stmt {
    VarExpr target;

    public PhiStmt(VarExpr target) {
        this.target = target;
        target.setParent(this);
    }

    public VarExpr target() {
        return this.target;
    }

    public DefExpr[] defs() {
        return new DefExpr[]{this.target};
    }

    public abstract Collection operands();

    public Object clone() {
        throw new RuntimeException();
    }
}

class InitStmt extends Stmt {
    LocalExpr[] targets;

    public InitStmt(LocalExpr[] targets) {
        this.targets = new LocalExpr[targets.length];

        for(int i = 0; i < targets.length; ++i) {
            this.targets[i] = targets[i];
            this.targets[i].setParent(this);
        }

    }

    public LocalExpr[] targets() {
        return this.targets;
    }

    public DefExpr[] defs() {
        return this.targets;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        for(int i = 0; i < this.targets.length; ++i) {
            this.targets[i].visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitInitStmt(this);
    }

    public Object clone() {
        LocalExpr[] t = new LocalExpr[this.targets.length];

        for(int i = 0; i < this.targets.length; ++i) {
            t[i] = (LocalExpr)this.targets[i].clone();
        }

        return this.copyInto(new InitStmt(t));
    }
}























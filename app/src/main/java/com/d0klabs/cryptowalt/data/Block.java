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

import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
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
class Label {
    public static boolean TRACE = false;
    private int index;
    private boolean startsBlock;
    private String comment;

    public Label(int index) {
        this(index, false);
    }

    public Label(int index, boolean startsBlock) {
        this.index = index;
        this.startsBlock = startsBlock;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setStartsBlock(boolean startsBlock) {
        this.startsBlock = startsBlock;
    }

    public boolean startsBlock() {
        return this.startsBlock;
    }

    public int index() {
        return this.index;
    }

    public int hashCode() {
        return this.index;
    }

    public boolean equals(Object obj) {
        return obj instanceof Label && ((Label)obj).index == this.index;
    }

    public String toString() {
        return this.comment != null ? "label_" + this.index + " (" + this.comment + ")" : "label_" + this.index;
    }
}
public class StackOptimizer {
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
            int toReturn = toReturn + (UI.type0s - UI.type0_x1s - UI.type0_x2s);
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
            if (DEBUG) {
                System.err.println("Error in StackOptimizer.dup_x1s: parameter not found in useInfoMap");
            }

            return toReturn;
        } else {
            int toReturn = toReturn + UI.type0_x1s;
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
            if (DEBUG) {
                System.err.println("Error in StackOptimizer.dup_x2s: parameter not found in useInfoMap");
            }

            return toReturn;
        } else {
            int toReturn = toReturn + UI.type0_x2s;
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
                if (DEBUG) {
                    System.err.println("Error in StackOptimizer.onStack: parameter not found in useInfoMap");
                }

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
        System.err.println(expr.toString());
        System.err.println(expr.parent().toString() + "-" + expr.parent().parent().toString());
        if (expr.parent().parent().parent() != null && expr.parent().parent().parent().parent() != null) {
            System.err.println(expr.parent().parent().parent().toString() + "-" + expr.parent().parent().parent().parent().toString());
        }

        if (DI == null) {
            System.err.println("not a definition");
            if (expr.def() == null) {
                System.err.println("has no definition (is parameter?)");
            } else {
                System.err.println("has definition " + expr.def());
            }
        } else {
            System.err.println("a definition with " + DI.type1s + " type1s total");
            System.err.println("uses: " + DI.uses);
            System.err.println("uses found: " + DI.usesFound);
            if (this.shouldStore(expr)) {
                System.err.println("should store");
            }
        }

        if (UI == null) {
            System.err.println("No use information entry. trouble");
        } else {
            if (DI == null) {
                System.err.println("type on stack: " + UI.type);
            }

            System.err.println("type0s for this instance: " + UI.type0s);
            System.err.println("of above, number of x1s: " + UI.type0_x1s);
            System.err.println("of above, number of x2s: " + UI.type0_x2s);
            System.err.println("type1s for this instance: " + UI.type1s);
            System.err.println("of above, number of x1s: " + UI.type1_x1s);
            System.err.println("of above, number of x2s: " + UI.type1_x2s);
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
}
public abstract class DescendVisitor extends TreeVisitor {
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

    public void visitGotoStmt(GotoStmt stmt) {
    }

    public void visitLabelStmt(LabelStmt stmt) {
    }

    public void visitMonitorStmt(MonitorStmt stmt) {
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
public class Type0Visitor extends AscendVisitor {
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
}
public abstract class AscendVisitor extends TreeVisitor {
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

    public void visitGotoStmt(GotoStmt stmt) {
    }

    public void visitLabelStmt(LabelStmt stmt) {
    }

    public void visitMonitorStmt(MonitorStmt stmt) {
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
        super(type);
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
        super(type);
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
        super(type);
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
public class NewMultiArrayExpr extends Expr {
    Expr[] dimensions;
    Type elementType;

    public NewMultiArrayExpr(Expr[] dimensions, Type elementType, Type type) {
        super(type);
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
        super(type);
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
        super(type);
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
        super(type);
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
        super(type);
    }
}
class FieldExpr extends MemRefExpr {
    Expr object;
    MemberRef field;

    public FieldExpr(Expr object, MemberRef field, Type type) {
        super(type);
        this.object = object;
        this.field = field;
        object.setParent(this);
    }

    public Expr object() {
        return this.object;
    }

    public MemberRef field() {
        return this.field;
    }

    public void visitForceChildren(TreeVisitor visitor) {
        if (visitor.reverse()) {
            this.object.visit(visitor);
        } else {
            this.object.visit(visitor);
        }

    }

    public void visit(TreeVisitor visitor) {
        visitor.visitFieldExpr(this);
    }

    public int exprHashCode() {
        return 11 + this.object.exprHashCode() ^ this.type.simple().hashCode();
    }

    public boolean equalsExpr(Expr other) {
        return other != null && other instanceof FieldExpr && ((FieldExpr)other).field.equals(this.field) && ((FieldExpr)other).object.equalsExpr(this.object);
    }

    public Object clone() {
        return this.copyInto(new FieldExpr((Expr)this.object.clone(), this.field, this.type));
    }
}
class MemberRef {
    private Type declaringClass;
    private NameAndType nameAndType;

    public MemberRef(Type declaringClass, NameAndType nameAndType) {
        this.declaringClass = declaringClass;
        this.nameAndType = nameAndType;
    }

    public Type declaringClass() {
        return this.declaringClass;
    }

    public String name() {
        return this.nameAndType.name();
    }

    public Type type() {
        return this.nameAndType.type();
    }

    public NameAndType nameAndType() {
        return this.nameAndType;
    }

    public String toString() {
        String className = this.declaringClass.toString();
        return "<" + (this.type().isMethod() ? "Method" : "Field") + " " + className + "." + this.name() + " " + this.type() + ">";
    }

    public boolean equals(Object obj) {
        return obj instanceof MemberRef && ((MemberRef)obj).declaringClass.equals(this.declaringClass) && ((MemberRef)obj).nameAndType.equals(this.nameAndType);
    }

    public int hashCode() {
        return this.declaringClass.hashCode() ^ this.nameAndType.hashCode();
    }
}
class NameAndType {
    private String name;
    private Type type;

    public NameAndType(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return this.name;
    }

    public Type type() {
        return this.type;
    }

    public String toString() {
        return "<NameandType " + this.name + " " + this.type + ">";
    }

    public boolean equals(Object obj) {
        return obj instanceof NameAndType && ((NameAndType)obj).name.equals(this.name) && ((NameAndType)obj).type.equals(this.type);
    }

    public int hashCode() {
        return this.name.hashCode() ^ this.type.hashCode();
    }
}
class ExprStmt extends Stmt {
    Expr expr;

    public ExprStmt(Expr expr) {
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
        visitor.visitExprStmt(this);
    }

    public Object clone() {
        return this.copyInto(new ExprStmt((Expr)this.expr.clone()));
    }
}
public class Type1Visitor extends AscendVisitor {
    Node turningPoint;
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
                DefInformation var10000 = (DefInformation)this.defInfoMap.get(start.def());
                var10000.type1s += 3;
            }
        }

    }

    public void check(Node node) {
        if (node instanceof Expr && ((Expr)node).type().isWide()) {
            this.turningPoint = null;
        } else {
            this.turningPoint = node;
            if (node instanceof StoreExpr) {
                this.check(((StoreExpr)node).expr());
            } else if (!(node instanceof LocalExpr) && node instanceof Expr) {
                this.found = (new Type1DownVisitor(this.useInfoMap, this.defInfoMap)).search(node, this.start);
            }

        }
    }
}
class StoreExpr extends Expr {
    MemExpr target;
    Expr expr;

    public StoreExpr(MemExpr target, Expr expr, Type type) {
        super(type);
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
}
















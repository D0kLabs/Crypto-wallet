package com.d0klabs.cryptowalt.data;

import com.d0klabs.cryptowalt.data.LeafExpr.DefExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.Expr;
import com.d0klabs.cryptowalt.data.LeafExpr.GotoStmt;
import com.d0klabs.cryptowalt.data.LeafExpr.IfZeroStmt;
import com.d0klabs.cryptowalt.data.LeafExpr.LabelStmt;
import com.d0klabs.cryptowalt.data.LeafExpr.MemExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.MonitorStmt;
import com.d0klabs.cryptowalt.data.LeafExpr.Node;
import com.d0klabs.cryptowalt.data.LeafExpr.OperandStack;
import com.d0klabs.cryptowalt.data.LeafExpr.PrintVisitor;
import com.d0klabs.cryptowalt.data.LeafExpr.StaticFieldExpr;
import com.d0klabs.cryptowalt.data.LeafExpr.Stmt;
import com.d0klabs.cryptowalt.data.LeafExpr.Tree;
import com.d0klabs.cryptowalt.data.LeafExpr.TreeVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

public class FlowGraph extends Graph {
    public static final int PEEL_NO_LOOPS = 0;
    public static final int PEEL_ALL_LOOPS = -1;
    public static int PEEL_LOOPS_LEVEL = 1;
    public static boolean DEBUG = false;
    public static boolean DB_GRAPHS = false;
    public static boolean PRINT_GRAPH = false;
    MethodEditor method;
    Map subroutines;
    List catchBlocks;
    Map handlers;
    Block srcBlock;
    Block snkBlock;
    Block iniBlock;
    List trace;
    Graph loopTree;
    int domEdgeModCount;
    int loopEdgeModCount;
    int maxLoopDepth = 0;
    int file = 0;
    int next = 1;

    private void db(String s) {
        if (DEBUG || DB_GRAPHS) {
            System.out.println(s);
        }

    }

    public FlowGraph(MethodEditor method) {
        this.method = method;
        this.subroutines = new HashMap();
        this.catchBlocks = new ArrayList(method.tryCatches().size());
        this.handlers = new HashMap(method.tryCatches().size() * 2 + 1);
        this.trace = new LinkedList();
        this.srcBlock = this.newBlock();
        this.iniBlock = this.newBlock();
        this.snkBlock = this.newBlock();
        this.trace.add(this.iniBlock);
        if (method.codeLength() == 0) {
            this.addEdge(this.srcBlock, this.iniBlock);
            this.addEdge(this.iniBlock, this.snkBlock);
            this.addEdge(this.srcBlock, this.snkBlock);
            this.buildSpecialTrees((Map)null, (Map)null);
        } else {
            Map labelPos = new HashMap();
            this.buildBlocks(labelPos);
            this.removeUnreachable();
            this.saveLabels();
            if (DEBUG || DB_GRAPHS) {
                System.out.println("---------- After building tree:");
                this.print(System.out);
                System.out.println("---------- end print after building tree");
            }

        }
    }

    public int maxLoopDepth() {
        return this.maxLoopDepth;
    }

    public void initialize() {
        if (this.method.codeLength() == 0) {
            this.computeDominators();
            this.buildLoopTree();
        } else {
            this.computeDominators();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After computing dominators (Begin)");
                this.print(System.out);
                this.db("------ After computing dominators (End)");
            }

            this.splitPhiBlocks();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After splitting phi blocks (Begin)");
                this.print(System.out);
                this.db("------ After splitting phi blocks (End)");
            }

            this.removeUnreachable();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After removing unreachable 1 (Begin)");
                this.print(System.out);
                this.db("------ After removing unreachable 1 (End)");
            }

            this.splitIrreducibleLoops();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After splitting irreduciable loops (Begin)");
                this.print(System.out);
                this.db("------ After splitting irreducible loops (End)");
            }

            this.removeUnreachable();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After removing unreachable 2 (Begin)");
                this.print(System.out);
                this.db("------ After removing unreachable 2 (End)");
            }

            this.splitReducibleLoops();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After splitting reducible loops (Begin)");
                this.print(System.out);
                this.db("------ After splitting reducible loops (End)");
            }

            this.removeUnreachable();
            if (DEBUG || DB_GRAPHS) {
                this.db("------ After removing unreachable 3 (Begin)");
                this.print(System.out);
                this.db("------ After removing unreachable 3 (End)");
            }

            this.buildLoopTree();
            this.peelLoops(PEEL_LOOPS_LEVEL);
            this.removeCriticalEdges();
            this.removeUnreachable();
            this.insertConditionalStores();
            this.insertProtectedRegionStores();
            if (DEBUG) {
                System.out.println("---------- After splitting loops:");
                this.print(System.out);
                System.out.println("---------- end print after splitting loops");
            }

        }
    }

    public Graph loopTree() {
        if (this.loopEdgeModCount != this.edgeModCount) {
            this.buildLoopTree();
        }

        return this.loopTree;
    }

    private void buildLoopTree() {
        this.db("  Building loop tree");
        this.loopEdgeModCount = this.edgeModCount;
        this.removeUnreachable();
        this.setBlockTypes();
        final FlowGraph.LoopNode root = new FlowGraph.LoopNode(this.srcBlock);
        this.loopTree = new Graph() {
            public Collection roots() {
                ArrayList r = new ArrayList(1);
                r.add(root);
                return r;
            }
        };
        this.loopTree.addNode(this.srcBlock, root);
        Iterator iter = this.nodes().iterator();

        FlowGraph.LoopNode headerLoop;
        FlowGraph.LoopNode loop;
        while(iter.hasNext()) {
            Block block = (Block)iter.next();
            Block header = block.header();
            if (header != null) {
                headerLoop = (FlowGraph.LoopNode)this.loopTree.getNode(header);
                if (headerLoop == null) {
                    headerLoop = new FlowGraph.LoopNode(header);
                    this.loopTree.addNode(header, headerLoop);
                }

                headerLoop.elements.add(block);
                if (block.blockType() != 0) {
                    loop = (FlowGraph.LoopNode)this.loopTree.getNode(block);
                    if (loop == null) {
                        loop = new FlowGraph.LoopNode(block);
                        this.loopTree.addNode(block, loop);
                    }

                    this.loopTree.addEdge(headerLoop, loop);
                }
            }
        }

        int level;
        for(iter = this.loopTree.postOrder().iterator(); iter.hasNext(); loop.level = level + 1) {
            loop = (FlowGraph.LoopNode)iter.next();
            level = 0;
            Iterator succs = this.loopTree.succs(loop).iterator();

            while(succs.hasNext()) {
                loop = (FlowGraph.LoopNode)succs.next();
                if (level < loop.level) {
                    level = loop.level;
                }
            }
        }

        iter = this.loopTree.preOrder().iterator();

        while(iter.hasNext()) {
            loop = (FlowGraph.LoopNode)iter.next();
            Iterator preds = this.loopTree.preds(loop).iterator();
            if (preds.hasNext()) {
                headerLoop = (FlowGraph.LoopNode)preds.next();
                loop.depth = headerLoop.depth + 1;
            } else {
                loop.depth = 0;
            }
        }

    }

    private void buildBlocks(Map labelPos) {
        this.db("  Building blocks");
        ListIterator iter = this.method.code().listIterator();

        while(iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Label) {
                Label label = (Label)obj;
                if (label.startsBlock()) {
                    this.trace.add(this.newBlock(label));
                }
            }
        }

        InstructionVisitor.Instruction lastInst = null;
        Block currBlock = this.iniBlock;
        Block firstBlock = null;
        int i = 0;

        for(iter = this.method.code().listIterator(); iter.hasNext(); ++i) {
            Object curr = iter.next();
            Block target;
            Subroutine sub;
            if (curr instanceof Label) {
                Label label = (Label)curr;
                if (label.startsBlock()) {
                    labelPos.put(label, new Integer(i));
                    Block nextBlock = (Block)this.getNode(label);
                    if (lastInst != null && lastInst.isJsr()) {
                        target = (Block)this.getNode(lastInst.operand());
                        sub = (Subroutine)this.subroutines.get(target);
                        sub.addPath(currBlock, nextBlock);
                    }

                    currBlock = nextBlock;
                    if (firstBlock == null) {
                        firstBlock = nextBlock;
                    }
                }
            } else {
                if (!(curr instanceof InstructionVisitor.Instruction)) {
                    throw new IllegalArgumentException();
                }

                InstructionVisitor.Instruction currInst = (InstructionVisitor.Instruction)curr;
                lastInst = currInst;
                if (currInst.isJsr()) {
                    Label label = (Label)currInst.operand();
                    target = (Block)this.getNode(label);
                    if (!this.subroutines.containsKey(target)) {
                        sub = new Subroutine(this);
                        this.setSubEntry(sub, target);
                    }
                }
            }
        }

        this.buildTrees(firstBlock, labelPos);
    }

    private void buildTrees(Block firstBlock, Map labelPos) {
        this.db("  Building trees for " + firstBlock);
        HashMap catchBodies = new HashMap(this.method.tryCatches().size() * 2 + 1);
        Iterator tryCatches = this.method.tryCatches().iterator();

        label40:
        while(tryCatches.hasNext()) {
            TryCatch tc = (TryCatch)tryCatches.next();
            Block catchBlock = this.newBlock();
            Block catchBody = (Block)this.getNode(tc.handler());
            catchBodies.put(catchBlock, catchBody);
            Integer pos = (Integer)labelPos.get(tc.handler());
            labelPos.put(catchBlock.label(), pos);
            this.addEdge(catchBlock, catchBody);
            this.trace.add(this.trace.indexOf(catchBody), catchBlock);
            Type type = tc.type();
            if (type == null) {
                type = Type.NULL;
            }

            this.catchBlocks.add(catchBlock);
            StackExpr lhs = new StackExpr(0, Type.THROWABLE);
            CatchExpr rhs = new CatchExpr(type, Type.THROWABLE);
            StoreExpr store = new StoreExpr(lhs, rhs, Type.THROWABLE);
            LeafExpr.Tree tree = new LeafExpr.Tree(catchBlock, new LeafExpr.OperandStack());
            catchBlock.setTree(tree);
            tree.addStmt(new ExprStmt(store));
            tree.addStmt(new LeafExpr.GotoStmt(catchBody));
            Integer start = (Integer)labelPos.get(tc.start());
            Integer end = (Integer)labelPos.get(tc.end());
            Handler handler = new Handler(catchBlock, type);
            this.handlers.put(catchBlock, handler);
            Iterator blocks = this.nodes().iterator();

            while(true) {
                Block block;
                do {
                    do {
                        do {
                            if (!blocks.hasNext()) {
                                continue label40;
                            }

                            block = (Block)blocks.next();
                            pos = (Integer)labelPos.get(block.label());
                        } while(pos == null);
                    } while(start > pos);
                } while(end != null && pos >= end);

                handler.protectedBlocks().add(block);
            }
        }

        this.addEdge(this.srcBlock, this.iniBlock);
        this.addEdge(this.srcBlock, this.snkBlock);
        this.addEdge(this.iniBlock, firstBlock);
        this.buildSpecialTrees(catchBodies, labelPos);
        this.buildTreeForBlock(firstBlock, this.iniBlock.tree().stack(), (Subroutine)null, labelPos, catchBodies);
    }

    private void insertConditionalStores() {
        this.db("  Inserting conditional stores");
        ImmutableIterator blocks = new ImmutableIterator(this.nodes());

        while(true) {
            while(true) {
                Block target;
                Object left;
                LocalExpr tmp;
                Object right;
                Expr copy;
                ExprStmt insert;
                do {
                    do {
                        IfCmpStmt stmt;
                        do {
                            label113:
                            do {
                                while(blocks.hasNext()) {
                                    Block block = (Block)blocks.next();
                                    Stmt last = block.tree().lastStmt();
                                    if (last instanceof IfCmpStmt) {
                                        stmt = (IfCmpStmt)last;
                                        target = null;
                                        continue label113;
                                    }

                                    if (last instanceof LeafExpr.IfZeroStmt) {
                                        stmt = (IfCmpStmt) last;
                                        target = null;
                                        if (stmt.trueTarget() != stmt.falseTarget()) {
                                            if (stmt.comparison() == 0) {
                                                target = stmt.trueTarget();
                                            } else if (stmt.comparison() == 1) {
                                                target = stmt.falseTarget();
                                            }

                                            if (target != null) {
                                                left = stmt.expr();
                                                if (!((LeafExpr.Expr)left).type().isReference()) {
                                                    if (!(left instanceof LeafExpr)) {
                                                        LocalVariable v = this.method.newLocal(((LeafExpr.Expr)left).type());
                                                        tmp = new LocalExpr(v.index(), ((LeafExpr.Expr)left).type());
                                                        copy = (LocalExpr)((LeafExpr.Expr)left).clone();
                                                        copy.setDef((LeafExpr.DefExpr)null);
                                                        ((LeafExpr.Expr)left).replaceWith(new StoreExpr(tmp, copy, ((LeafExpr.Expr)left).type()));
                                                        left = tmp;
                                                    }

                                                    Object value = null;
                                                    Type type = ((LeafExpr.Expr)left).type();
                                                    if (((LeafExpr.Expr)left).type().isIntegral()) {
                                                        value = new Integer(0);
                                                    } else {
                                                    }

                                                    if (left instanceof LocalExpr) {
                                                        copy = (LocalExpr)((LeafExpr.Expr)left).clone();
                                                        copy.setDef((LeafExpr.DefExpr)null);
                                                        insert = new ExprStmt(new StoreExpr((MemExpr) copy, new ConstantExpr(value, type), ((LeafExpr.Expr)left).type()));
                                                        target.tree().prependStmt(insert);
                                                    } else {
                                                    }
                                                }
                                            }
                                        }
                                    } else if (last instanceof SwitchStmt) {
                                        stmt = (IfCmpStmt) last;
                                        LeafExpr.Expr index = stmt.index();
                                        if (!(index instanceof LeafExpr)) {
                                            LocalVariable v = this.method.newLocal(((LeafExpr.Expr)index).type());
                                            tmp = new LocalExpr(v.index(), ((LeafExpr.Expr) index).type());
                                            copy = (Expr)((Expr)index).clone();
                                            copy.setDef((DefExpr)null);
                                            ((Expr)index).replaceWith(new StoreExpr(tmp, copy, ((Expr)index).type()));
                                            index = tmp;
                                        }

                                        if (index instanceof LocalExpr) {
                                            Block[] targets = stmt.targets();
                                            int[] values = stmt.values();
                                            HashSet seen = new HashSet();
                                            HashSet duplicate = new HashSet();

                                            int i;
                                            for(i = 0; i < targets.length; ++i) {
                                                if (seen.contains(targets[i])) {
                                                    duplicate.add(targets[i]);
                                                } else {
                                                    seen.add(targets[i]);
                                                }
                                            }

                                            for(i = 0; i < targets.length; ++i) {
                                                target = targets[i];
                                                if (!duplicate.contains(target)) {
                                                    this.splitEdge(block, targets[i]);
                                                    copy = (LocalExpr)((Expr)index).clone();
                                                    copy.setDef((DefExpr)null);
                                                    insert = new ExprStmt(new StoreExpr((MemExpr) copy, new ConstantExpr(new Integer(values[i]), ((Expr)index).type()), ((Expr)index).type()));
                                                    targets[i].tree().prependStmt(insert);
                                                }
                                            }
                                        }
                                    }
                                }

                                return;
                            } while(stmt.trueTarget() == stmt.falseTarget());

                            if (stmt.comparison() == 0) {
                                target = stmt.trueTarget();
                            } else if (stmt.comparison() == 1) {
                                target = stmt.falseTarget();
                            }
                        } while(target == null);

                        left = stmt.left();
                        right = stmt.right();
                    } while(((Expr)left).type().isReference());
                } while(((Expr)right).type().isReference());

                LocalVariable v;
                if (!(left instanceof LeafExpr)) {
                    v = this.method.newLocal(((Expr)left).type());
                    copy = new LocalExpr(v.index(), ((Expr)left).type());
                    copy = (Expr)((Expr)left).clone();
                    copy.setDef((DefExpr)null);
                    ((Expr)left).replaceWith(new StoreExpr((MemExpr) copy, copy, ((Expr)left).type()));
                    left = copy;
                }

                if (!(right instanceof LeafExpr)) {
                    v = this.method.newLocal(((Expr)right).type());
                    copy = new LocalExpr(v.index(), ((Expr)right).type());
                    copy = (Expr)((Expr)right).clone();
                    copy.setDef((DefExpr)null);
                    ((Expr)right).replaceWith(new StoreExpr((MemExpr) copy, copy, ((Expr)right).type()));
                    right = copy;
                }

                if (left instanceof LocalExpr) {
                    tmp = (LocalExpr)((Expr)left).clone();
                    tmp.setDef((DefExpr)null);
                    copy = (Expr)((Expr)right).clone();
                    copy.setDef((DefExpr)null);
                    insert = new ExprStmt(new StoreExpr(tmp, copy, ((Expr)left).type()));
                    target.tree().prependStmt(insert);
                } else if (right instanceof LocalExpr) {
                    tmp = (LocalExpr)((Expr)right).clone();
                    tmp.setDef((DefExpr)null);
                    copy = (Expr)((Expr)left).clone();
                    copy.setDef((DefExpr)null);
                    insert = new ExprStmt(new StoreExpr(tmp, copy, ((Expr)right).type()));
                    target.tree().prependStmt(insert);
                } else {
                }
            }
        }
    }

    private void insertProtectedRegionStores() {
        this.db("  Inserting protected region stores");
        HashSet tryPreds = new HashSet();
        Iterator blocks = this.catchBlocks.iterator();

        while(true) {
            Handler handler;
            do {
                if (!blocks.hasNext()) {
                    this.insertProtStores(this.srcBlock, tryPreds, new ResizeableArrayList());
                    return;
                }

                Block block = (Block)blocks.next();
                handler = (Handler)this.handlers.get(block);
            } while(handler == null);

            HashSet p = new HashSet();
            Iterator prots = handler.protectedBlocks().iterator();

            while(prots.hasNext()) {
                Block prot = (Block)prots.next();
                p.addAll(this.preds(prot));
            }

            p.removeAll(handler.protectedBlocks());
            tryPreds.addAll(p);
        }
    }

    private void insertProtStores(Block block, HashSet tryPreds, final ResizeableArrayList defs) {
        final Tree tree = block.tree();
        tree.visitChildren(new TreeVisitor() {
            public void visitLocalExpr(LocalExpr expr) {
                if (expr.isDef()) {
                    int index = expr.index();
                    if (expr.type().isWide()) {
                        defs.ensureSize(index + 2);
                        defs.set(index, expr);
                        defs.set(index + 1, (Object)null);
                    } else {
                        defs.ensureSize(index + 1);
                        defs.set(index, expr);
                    }
                }

            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }
        });
        if (tryPreds.contains(block)) {
            for(int i = 0; i < defs.size(); ++i) {
                LocalExpr expr = (LocalExpr)defs.get(i);
                if (expr != null) {
                    Stmt last = tree.lastStmt();
                    last.visitChildren(new TreeVisitor() {
                        public void visitExpr(Expr expr) {
                            LeafExpr.StackExpr var = tree.newStack(expr.type());
                            var.setValueNumber(expr.valueNumber());
                            Node p = expr.parent();
                            expr.setParent((Node)null);
                            p.visit(new ReplaceVisitor(expr, var));
                            var = (LeafExpr.StackExpr) var.clone();
                            var.setDef((DefExpr)null);
                            StoreExpr store = new StoreExpr(var, expr, expr.type());
                            store.setValueNumber(expr.valueNumber());
                            Stmt storeStmt = new ExprStmt(store);
                            storeStmt.setValueNumber(expr.valueNumber());
                            tree.addStmtBeforeJump(storeStmt);
                        }

                        public void visitStackExpr(StackExpr expr) {
                        }

                        @Override
                        public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                        }
                    });
                    LocalExpr copy1 = (LocalExpr)expr.clone();
                    LocalExpr copy2 = (LocalExpr)expr.clone();
                    copy1.setDef((DefExpr)null);
                    copy2.setDef((DefExpr)null);
                    StoreExpr store = new StoreExpr(copy1, copy2, expr.type());
                    tree.addStmtBeforeJump(new ExprStmt(store));
                }
            }
        }

        Iterator children = this.domChildren(block).iterator();

        while(children.hasNext()) {
            Block child = (Block)children.next();
            this.insertProtStores(child, tryPreds, new ResizeableArrayList(defs));
        }

    }

    private void saveLabels() {
        boolean save = false;
        ListIterator iter = this.method.code().listIterator();

        while(iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Label) {
                Label label = (Label)obj;
                if (label.startsBlock()) {
                    if (this.getNode(label) == null) {
                        save = true;
                    } else {
                        save = false;
                    }
                }

                if (save) {
                    label.setStartsBlock(false);
                    this.iniBlock.tree().addStmt(new LabelStmt(label));
                }
            }
        }

    }

    public void removeSub(Subroutine sub) {
        this.subroutines.remove(sub.entry());
    }

    public void addEdge(GraphNode src, GraphNode dst) {
        if (DEBUG) {
            System.out.println("    ADDING EDGE " + src + " -> " + dst);
        }

        super.addEdge(src, dst);
    }

    public void removeEdge(GraphNode v, GraphNode w) {
        Block src = (Block)v;
        Block dst = (Block)w;
        if (DEBUG) {
            System.out.println("    REMOVING EDGE " + src + " -> " + dst);
        }

        super.removeEdge(src, dst);
        this.cleanupEdge(src, dst);
    }

    private void cleanupEdge(final Block src, Block dst) {
        dst.visit(new TreeVisitor() {
            public void visitPhiJoinStmt(PhiJoinStmt stmt) {
                Expr operand = stmt.operandAt(src);
                if (operand != null) {
                    operand.cleanup();
                    stmt.setOperandAt(src, (Expr)null);
                }

            }

            public void visitStmt(Stmt stmt) {
            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }
        });
    }

    public Block newBlock() {
        return this.newBlock(this.method.newLabel());
    }

    Block newBlock(Label label) {
        Block block = new Block(label, this);
        this.addNode(label, block);
        if (DEBUG) {
            System.out.println("    new block " + block);
        }

        return block;
    }

    private void computeDominators() {
        this.db("  Computing Dominators");
        this.domEdgeModCount = this.edgeModCount;
        this.removeUnreachable();
        DominatorTree.buildTree(this, false);
        DominanceFrontier.buildFrontier(this, false);
        DominatorTree.buildTree(this, true);
        DominanceFrontier.buildFrontier(this, true);
    }

    private void setBlockTypes() {
        this.db("  Setting block types");
        List blocks = this.preOrder();
        Set[] nonBackPreds = new Set[blocks.size()];
        Set[] backPreds = new Set[blocks.size()];
        ListIterator iter = blocks.listIterator();

        Block h;
        while(iter.hasNext()) {
            Block w = (Block)iter.next();
            int wn = this.preOrderIndex(w);
            Set nonBack = new HashSet();
            nonBackPreds[wn] = nonBack;
            Set back = new HashSet();
            backPreds[wn] = back;
            w.setHeader(this.srcBlock);
            w.setBlockType(0);
            Iterator preds = this.preds(w).iterator();

            while(preds.hasNext()) {
                h = (Block)preds.next();
                if (this.isAncestorToDescendent(w, h)) {
                    back.add(h);
                } else {
                    nonBack.add(h);
                }
            }
        }

        this.srcBlock.setHeader((Block)null);
        UnionFind uf = new UnionFind(blocks.size());
        iter = blocks.listIterator(blocks.size());

        while(true) {
            Block x;
            Block w;
            int wn;
            Set nonBack;
            HashSet body;
            do {
                if (!iter.hasPrevious()) {
                    Iterator e = this.subroutines.values().iterator();

                    while(e.hasNext()) {
                        Subroutine sub = (Subroutine)e.next();
                        Iterator paths = sub.paths().iterator();

                        while(paths.hasNext()) {
                            Block[] path = (Block[])paths.next();
                            if (path[0].blockType() != 0) {
                                path[0].setBlockType(1);
                            }

                            if (path[1].blockType() != 0) {
                                path[1].setBlockType(1);
                            }

                            h = path[0].header();
                            if (h != null) {
                                h.setBlockType(1);
                            }

                            h = path[1].header();
                            if (h != null) {
                                h.setBlockType(1);
                            }
                        }
                    }

                    e = this.catchBlocks.iterator();

                    while(e.hasNext()) {
                        Block catchBlock = (Block)e.next();
                        if (catchBlock.blockType() != 0) {
                            catchBlock.setBlockType(1);
                        }

                        h = catchBlock.header();
                        if (h != null) {
                            h.setBlockType(1);
                        }
                    }

                    return;
                }

                w = (Block)iter.previous();
                wn = this.preOrderIndex(w);
                nonBack = nonBackPreds[wn];
                Set back = backPreds[wn];
                body = new HashSet();
                Iterator preds = back.iterator();

                while(preds.hasNext()) {
                    Block v = (Block)preds.next();
                    if (v != w) {
                        int vn = this.preOrderIndex(v);
                        x = (Block)blocks.get(uf.find(vn));
                        body.add(x);
                    } else {
                        w.setBlockType(2);
                    }
                }
            } while(body.size() == 0);

            w.setBlockType(2);
            LinkedList worklist = new LinkedList(body);

            while(!worklist.isEmpty()) {
                x = (Block)worklist.removeFirst();
                int xn = this.preOrderIndex(x);
                Iterator e = nonBackPreds[xn].iterator();

                while(e.hasNext()) {
                    Block y = (Block)e.next();
                    int yn = this.preOrderIndex(y);
                    Block z = (Block)blocks.get(uf.find(yn));
                    if (!this.isAncestorToDescendent(w, z)) {
                        w.setBlockType(1);
                        nonBack.add(z);
                    } else if (!body.contains(z) && z != w) {
                        body.add(z);
                        worklist.add(z);
                    }
                }
            }

            Iterator e = body.iterator();

            while(e.hasNext()) {
                x = (Block)e.next();
                int xn = this.preOrderIndex(x);
                x.setHeader(w);
                uf.union(xn, wn);
            }
        }
    }

    private void splitIrreducibleLoops() {
        this.db("  Splitting irreducible loops");
        List removeEdges = new LinkedList();
        Iterator iter = this.nodes().iterator();

        while(true) {
            Block w;
            boolean hasReducibleBackIn;
            HashSet otherIn;
            do {
                do {
                    if (!iter.hasNext()) {
                        iter = removeEdges.iterator();

                        while(iter.hasNext()) {
                            Block[] edge = (Block[])iter.next();
                            this.splitEdge(edge[0], edge[1]);
                        }

                        return;
                    }

                    w = (Block)iter.next();
                    hasReducibleBackIn = false;
                    otherIn = new HashSet();
                    Iterator preds = this.preds(w).iterator();

                    while(preds.hasNext()) {
                        Block v = (Block)preds.next();
                        if (w.dominates(v)) {
                            hasReducibleBackIn = true;
                        } else {
                            otherIn.add(v);
                        }
                    }
                } while(!hasReducibleBackIn);
            } while(otherIn.size() <= 1);

            Iterator e = otherIn.iterator();

            while(e.hasNext()) {
                Block v = (Block)e.next();
                removeEdges.add(new Block[]{v, w});
            }
        }
    }

    private void splitReducibleLoops() {
        this.db("  Splitting reducible loops");
        Map reducibleBackIn = new HashMap();
        Stack stack = new Stack();
        Iterator iter = this.nodes().iterator();

        Block w;
        while(iter.hasNext()) {
            w = (Block)iter.next();
            Set edges = new HashSet();
            Iterator preds = this.preds(w).iterator();

            while(preds.hasNext()) {
                Block v = (Block)preds.next();
                if (w.dominates(v)) {
                    edges.add(v);
                }
            }

            if (edges.size() > 1 && !this.handlers.containsKey(w)) {
                stack.push(w);
                reducibleBackIn.put(w, edges);
            }
        }

        label68:
        while(!stack.isEmpty()) {
            w = (Block)stack.pop();
            Set edges = (Set)reducibleBackIn.get(w);
            Block min = null;
            Iterator preds = edges.iterator();

            while(true) {
                Block newBlock;
                int vn;
                do {
                    if (!preds.hasNext()) {
                        newBlock = this.newBlock();
                        this.trace.add(this.trace.indexOf(w), newBlock);
                        LeafExpr.Tree tree = new LeafExpr.Tree(newBlock, min.tree().stack());
                        newBlock.setTree(tree);
                        tree.addInstruction(new InstructionVisitor.Instruction(167, w.label()));
                        LeafExpr.JumpStmt newJump = (LeafExpr.JumpStmt)tree.lastStmt();
                        Iterator e = this.handlers.values().iterator();

                        while(e.hasNext()) {
                            Handler handler = (Handler)e.next();
                            if (handler.protectedBlocks().contains(w)) {
                                handler.protectedBlocks().add(newBlock);
                                this.addEdge(newBlock, handler.catchBlock());
                                newJump.catchTargets().add(handler.catchBlock());
                            }
                        }

                        preds = new ImmutableIterator(this.preds(w));

                        while(preds.hasNext()) {
                            Block v = (Block)preds.next();
                            if (v != min) {
                                this.addEdge(v, newBlock);
                                this.removeEdge(v, w);
                                v.visit(new ReplaceTarget(w, newBlock));
                            }
                        }

                        this.addEdge(newBlock, w);
                        edges.remove(min);
                        if (edges.size() > 1) {
                            stack.push(newBlock);
                            reducibleBackIn.put(newBlock, edges);
                        }
                        continue label68;
                    }

                    newBlock = (Block)preds.next();
                    vn = this.preOrderIndex(newBlock);
                } while(min != null && vn >= this.preOrderIndex(min));

                min = newBlock;
            }
        }

    }

    private void peelLoops(int level) {
        if (DEBUG) {
            System.out.println("Peeling loops");
            System.out.println("  loop tree = " + this.loopTree);
        }

        final Set hoistable = new HashSet();
        this.visit(new LeafExpr.TreeVisitor() {
            public void visitNode(LeafExpr.Node node) {
                if (!hoistable.contains(node.block())) {
                    node.visitChildren(this);
                }

            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }

            public void visitCastExpr(CastExpr expr) {
                if (expr.castType().isReference() && expr.expr() instanceof LeafExpr) {
                    hoistable.add(expr.block());
                }

                this.visitNode(expr);
            }

            public void visitArithExpr(ArithExpr expr) {
                if ((expr.operation() == 47 || expr.operation() == 37) && expr.type().isIntegral() && expr.left() instanceof LeafExpr && expr.right() instanceof LeafExpr) {
                    hoistable.add(expr.block());
                }

                this.visitNode(expr);
            }

            public void visitArrayLengthExpr(ArrayLengthExpr expr) {
                if (expr.array() instanceof LeafExpr) {
                    hoistable.add(expr.block());
                }

                this.visitNode(expr);
            }

            public void visitArrayRefExpr(ArrayRefExpr expr) {
                if (expr.array() instanceof LeafExpr && expr.index() instanceof LeafExpr) {
                    hoistable.add(expr.block());
                }

                this.visitNode(expr);
            }

            public void visitFieldExpr(FieldExpr expr) {
                if (expr.object() instanceof LeafExpr) {
                    hoistable.add(expr.block());
                }

                this.visitNode(expr);
            }
        });
        List peel = new ArrayList(this.loopTree.size());
        List headers = new ArrayList(this.loopTree.size());
        List outer = new ArrayList(this.loopTree.size());
        List loops = new ArrayList(this.loopTree.postOrder());

        FlowGraph.LoopNode loop;
        for(int i = 0; i < loops.size(); ++i) {
            loop = (FlowGraph.LoopNode)loops.get(i);
            if (this.loopTree.preds(loop).size() > 0 && loop.header.blockType() != 1) {
                headers.add(loop.header);
                peel.add(new Integer(i));
                loop = null;
                Iterator e = this.loopTree.preds(loop).iterator();
                loop = (FlowGraph.LoopNode)e.next();
                int outerIndex = loops.indexOf(loop);
                outer.add(new Integer(outerIndex));
            }
        }

        int[] levels = new int[loops.size()];

        int i;
        for(i = 0; i < loops.size(); ++i) {
            loop = (FlowGraph.LoopNode)loops.get(i);
            loops.set(i, new ArrayList(loop.elements));
            levels[i] = loop.level;
            this.maxLoopDepth = loop.level > this.maxLoopDepth ? loop.level : this.maxLoopDepth;
        }

        for(i = 0; i < peel.size(); ++i) {
            Integer loopIndex = (Integer)peel.get(i);
            Integer outerLoopIndex = (Integer)outer.get(i);
            Block header = (Block)headers.get(i);
            loop = (LoopNode) loops.get(loopIndex);
            Collection outerLoop = (Collection)loops.get(outerLoopIndex);
            loop.retainAll(this.nodes());
            if (DEBUG) {
                System.out.println("  loop = " + loop);
                System.out.println("  outer = " + outerLoop);
            }

            boolean canPeel = false;
            boolean canInvert = false;
            if (level != 0 && (level == -1 || level >= levels[loopIndex])) {
                Iterator e = loop.iterator();

                while(e.hasNext()) {
                    Block block = (Block)e.next();
                    if (hoistable.contains(block)) {
                        canPeel = true;
                        break;
                    }
                }
            }

            Iterator e;
            Block block;
            if (!canPeel) {
                boolean hasExitSucc = false;
                boolean hasLoopSucc = false;
                e = this.succs(header).iterator();

                while(e.hasNext()) {
                    block = (Block)e.next();
                    if (!loop.contains(block)) {
                        hasExitSucc = true;
                    } else if (block != header) {
                        hasLoopSucc = true;
                    }
                }

                canInvert = hasExitSucc && hasLoopSucc;
            }

            Set copySet = new HashSet();
            Block copy;
            if (canPeel) {
                Set exits = new HashSet();
                exits.addAll(hoistable);
                exits.retainAll((Collection<?>) loop);
                e = ((Collection<?>) loop).iterator();

                while(true) {
                    while(e.hasNext()) {
                        block = (Block)e.next();
                        Iterator succs = this.succs(block).iterator();

                        while(succs.hasNext()) {
                            block = (Block)succs.next();
                            if (!loop.contains(block)) {
                                exits.add(block);
                                break;
                            }
                        }
                    }

                    ArrayList stack = new ArrayList(exits);
                    e = exits.iterator();

                    while(e.hasNext()) {
                        copy = (Block)e.next();
                        copySet.add(copy);
                        stack.add(copy);
                    }

                    while(!stack.isEmpty()) {
                        copy = (Block)stack.remove(stack.size() - 1);
                        Iterator preds = this.preds(copy).iterator();

                        while(preds.hasNext()) {
                            copy = (Block)preds.next();
                            if (!copySet.contains(copy)) {
                                copySet.add(copy);
                                stack.add(copy);
                            }
                        }
                    }
                    break;
                }
            } else {
                if (!canInvert) {
                    if (outerLoop != null) {
                        outerLoop.addAll((Collection) loop);
                    }
                    continue;
                }

                copySet.add(header);
            }

            Map copies = new HashMap();
            e = copySet.iterator();

            while(e.hasNext()) {
                block = (Block)e.next();
                if (DEBUG) {
                    Stmt jump = block.tree().lastStmt();
                    if (jump instanceof JsrStmt) {
                        JsrStmt jsr = (JsrStmt)jump;
                    }
                }

                if (loop.contains(block)) {
                    copy = (Block)copies.get(block);
                    if (copy == null) {
                        copy = this.copyBlock(block);
                        copies.put(block, copy);
                    }

                    if (hoistable.contains(block)) {
                        hoistable.add(copy);
                    }
                }
            }

            if (DEBUG) {
                System.out.println("  copy = " + copies);
            }

            int copyIndex = -1;
            e = this.preds(header).iterator();

            while(e.hasNext()) {
                copy = (Block)e.next();
                if (!header.dominates(copy)) {
                    int index = this.trace.indexOf(copy);
                    if (copyIndex <= index) {
                        copyIndex = index + 1;
                    }
                }
            }

            if (copyIndex < 0) {
                copyIndex = this.trace.indexOf(header);
            }

            List copyTrace = new ResizeableArrayList(copies.size());
            e = this.trace.iterator();

            while(e.hasNext()) {
                block = (Block)e.next();
                copy = (Block)copies.get(block);
                if (copy != null) {
                    copyTrace.add(copy);
                }
            }

            this.trace.addAll(copyIndex, copyTrace);
            List addEdges = new LinkedList();
            List removeEdges = new LinkedList();
            e = copies.entrySet().iterator();

            Entry pair;
            Block v;
            Block w;
            Iterator preds;
            label234:
            while(e.hasNext()) {
                pair = (Entry)e.next();
                v = (Block)pair.getKey();
                w = (Block)pair.getValue();
                preds = this.handlers.values().iterator();

                while(preds.hasNext()) {
                    Handler handler = (Handler)preds.next();
                    if (handler.protectedBlocks().contains(v)) {
                        handler.protectedBlocks().add(w);
                    }
                }

                Iterator succs = this.succs(v).iterator();

                while(true) {
                    while(true) {
                        if (!succs.hasNext()) {
                            continue label234;
                        }

                        Block succ = (Block)succs.next();
                        Block succCopy = (Block)copies.get(succ);
                        if (succ != header && succCopy != null) {
                            addEdges.add(new Block[]{w, succCopy});
                            w.visit(new ReplaceTarget(succ, succCopy));
                        } else {
                            addEdges.add(new Block[]{w, succ});
                        }
                    }
                }
            }

            e = copies.entrySet().iterator();

            while(e.hasNext()) {
                pair = (Entry)e.next();
                v = (Block)pair.getKey();
                w = (Block)pair.getValue();
                preds = this.preds(v).iterator();

                while(preds.hasNext()) {
                    Block pred = (Block)preds.next();
                    if (!loop.contains(pred)) {
                        addEdges.add(new Block[]{pred, w});
                        removeEdges.add(new Block[]{pred, v});
                        pred.visit(new ReplaceTarget(v, w));
                    }
                }
            }

            e = addEdges.iterator();

            Block[] edge;
            while(e.hasNext()) {
                edge = (Block[])e.next();
                this.addEdge(edge[0], edge[1]);
            }

            e = removeEdges.iterator();

            while(e.hasNext()) {
                edge = (Block[])e.next();
                v = edge[0];
                w = edge[1];
                if (this.hasNode(v) && this.hasNode(w) && this.hasEdge(v, w)) {
                    this.removeEdge(v, w);
                }
            }

            if (outerLoop != null) {
                outerLoop.addAll(copies.values());
                outerLoop.addAll((Collection) loop);
            }
        }

        if (DEBUG) {
            System.out.println("Begin after peeling:");
            System.out.println(this);
            System.out.println("End after peeling");
        }

    }

    private Block copyBlock(Block block) {
        Block copy = this.newBlock();
        Tree tree = new Tree(copy, block.tree().stack());
        copy.setTree(tree);
        Iterator stmts = block.tree().stmts().iterator();

        while(stmts.hasNext()) {
            Stmt stmt = (Stmt)stmts.next();
            if (!(stmt instanceof LabelStmt)) {
                tree.addStmt((Stmt)stmt.clone());
            }
        }

        return copy;
    }

    public Subroutine labelSub(Label label) {
        return (Subroutine)this.subroutines.get(this.getNode(label));
    }

    void setSubEntry(Subroutine sub, Block entry) {
        if (sub.entry() != null) {
            this.subroutines.remove(sub.entry());
        }

        sub.setEntry(entry);
        this.subroutines.put(entry, sub);
    }

    public Collection subroutines() {
        return this.subroutines.values();
    }

    public void print(PrintStream out) {
        this.print(new PrintWriter(out, true));
    }

    public void print(PrintWriter out) {
        String dateString = DateFormat.getDateInstance().format(new Date());
        out.println("Print " + ++this.file + " at " + dateString + " " + this.method.type() + " " + this.method.name() + ":");
        this.visit(new PrintVisitor(out));
        if (PRINT_GRAPH) {
            this.printGraph();
        }

    }

    public void printGraph() {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(this.method.name() + "." + this.next++ + ".dot"));
            this.printGraph(out);
        } catch (IOException var2) {
        }

    }

    public void print() {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(this.method.name() + "." + this.next++ + ".cfg"));
            this.print(out);
        } catch (IOException var2) {
        }

    }

    public void printGraph(PrintStream out) {
        this.printGraph(new PrintWriter(out, true));
    }

    public void printGraph(PrintWriter out) {
        this.printGraph(out, "cfg");
    }

    public void printGraph(PrintWriter out, String name) {
        out.println("digraph " + name + " {");
        out.println("    fontsize=8;");
        out.println("    ordering=out;");
        out.println("    center=1;");
        this.visit(new PrintVisitor(out) {
            public void println() {
                super.print("\\n");
            }

            public void println(Object obj) {
                super.print(obj);
                super.print("\\n");
            }

            public void visitBlock(Block block) {
                super.print("    " + block.label() + " [shape=box,fontname=\"Courier\",fontsize=6,label=\"");
                block.visitChildren(this);
                super.print("\"];\n");
                Iterator succs = FlowGraph.this.succs(block).iterator();

                while(succs.hasNext()) {
                    Block succ = (Block)succs.next();
                    super.print("    " + block.label() + " -> " + succ.label());
                    if (FlowGraph.this.handlers.containsKey(succ)) {
                        super.print(" [style=dotted];\n");
                    } else {
                        super.print(" [style=solid];\n");
                    }
                }

            }
        });
        out.println("    page=\"8.5,11\";");
        out.println("}");
        out.close();
    }

    public void visitChildren(TreeVisitor visitor) {
        List list = this.preOrder();
        ListIterator iter;
        Block block;
        if (!visitor.reverse()) {
            iter = list.listIterator();

            while(iter.hasNext()) {
                block = (Block)iter.next();
                block.visit(visitor);
            }
        } else {
            iter = list.listIterator(list.size());

            while(iter.hasPrevious()) {
                block = (Block)iter.previous();
                block.visit(visitor);
            }
        }

    }

    public void visit(LeafExpr.TreeVisitor visitor) {
        visitor.visitFlowGraph(this);
    }

    public MethodEditor method() {
        return this.method;
    }

    private void removeCriticalEdges() {
        List edges = new LinkedList();
        Iterator blocks = this.nodes().iterator();

        while(true) {
            Block dst;
            Block src;
            do {
                do {
                    do {
                        do {
                            if (!blocks.hasNext()) {
                                Iterator e = edges.iterator();

                                while(e.hasNext()) {
                                    Block[] edge = (Block[])e.next();
                                    src = edge[0];
                                    Block w = edge[1];
                                    if (this.hasEdge(src, w)) {
                                        if (DEBUG) {
                                            System.out.println("removing critical edge from " + src + " to " + w);
                                        }

                                        this.splitEdge(src, w);
                                    }
                                }

                                return;
                            }

                            dst = (Block)blocks.next();
                        } while(this.subroutines.containsKey(dst));
                    } while(this.handlers.containsKey(dst));
                } while(this.preds(dst).size() <= 1);
            } while(dst == this.snkBlock);

            Iterator preds = this.preds(dst).iterator();

            while(preds.hasNext()) {
                src = (Block)preds.next();
                if (this.succs(src).size() > 1) {
                    edges.add(new Block[]{src, dst});
                }
            }
        }
    }

    private void splitEdge(Block src, Block dst) {
        if (this.handlers.containsKey(dst)) {
        } else {
            Block newBlock = this.newBlock();
            this.trace.add(this.trace.indexOf(dst), newBlock);
            Tree tree = new Tree(newBlock, src.tree().stack());
            newBlock.setTree(tree);
            tree.addInstruction(new InstructionVisitor.Instruction(167, dst.label()));
            src.visit(new ReplaceTarget(dst, newBlock));
            this.addEdge(src, newBlock);
            this.addEdge(newBlock, dst);
            this.removeEdge(src, dst);
            JumpStmt newJump = (JumpStmt)newBlock.tree().lastStmt();
            Iterator e = this.handlers.values().iterator();

            while(e.hasNext()) {
                Handler handler = (Handler)e.next();
                if (handler.protectedBlocks().contains(dst)) {
                    handler.protectedBlocks().add(newBlock);
                    this.addEdge(newBlock, handler.catchBlock());
                    newJump.catchTargets().add(handler.catchBlock());
                }
            }

        }
    }

    private void splitPhiBlocks() {
        Iterator entries = this.subroutines.values().iterator();

        while(true) {
            Subroutine entrySub;
            Block block;
            Subroutine returnSub;
            Block returnSubCaller;
            label49:
            do {
                if (!entries.hasNext()) {
                    return;
                }

                entrySub = (Subroutine)entries.next();
                block = entrySub.entry();
                returnSub = null;
                returnSubCaller = null;
                Iterator returns = this.subroutines.values().iterator();

                while(true) {
                    do {
                        if (!returns.hasNext()) {
                            continue label49;
                        }

                        returnSub = (Subroutine)returns.next();
                    } while(returnSub == entrySub);

                    Iterator paths = returnSub.paths().iterator();

                    while(paths.hasNext()) {
                        Block[] path = (Block[])paths.next();
                        if (block == path[1]) {
                            returnSubCaller = path[0];
                            continue label49;
                        }
                    }
                }
            } while(returnSubCaller == null);

            if (DEBUG) {
                System.out.println(block + " is both an entry and a return target");
            }

            int traceIndex = this.trace.indexOf(block);
            Block newEntry = this.newBlock();
            this.trace.add(traceIndex, newEntry);
            Tree tree = new Tree(newEntry, returnSub.exit().tree().stack());
            newEntry.setTree(tree);
            tree.addInstruction(new InstructionVisitor.Instruction(167, block.label()));
            this.addEdge(newEntry, block);
            Iterator paths = entrySub.paths().iterator();
            while(paths.hasNext()) {
                Block[] path = (Block[])paths.next();
                this.removeEdge(path[0], block);
                this.addEdge(path[0], newEntry);
                path[0].visit(new ReplaceTarget(block, newEntry));
            }
            this.setSubEntry(entrySub, newEntry);
            Block newTarget = this.newBlock();
            this.trace.add(traceIndex, newTarget);
            tree = new Tree(newTarget, returnSub.exit().tree().stack());
            newTarget.setTree(tree);
            tree.addInstruction(new InstructionVisitor.Instruction(167, block.label()));
            returnSub.exit().visit(new ReplaceTarget(block, newTarget));
            ((JsrStmt)returnSubCaller.tree().lastStmt()).setFollow(newTarget);
            this.addEdge(newTarget, block);
            this.addEdge(returnSub.exit(), newTarget);
            this.removeEdge(returnSub.exit(), block);
            JumpStmt entryJump = (JumpStmt)newEntry.tree().lastStmt();
            JumpStmt targetJump = (JumpStmt)newTarget.tree().lastStmt();
            Iterator e = this.handlers.values().iterator();

            while(e.hasNext()) {
                Handler handler = (Handler)e.next();
                if (handler.protectedBlocks().contains(block)) {
                    handler.protectedBlocks().add(newEntry);
                    this.addEdge(newEntry, handler.catchBlock());
                    entryJump.catchTargets().add(handler.catchBlock());
                    handler.protectedBlocks().add(newTarget);
                    this.addEdge(newTarget, handler.catchBlock());
                    targetJump.catchTargets().add(handler.catchBlock());
                }
            }
        }
    }

    private void buildSpecialTrees(Map catchBodies, Map labelPos) {
        Tree tree = new Tree(this.srcBlock, new OperandStack());
        this.srcBlock.setTree(tree);
        tree = new Tree(this.snkBlock, new OperandStack());
        this.snkBlock.setTree(tree);
        tree = new Tree(this.iniBlock, new OperandStack());
        this.iniBlock.setTree(tree);
        if (this.method.codeLength() > 0) {
            tree.initLocals(this.methodParams(this.method));
            tree.addInstruction(new InstructionVisitor.Instruction(167, this.method.firstBlock()));
            if (catchBodies != null) {
                this.addHandlerEdges(this.iniBlock, catchBodies, labelPos, (Subroutine)null, new HashSet());
            }
        }

    }

    private void addHandlerEdges(Block block, Map catchBodies, Map labelPos, Subroutine sub, Set visited) {
        if (!visited.contains(block)) {
            visited.add(block);
            Tree tree = block.tree();
            Iterator hiter = this.handlers.values().iterator();

            while(hiter.hasNext()) {
                Handler handler = (Handler)hiter.next();
                boolean prot = false;
                if (handler.protectedBlocks().contains(block)) {
                    prot = true;
                } else {
                    Iterator succs = this.succs(block).iterator();

                    while(succs.hasNext()) {
                        Block succ = (Block)succs.next();
                        if (handler.protectedBlocks().contains(succ)) {
                            prot = true;
                            break;
                        }
                    }
                }

                if (prot) {
                    Block catchBlock = handler.catchBlock();
                    JumpStmt jump = (JumpStmt)tree.lastStmt();
                    jump.catchTargets().add(catchBlock);
                    this.addEdge(block, catchBlock);
                    Block catchBody = (Block)catchBodies.get(catchBlock);
                    if (catchBody.tree() == null) {
                        OperandStack s = new OperandStack();
                        s.push(new StackExpr(0, Type.THROWABLE));
                        this.buildTreeForBlock(catchBody, s, sub, labelPos, catchBodies);
                    }

                    this.addHandlerEdges(catchBlock, catchBodies, labelPos, sub, visited);
                }
            }

        }
    }

    private void buildTreeForBlock(Block block, OperandStack stack, Subroutine sub, Map labelPos, Map catchBodies) {
        if (block.tree() == null) {
            Tree tree = new Tree(block, stack);
            block.setTree(tree);
            Integer start = (Integer)labelPos.get(block.label());
            ListIterator iter = this.method.code().listIterator(start + 1);

            label133:
            while(iter.hasNext()) {
                Object ce = iter.next();
                Block target;
                if (!(ce instanceof InstructionVisitor.Instruction)) {
                    if (ce instanceof Label) {
                        Label label = (Label)ce;
                        if (label.startsBlock()) {
                            tree.addInstruction(new InstructionVisitor.Instruction(167, label));
                            target = (Block)this.getNode(label);
                            this.addEdge(block, target);
                            this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                            break;
                        }

                        tree.addLabel(label);
                    }
                } else {
                    InstructionVisitor.Instruction inst = (InstructionVisitor.Instruction)ce;
                    Block next = null;
                    if (inst.isJsr() || inst.isConditionalJump()) {
                        int var14 = 0;

                        label125:
                        while(iter.hasNext()) {
                            Object obj = iter.next();
                            ++var14;
                            if (!(obj instanceof Label)) {
                                throw new RuntimeException(inst + " not followed by a label: " + obj + " (" + obj.getClass() + ")");
                            }

                            if (((Label)obj).startsBlock()) {
                                next = (Block)this.getNode(obj);

                                while(true) {
                                    if (var14-- <= 0) {
                                        break label125;
                                    }

                                    iter.previous();
                                }
                            }
                        }
                    }

                    if (inst.opcodeClass() == 58) {
                        tree.addInstruction(inst, sub);
                    } else {
                        if (inst.isRet()) {
                            sub.setExit(block);
                            tree.addInstruction(inst, sub);
                            Iterator paths = sub.paths().iterator();

                            while(true) {
                                if (!paths.hasNext()) {
                                    break label133;
                                }

                                Block[] path = (Block[])paths.next();
                                this.addEdge(block, path[1]);
                            }
                        }

                        if (inst.isThrow() || inst.isReturn()) {
                            tree.addInstruction(inst);
                            this.addEdge(block, this.snkBlock);
                            break;
                        }

                        Label label;
                        if (inst.isJsr()) {
                            tree.addInstruction(inst, next);
                            label = (Label)inst.operand();
                            target = (Block)this.getNode(label);
                            Subroutine nextSub = this.labelSub(label);
                            this.setSubEntry(nextSub, target);
                            this.buildTreeForBlock(target, tree.stack(), nextSub, labelPos, catchBodies);
                            this.addEdge(block, target);
                            if (nextSub.exit() != null) {
                                this.buildTreeForBlock(next, nextSub.exit().tree().stack(), sub, labelPos, catchBodies);
                                this.addEdge(nextSub.exit(), next);
                            }
                            break;
                        }

                        if (inst.isGoto()) {
                            tree.addInstruction(inst);
                            label = (Label)inst.operand();
                            target = (Block)this.getNode(label);
                            this.addEdge(block, target);
                            this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                            break;
                        }

                        if (inst.isConditionalJump()) {
                            tree.addInstruction(inst, next);
                            label = (Label)inst.operand();
                            target = (Block)this.getNode(label);
                            this.addEdge(block, target);
                            this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                            this.addEdge(block, next);
                            this.buildTreeForBlock(next, tree.stack(), sub, labelPos, catchBodies);
                            break;
                        }

                        if (inst.isSwitch()) {
                            tree.addInstruction(inst);
                            Switch sw = (Switch)inst.operand();
                            target = (Block)this.getNode(sw.defaultTarget());
                            this.addEdge(block, target);
                            this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                            int j = 0;

                            while(true) {
                                if (j >= sw.targets().length) {
                                    break label133;
                                }

                                target = (Block)this.getNode(sw.targets()[j]);
                                this.addEdge(block, target);
                                Integer targetStart = (Integer)labelPos.get(target.label());
                                this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                                ++j;
                            }
                        }

                        tree.addInstruction(inst);
                    }
                }
            }

            this.addHandlerEdges(block, catchBodies, labelPos, sub, new HashSet());
        }
    }

    private ArrayList methodParams(MethodEditor method) {
        ArrayList locals = new ArrayList();
        int index = 0;
        if (!method.isStatic()) {
            Type type = method.declaringClass().type();
            LocalVariable var = method.paramAt(index++);
            locals.add(new LocalExpr(var.index(), type));
        }

        Type[] paramTypes = method.type().indexedParamTypes();

        for(int i = 0; i < paramTypes.length; ++i) {
            if (paramTypes[i] != null) {
                LocalVariable var = method.paramAt(index);
                locals.add(new LocalExpr(var.index(), paramTypes[i]));
            }

            ++index;
        }

        return locals;
    }

    public List trace() {
        return this.trace;
    }

    public void commit() {
        this.method.clearCode();
        CodeGenerator codegen = new CodeGenerator(this.method);
        this.visit(codegen);
        Label endLabel = this.method.newLabel();
        this.method.addLabel(endLabel);
        Iterator iter = this.catchBlocks.iterator();

        while(iter.hasNext()) {
            Block catchBlock = (Block)iter.next();
            Handler handler = (Handler)this.handlers.get(catchBlock);
            Type type = handler.catchType();
            if (type.isNull()) {
                type = null;
            }

            Block begin = null;
            Iterator blocks = this.trace().iterator();

            while(blocks.hasNext()) {
                Block block = (Block)blocks.next();
                if (handler.protectedBlocks().contains(block)) {
                    if (begin == null) {
                        begin = block;
                    }
                } else if (begin != null) {
                    TryCatch tc = new TryCatch(begin.label(), block.label(), catchBlock.label(), type);
                    this.method.addTryCatch(tc);
                    begin = null;
                }
            }
        }

    }

    public Block source() {
        return this.srcBlock;
    }

    public Block init() {
        return this.iniBlock;
    }

    public Block sink() {
        return this.snkBlock;
    }

    public Collection iteratedDomFrontier(Collection blocks) {
        return this.idf(blocks, false);
    }

    public Collection iteratedPdomFrontier(Collection blocks) {
        return this.idf(blocks, true);
    }

    private Collection idf(Collection blocks, boolean reverse) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        HashSet idf = new HashSet();
        HashSet inWorklist = new HashSet(blocks);
        LinkedList worklist = new LinkedList(inWorklist);

        while(!worklist.isEmpty()) {
            Block block = (Block)worklist.removeFirst();
            Collection df;
            if (!reverse) {
                df = block.domFrontier();
            } else {
                df = block.pdomFrontier();
            }

            Iterator iter = df.iterator();

            while(iter.hasNext()) {
                Block dfBlock = (Block)iter.next();
                idf.add(dfBlock);
                if (inWorklist.add(dfBlock)) {
                    worklist.add(dfBlock);
                }
            }
        }

        return idf;
    }

    public Collection roots() {
        return new AbstractCollection() {
            public int size() {
                return 1;
            }

            public boolean contains(Object obj) {
                return obj == FlowGraph.this.srcBlock;
            }

            public Iterator iterator() {
                return new Iterator() {
                    Object next;

                    {
                        this.next = FlowGraph.this.srcBlock;
                    }

                    public boolean hasNext() {
                        return this.next != null;
                    }

                    public Object next() {
                        Object n = this.next;
                        this.next = null;
                        return n;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public Collection reverseRoots() {
        return new AbstractCollection() {
            public int size() {
                return 1;
            }

            public boolean contains(Object obj) {
                return obj == FlowGraph.this.snkBlock;
            }

            public Iterator iterator() {
                return new Iterator() {
                    Object next;

                    {
                        this.next = FlowGraph.this.snkBlock;
                    }

                    public boolean hasNext() {
                        return this.next != null;
                    }

                    public Object next() {
                        Object n = this.next;
                        this.next = null;
                        return n;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public void removeNode(Object key) {
        Block block = (Block)this.getNode(key);
        this.removeBlock(block);
    }

    public Map handlersMap() {
        return this.handlers;
    }

    public Collection handlers() {
        return this.handlers.values();
    }

    public List catchBlocks() {
        return this.catchBlocks;
    }

    private void removeBlock(Block block) {
        this.trace.remove(block);
        this.subroutines.remove(block);
        this.catchBlocks.remove(block);
        this.handlers.remove(block);
        block.setDomParent((Block)null);
        block.setPdomParent((Block)null);
        block.domChildren().clear();
        block.pdomChildren().clear();
        block.domFrontier().clear();
        block.pdomFrontier().clear();
        Iterator iter = this.handlers.values().iterator();

        while(iter.hasNext()) {
            Handler handler = (Handler)iter.next();
            handler.protectedBlocks().remove(block);
        }

        iter = this.subroutines.values().iterator();

        while(iter.hasNext()) {
            Subroutine sub = (Subroutine)iter.next();
            sub.removePathsContaining(block);
            if (sub.exit() == block) {
                sub.setExit((Block)null);
            }
        }

        Stmt s;
        if (block.tree() != null) {
            for(iter = block.tree().stmts().iterator(); iter.hasNext(); s.cleanup()) {
                s = (Stmt)iter.next();
                if (s instanceof LabelStmt) {
                    Label label = ((LabelStmt)s).label();
                    label.setStartsBlock(false);
                    this.iniBlock.tree().addStmt(new LabelStmt(label));
                }
            }
        }

        super.removeNode(block.label());
    }

    public Collection domChildren(Block block) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        return block.domChildren();
    }

    public Block domParent(Block block) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        return block.domParent();
    }

    public int blockType(Block block) {
        if (this.loopEdgeModCount != this.edgeModCount) {
            this.buildLoopTree();
        }

        return block.blockType();
    }

    public int loopDepth(Block block) {
        if (this.loopEdgeModCount != this.edgeModCount) {
            this.buildLoopTree();
        }

        FlowGraph.LoopNode loop;
        if (block != this.srcBlock && block.blockType() == 0) {
            if (block.header() != null) {
                loop = (FlowGraph.LoopNode)this.loopTree.getNode(block.header());
                return loop.depth;
            } else {
                throw new RuntimeException();
            }
        } else {
            loop = (FlowGraph.LoopNode)this.loopTree.getNode(block);
            return loop.depth;
        }
    }

    public int loopLevel(Block block) {
        if (this.loopEdgeModCount != this.edgeModCount) {
            this.buildLoopTree();
        }

        FlowGraph.LoopNode loop;
        if (block != this.srcBlock && block.blockType() == 0) {
            if (block.header() != null) {
                loop = (FlowGraph.LoopNode)this.loopTree.getNode(block.header());
                return loop.level;
            } else {
                throw new RuntimeException();
            }
        } else {
            loop = (FlowGraph.LoopNode)this.loopTree.getNode(block);
            return loop.level;
        }
    }

    public Block loopHeader(Block block) {
        if (this.loopEdgeModCount != this.edgeModCount) {
            this.buildLoopTree();
        }

        return block.header();
    }

    public List preOrder() {
        return super.preOrder();
    }

    public List postOrder() {
        return super.postOrder();
    }

    public Collection pdomChildren(Block block) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        return block.pdomChildren();
    }

    public Block pdomParent(Block block) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        return block.pdomParent();
    }

    public Collection domFrontier(Block block) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        return block.domFrontier();
    }

    public Collection pdomFrontier(Block block) {
        if (this.domEdgeModCount != this.edgeModCount) {
            this.computeDominators();
        }

        return block.pdomFrontier();
    }

    public String toString() {
        return "CFG for " + this.method;
    }

    class LoopNode extends GraphNode {
        Block header;
        int depth;
        int level;
        Set elements;

        public LoopNode(Block header) {
            this.header = header;
            this.depth = 1;
            this.level = 1;
            this.elements = new HashSet();
            this.elements.add(header);
        }

        public String toString() {
            return "level=" + this.level + " depth=" + this.depth + " header=" + this.header + " " + this.elements;
        }

        @Override
        public boolean contains(Block pred) {
            return false;
        }

        @Override
        public void retainAll(Collection nodes) {

        }
    }
}
class Subroutine {
    FlowGraph graph;
    Block entry;
    Block exit;
    ArrayList paths;
    LocalVariable returnAddress;

    public Subroutine(FlowGraph graph) {
        this.graph = graph;
        this.entry = null;
        this.exit = null;
        this.paths = new ArrayList();
        this.returnAddress = null;
    }

    public LocalVariable returnAddress() {
        return this.returnAddress;
    }

    public void setReturnAddress(LocalVariable returnAddress) {
        this.returnAddress = returnAddress;
    }

    public int numPaths() {
        return this.paths.size();
    }

    public Collection paths() {
        return this.paths;
    }

    public FlowGraph graph() {
        return this.graph;
    }

    public void removePathsContaining(Block block) {
        for(int i = this.paths.size() - 1; i >= 0; --i) {
            Block[] path = (Block[])this.paths.get(i);
            if (path[0] == block || path[1] == block) {
                if (FlowGraph.DEBUG) {
                    System.out.println("removing path " + path[0] + " -> " + path[1]);
                }

                this.paths.remove(i);
            }
        }

    }

    public void removePath(Block callerBlock, Block returnBlock) {
        for(int i = 0; i < this.paths.size(); ++i) {
            Block[] path = (Block[])this.paths.get(i);
            if (path[0] == callerBlock && path[1] == returnBlock) {
                if (FlowGraph.DEBUG) {
                    System.out.println("removing path " + path[0] + " -> " + path[1]);
                }

                this.paths.remove(i);
                return;
            }
        }

    }

    public void removeAllPaths() {
        this.paths = new ArrayList();
    }

    public void addPath(Block callerBlock, Block returnBlock) {
        for(int i = 0; i < this.paths.size(); ++i) {
            Block[] path = (Block[])this.paths.get(i);
            if (path[0] == callerBlock) {
                path[1] = returnBlock;
                return;
            }
        }

        this.paths.add(new Block[]{callerBlock, returnBlock});
    }

    public Block pathTarget(Block block) {
        for(int i = 0; i < this.paths.size(); ++i) {
            Block[] path = (Block[])this.paths.get(i);
            if (path[0] == block) {
                return path[1];
            }
        }

        return null;
    }

    public Block pathSource(Block block) {
        for(int i = 0; i < this.paths.size(); ++i) {
            Block[] path = (Block[])this.paths.get(i);
            if (path[1] == block) {
                return path[0];
            }
        }

        return null;
    }

    public void setEntry(Block entry) {
        this.entry = entry;
    }

    public void setExit(Block exit) {
        this.exit = exit;
    }

    public Block entry() {
        return this.entry;
    }

    public Block exit() {
        return this.exit;
    }

    public void print(PrintStream out) {
        out.println("    " + this.entry);
        Iterator e = this.paths().iterator();

        while(e.hasNext()) {
            Block[] path = (Block[])e.next();
            out.println("    path: " + path[0] + " -> " + path[1]);
        }

    }

    public String toString() {
        return "sub " + this.entry;
    }
}
class LocalVariable {
    private String name;
    private Type type;
    private int index;

    public LocalVariable(int index) {
        this.name = null;
        this.type = null;
        this.index = index;
    }

    public LocalVariable(String name, Type type, int index) {
        this.name = name;
        this.type = type;
        this.index = index;
    }

    public int hashCode() {
        return this.index;
    }

    public boolean equals(Object obj) {
        return obj != null && obj instanceof LocalVariable && ((LocalVariable)obj).index == this.index;
    }

    public String name() {
        return this.name;
    }

    public Type type() {
        return this.type;
    }

    public int index() {
        return this.index;
    }

    public String toString() {
        return this.name == null ? "Local$" + this.index : this.name + "$" + this.index;
    }
}
class ImmutableIterator implements Iterator {
    Iterator iter;

    public ImmutableIterator(final Collection c) {
        iter = new ArrayList(c).iterator();
    }

    public Object next() {
        return iter.next();
    }

    public boolean hasNext() {
        return iter.hasNext();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
class ReplaceVisitor extends TreeVisitor {
    Node from;
    Node to;

    public ReplaceVisitor(Node from, Node to) {
        this.from = from;
        this.to = to;
        if (Tree.DEBUG) {
            System.out.println("replace " + from + " VN=" + from.valueNumber() + " in " + from.parent + " with " + to);
        }

    }

    public void visitTree(Tree tree) {
        if (this.to instanceof Stmt) {
            ((Stmt)this.to).setParent(tree);
            ListIterator iter = tree.stmts.listIterator(tree.stmts.size());

            while(iter.hasPrevious()) {
                Stmt s = (Stmt)iter.previous();
                if (s == this.from) {
                    iter.set(this.to);
                    break;
                }
            }
        } else {
            tree.visitChildren(this);
        }

    }

    public void visitExprStmt(ExprStmt stmt) {
        if (stmt.expr == this.from) {
            stmt.expr = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitInitStmt(InitStmt stmt) {
        for(int i = 0; i < stmt.targets.length; ++i) {
            if (stmt.targets[i] == this.from) {
                stmt.targets[i] = (LocalExpr)this.to;
                ((LocalExpr)this.to).setParent(stmt);
                return;
            }
        }

        stmt.visitChildren(this);
    }

    public void visitGotoStmt(GotoStmt stmt) {
        stmt.visitChildren(this);
    }

    public void visitMonitorStmt(MonitorStmt stmt) {
        if (stmt.object == this.from) {
            stmt.object = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitStackManipStmt(StackManipStmt stmt) {
        int i;
        for(i = 0; i < stmt.target.length; ++i) {
            if (stmt.target[i] == this.from) {
                stmt.target[i] = (StackExpr)this.to;
                ((Expr)this.to).setParent(stmt);
                return;
            }
        }

        for(i = 0; i < stmt.source.length; ++i) {
            if (stmt.source[i] == this.from) {
                stmt.source[i] = (StackExpr)this.to;
                ((Expr)this.to).setParent(stmt);
                return;
            }
        }

        stmt.visitChildren(this);
    }

    public void visitCatchExpr(CatchExpr expr) {
        expr.visitChildren(this);
    }

    public void visitPhiJoinStmt(PhiJoinStmt stmt) {
        if (stmt.target == this.from) {
            stmt.target = (VarExpr)this.to;
            ((VarExpr)this.to).setParent(stmt);
        } else {
            Iterator e = stmt.operands.keySet().iterator();

            while(e.hasNext()) {
                Block block = (Block)e.next();
                if (stmt.operandAt(block) == this.from) {
                    stmt.setOperandAt(block, (Expr)this.to);
                    ((Expr)this.to).setParent(stmt);
                    return;
                }
            }

            stmt.visitChildren(this);
        }

    }

    public void visitPhiCatchStmt(PhiCatchStmt stmt) {
        if (stmt.target == this.from) {
            stmt.target = (LocalExpr)this.to;
            ((LocalExpr)this.to).setParent(stmt);
        } else {
            ListIterator e = stmt.operands.listIterator();

            while(e.hasNext()) {
                LocalExpr expr = (LocalExpr)e.next();
                if (expr == this.from) {
                    e.set(this.to);
                    this.from.cleanup();
                    ((LocalExpr)this.to).setParent(stmt);
                    return;
                }
            }

            stmt.visitChildren(this);
        }

    }

    public void visitRetStmt(RetStmt stmt) {
        stmt.visitChildren(this);
    }

    public void visitReturnExprStmt(ReturnExprStmt stmt) {
        if (stmt.expr == this.from) {
            stmt.expr = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitReturnStmt(ReturnStmt stmt) {
        stmt.visitChildren(this);
    }

    public void visitAddressStoreStmt(AddressStoreStmt stmt) {
        stmt.visitChildren(this);
    }

    public void visitStoreExpr(StoreExpr expr) {
        if (expr.target == this.from) {
            expr.target = (MemExpr)this.to;
            ((MemExpr)this.to).setParent(expr);
        } else if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitSwitchStmt(SwitchStmt stmt) {
        if (stmt.index == this.from) {
            stmt.index = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitThrowStmt(ThrowStmt stmt) {
        if (stmt.expr == this.from) {
            stmt.expr = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitSCStmt(SCStmt stmt) {
        if (stmt.array == this.from) {
            stmt.array = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else if (stmt.index == this.from) {
            stmt.index = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitSRStmt(SRStmt stmt) {
        if (stmt.array == this.from) {
            stmt.array = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else if (stmt.start == this.from) {
            stmt.start = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else if (stmt.end == this.from) {
            stmt.end = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitDefExpr(DefExpr expr) {
        expr.visitChildren(this);
    }

    public void visitArrayLengthExpr(ArrayLengthExpr expr) {
        if (expr.array == this.from) {
            expr.array = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitArithExpr(ArithExpr expr) {
        if (expr.left == this.from) {
            expr.left = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else if (expr.right == this.from) {
            expr.right = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitArrayRefExpr(ArrayRefExpr expr) {
        if (expr.array == this.from) {
            expr.array = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else if (expr.index == this.from) {
            expr.index = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitCallMethodExpr(CallMethodExpr expr) {
        if (expr.receiver == this.from) {
            expr.receiver = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            for(int i = 0; i < expr.params.length; ++i) {
                if (expr.params[i] == this.from) {
                    expr.params[i] = (Expr)this.to;
                    ((Expr)this.to).setParent(expr);
                    return;
                }
            }

            expr.visitChildren(this);
        }

    }

    public void visitCallStaticExpr(CallStaticExpr expr) {
        for(int i = 0; i < expr.params.length; ++i) {
            if (expr.params[i] == this.from) {
                expr.params[i] = (Expr)this.to;
                ((Expr)this.to).setParent(expr);
                return;
            }
        }

        expr.visitChildren(this);
    }

    public void visitCastExpr(CastExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitConstantExpr(ConstantExpr expr) {
        expr.visitChildren(this);
    }

    public void visitFieldExpr(FieldExpr expr) {
        if (expr.object == this.from) {
            expr.object = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitInstanceOfExpr(InstanceOfExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitLocalExpr(LocalExpr expr) {
        expr.visitChildren(this);
    }

    public void visitNegExpr(NegExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitNewArrayExpr(NewArrayExpr expr) {
        if (expr.size == this.from) {
            expr.size = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitNewExpr(NewExpr expr) {
        expr.visitChildren(this);
    }

    public void visitNewMultiArrayExpr(NewMultiArrayExpr expr) {
        for(int i = 0; i < expr.dimensions.length; ++i) {
            if (expr.dimensions[i] == this.from) {
                expr.dimensions[i] = (Expr)this.to;
                ((Expr)this.to).setParent(expr);
                return;
            }
        }

        expr.visitChildren(this);
    }

    public void visitIfZeroStmt(IfZeroStmt stmt) {
        if (stmt.expr == this.from) {
            stmt.expr = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitIfCmpStmt(IfCmpStmt stmt) {
        if (stmt.left == this.from) {
            stmt.left = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else if (stmt.right == this.from) {
            stmt.right = (Expr)this.to;
            ((Expr)this.to).setParent(stmt);
        } else {
            stmt.visitChildren(this);
        }

    }

    public void visitReturnAddressExpr(ReturnAddressExpr expr) {
        expr.visitChildren(this);
    }

    public void visitShiftExpr(ShiftExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else if (expr.bits == this.from) {
            expr.bits = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitZeroCheckExpr(ZeroCheckExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitRCExpr(RCExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitUCExpr(UCExpr expr) {
        if (expr.expr == this.from) {
            expr.expr = (Expr)this.to;
            ((Expr)this.to).setParent(expr);
        } else {
            expr.visitChildren(this);
        }

    }

    public void visitStackExpr(StackExpr expr) {
        expr.visitChildren(this);
    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }

    public void visitStaticFieldExpr(StaticFieldExpr expr) {
        expr.visitChildren(this);
    }
}
class DominatorTree {
    public static boolean DEBUG = false;

    /**
     * Calculates what vertices dominate other verices and notify the basic
     * Blocks as to who their dominator is.
     *
     * @param graph
     *            The cfg that is used to find the dominator tree.
     * @param reverse
     *            Do we go in revsers? That is, are we computing the dominatance
     *            (false) or postdominance (true) tree.
     * @see Block
     */
    public static void buildTree(final FlowGraph graph, boolean reverse) {
        final int size = graph.size(); // The number of vertices in the cfg

        final Map snkPreds = new HashMap(); // The predacessor vertices from the
        // sink

        // Determine the predacessors of the cfg's sink node
        DominatorTree.insertEdgesToSink(graph, snkPreds, reverse);

        // Get the index of the root
        final int root = reverse ? graph.preOrderIndex(graph.sink()) : graph
                .preOrderIndex(graph.source());
        // Bit matrix indicating the dominators of each vertex.
        // If bit j of dom[i] is set, then node j dominates node i.
        final BitSet[] dom = new BitSet[size];

        // A bit vector of all 1's
        final BitSet ALL = new BitSet(size);

        for (int i = 0; i < size; i++) {
            ALL.set(i);
        }

        // Initially, all the bits in the dominance matrix are set, except
        // for the root node. The root node is initialized to have itself
        // as an immediate dominator.
        //
        for (int i = 0; i < size; i++) {
            final BitSet blockDoms = new BitSet(size);
            dom[i] = blockDoms;

            if (i != root) {
                blockDoms.or(ALL);
            } else {
                blockDoms.set(root);
            }
        }

        // Did the dominator bit vector array change?
        boolean changed = true;

        while (changed) {
            changed = false;

            // Get the basic blocks contained in the cfg
            final Iterator blocks = reverse ? graph.postOrder().iterator()
                    : graph.preOrder().iterator();

            // Compute the dominators of each node in the cfg. We iterate
            // over every node in the cfg. The dominators of a node, x, are
            // found by taking the intersection of the dominator bit vectors
            // of each predacessor of x and unioning that with x. This
            // process is repeated until no changes are made to any dominator
            // bit vector.

            while (blocks.hasNext()) {
                final Block block = (Block) blocks.next();

                final int i = graph.preOrderIndex(block);
                // We already know the dominators of the root, keep looking
                if (i == root) {
                    continue;
                }

                final BitSet oldSet = dom[i];
                final BitSet blockDoms = new BitSet(size);
                blockDoms.or(oldSet);

                // print(graph, reverse, "old set", i, blockDoms);

                // blockDoms := intersection of dom(pred) for all pred(block).
                Collection preds = reverse ? graph.succs(block) : graph
                        .preds(block);

                Iterator e = preds.iterator();

                // Find the intersection of the dominators of block's
                // predacessors.
                while (e.hasNext()) {
                    final Block pred = (Block) e.next();

                    final int j = graph.preOrderIndex(pred);
                    blockDoms.and(dom[j]);
                }

                // Don't forget to account for the sink node if block is a
                // leaf node. Appearantly, there are not edges between
                // leaf nodes and the sink node!
                preds = (Collection) snkPreds.get(block);

                if (preds != null) {
                    e = preds.iterator();

                    while (e.hasNext()) {
                        final Block pred = (Block) e.next();

                        final int j = graph.preOrderIndex(pred);

                        blockDoms.and(dom[j]);
                    }
                }

                // Include yourself in your dominators?!
                blockDoms.set(i);

                // print(graph, reverse, "intersecting " + preds, i, blockDoms);

                // If the set changed, set the changed bit.
                if (!blockDoms.equals(oldSet)) {
                    changed = true;
                    dom[i] = blockDoms;
                }
            }
        }

        // Once we have the predacessor bit vectors all squared away, we can
        // determine which vertices dominate which vertices.

        Iterator blocks = graph.nodes().iterator();

        // Initialize each block's (post)dominator parent and children
        while (blocks.hasNext()) {
            final Block block = (Block) blocks.next();
            if (!reverse) {
                block.setDomParent(null);
                block.domChildren().clear();
            } else {
                block.setPdomParent(null);
                block.pdomChildren().clear();
            }
        }

        blocks = graph.nodes().iterator();

        // A block's immediate dominator is its closest dominator. So, we
        // start with the dominators, dom(b), of a block, b. To find the
        // imediate dominator of b, we remove all blocks from dom(b) that
        // dominate any block in dom(b).

        while (blocks.hasNext()) {
            final Block block = (Block) blocks.next();
            final int i = graph.preOrderIndex(block);
            if (i == root) {
                if (!reverse) {
                    block.setDomParent(null);
                } else {
                    block.setPdomParent(null);
                }

            } else {
                // Find the immediate dominator
                // idom := dom(block) - dom(dom(block)) - block
                final BitSet blockDoms = dom[i];

                // print(graph, reverse, "dom set", i, blockDoms);

                final BitSet idom = new BitSet(size);
                idom.or(blockDoms);
                idom.clear(i);

                for (int j = 0; j < size; j++) {
                    if ((i != j) && blockDoms.get(j)) {
                        final BitSet domDomBlocks = dom[j];

                        // idom = idom - (domDomBlocks - {j})
                        final BitSet b = new BitSet(size);
                        b.or(domDomBlocks);
                        b.xor(ALL);
                        b.set(j);
                        idom.and(b);

                        // print(graph, reverse,
                        // "removing dom(" + graph.preOrder().get(j) +")",
                        // i, idom);
                    }
                }

                Block parent = null;

                // A block should only have one immediate dominator.
                for (int j = 0; j < size; j++) {
                    if (idom.get(j)) {
                        final Block p = (Block) graph.preOrder().get(j);
                        parent = p;
                    }
                }

                if (!reverse) {
                    if (DominatorTree.DEBUG) {
                        System.out.println(parent + " dominates " + block);
                    }

                    block.setDomParent(parent);

                } else {
                    if (DominatorTree.DEBUG) {
                        System.out.println(parent + " postdominates " + block);
                    }

                    block.setPdomParent(parent);
                }
            }
        }
    }

    /**
     * Determines which nodes are predacessors of a cfg's sink node. Creates a
     * Map that maps the sink node to its predacessors (or the leaf nodes to the
     * sink node, their predacessor, if we're going backwards).
     *
     * @param graph
     *            The cfg to operate on.
     * @param preds
     *            A mapping from leaf nodes to their predacessors. The exact
     *            semantics depend on whether or not we are going forwards.
     * @param reverse
     *            Are we computing the dominators or postdominators?
     */
    private static void insertEdgesToSink(final FlowGraph graph,
                                          final Map preds, final boolean reverse) {
        final BitSet visited = new BitSet(); // see insertEdgesToSinkDFS
        final BitSet returned = new BitSet();

        visited.set(graph.preOrderIndex(graph.source()));

        DominatorTree.insertEdgesToSinkDFS(graph, graph.source(), visited,
                returned, preds, reverse);
    }

    /**
     * This method determines which nodes are the predacessor of the sink node
     * of a cfg. A depth-first traversal of the cfg is performed. When a leaf
     * node (that is not the sink node) is encountered, add an entry to the
     * preds Map.
     *
     * @param graph
     *            The cfg being operated on.
     * @param block
     *            The basic Block to start at.
     * @param visited
     *            Vertices that were visited
     * @param returned
     *            Vertices that returned
     * @param preds
     *            Maps a node to a HashSet representing its predacessors. In the
     *            case that we're determining the dominace tree, preds maps the
     *            sink node to its predacessors. In the case that we're
     *            determining the postdominance tree, preds maps the sink node's
     *            predacessors to the sink node.
     * @param reverse
     *            Do we go in reverse?
     */
    private static void insertEdgesToSinkDFS(final FlowGraph graph,
                                             final Block block, final BitSet visited, final BitSet returned,
                                             final Map preds, boolean reverse) {
        boolean leaf = true; // Is a vertex a leaf node?

        // Get the successors of block
        final Iterator e = graph.succs(block).iterator();

        while (e.hasNext()) {
            final Block succ = (Block) e.next();

            // Determine index of succ vertex in a pre-order traversal
            final int index = graph.preOrderIndex(succ);
            if (!visited.get(index)) {
                // If the successor block hasn't been visited, visit it
                visited.set(index);
                DominatorTree.insertEdgesToSinkDFS(graph, succ, visited,
                        returned, preds, reverse);
                returned.set(index);
                leaf = false;

            } else if (returned.get(index)) {
                // Already visited and returned, so a descendent of succ
                // has an edge to the sink.
                leaf = false;
            }
        }

        if (leaf && (block != graph.sink())) {
            // If we're dealing with a leaf node that is not the sink, set
            // up its predacessor set.

            if (!reverse) {
                // If we're going forwards (computing dominators), get the
                // predacessor vertices from the sink
                Set p = (Set) preds.get(graph.sink());

                // If there are no (known) predacessors, make a new HashSet to
                // store them and register it in the pred Map.
                if (p == null) {
                    p = new HashSet();
                    preds.put(graph.sink(), p);
                }

                // The block is in the predacessors of the sink
                p.add(block);

            } else {
                // If we're going backwards, get the block's predacessors
                Set p = (Set) preds.get(block);

                if (p == null) {
                    p = new HashSet();
                    preds.put(block, p);
                }
                // Add the sink vertex to the predacessors of the block
                p.add(graph.sink());
            }
        }
    }
}
class DominanceFrontier {
    public DominanceFrontier() {
    }

    public static void buildFrontier(FlowGraph graph, boolean reverse) {
        if (!reverse) {
            calcFrontier(graph.source(), graph, reverse);
        } else {
            calcFrontier(graph.sink(), graph, reverse);
        }

    }

    private static LinkedList calcFrontier(Block block, FlowGraph graph, boolean reverse) {
        Block[] local = new Block[graph.size()];
        Iterator children;
        if (!reverse) {
            children = block.domChildren().iterator();
        } else {
            children = block.pdomChildren().iterator();
        }

        LinkedList v;
        while(children.hasNext()) {
            Block child = (Block)children.next();
            v = calcFrontier(child, graph, reverse);
            Iterator e = v.iterator();

            while(e.hasNext()) {
                Block dfChild = (Block)e.next();
                if (!reverse) {
                    if (block != dfChild.domParent()) {
                        local[graph.preOrderIndex(dfChild)] = dfChild;
                    }
                } else if (block != dfChild.pdomParent()) {
                    local[graph.preOrderIndex(dfChild)] = dfChild;
                }
            }
        }

        Iterator succs = reverse ? graph.preds(block).iterator() : graph.succs(block).iterator();

        while(succs.hasNext()) {
            Block succ = (Block)succs.next();
            if (!reverse) {
                if (block != succ.domParent()) {
                    local[graph.preOrderIndex(succ)] = succ;
                }
            } else if (block != succ.pdomParent()) {
                local[graph.preOrderIndex(succ)] = succ;
            }
        }

        v = new LinkedList();

        for(int i = 0; i < local.length; ++i) {
            if (local[i] != null) {
                v.add(local[i]);
            }
        }

        if (!reverse) {
            block.domFrontier().clear();
            block.domFrontier().addAll(v);
        } else {
            block.pdomFrontier().clear();
            block.pdomFrontier().addAll(v);
        }

        return v;
    }
}
class UnionFind {
    ResizeableArrayList nodes;

    public UnionFind() {
        this.nodes = new ResizeableArrayList();
    }

    public UnionFind(int size) {
        this.nodes = new ResizeableArrayList(size);
    }

    public UnionFind.Node findNode(int a) {
        this.nodes.ensureSize(a + 1);
        UnionFind.Node na = (UnionFind.Node)this.nodes.get(a);
        if (na == null) {
            UnionFind.Node root = new UnionFind.Node(a);
            root.child = new UnionFind.Node(a);
            root.child.parent = root;
            this.nodes.set(a, root.child);
            return root;
        } else {
            return this.findNode(na);
        }
    }

    public int find(int a) {
        return this.findNode(a).value;
    }

    private UnionFind.Node findNode(UnionFind.Node node) {
        Stack stack;
        for(stack = new Stack(); node.parent.child == null; node = node.parent) {
            stack.push(node);
        }

        UnionFind.Node rootChild;
        for(rootChild = node; !stack.empty(); node.parent = rootChild) {
            node = (UnionFind.Node)stack.pop();
        }
        return rootChild.parent;
    }

    public boolean isEquiv(int a, int b) {
        return this.findNode(a) == this.findNode(b);
    }

    public void union(int a, int b) {
        UnionFind.Node na = this.findNode(a);
        UnionFind.Node nb = this.findNode(b);
        if (na != nb) {
            if (na.rank > nb.rank) {
                nb.child.parent = na.child;
                na.value = b;
            } else {
                na.child.parent = nb.child;
                nb.value = b;
                if (na.rank == nb.rank) {
                    ++nb.rank;
                }
            }

        }
    }

    class Node {
        UnionFind.Node parent;
        UnionFind.Node child;
        int value;
        int rank;

        public Node(int v) {
            this.value = v;
            this.rank = 0;
        }
    }
}
class ReplaceTarget extends TreeVisitor {
    Block oldDst;

    Block newDst;

    public ReplaceTarget(final Block oldDst, final Block newDst) {
        this.oldDst = oldDst;
        this.newDst = newDst;
    }

    public void visitTree(final Tree tree) {
        final Stmt last = tree.lastStmt();

        if (last instanceof JumpStmt) {
            final JumpStmt stmt = (JumpStmt) last;

            if (FlowGraph.DEBUG) {
                System.out.println("  Replacing " + oldDst + " with " + newDst
                        + " in " + stmt);
            }

            if (stmt.catchTargets().remove(oldDst)) {
                stmt.catchTargets().add(newDst);
            }

            stmt.visit(this);
        }
    }

    public void visitJsrStmt(final JsrStmt stmt) {
        if (stmt.sub().entry() == oldDst) {
            if (FlowGraph.DEBUG) {
                System.out.print("  replacing " + stmt);
            }

            stmt.block().graph().setSubEntry(stmt.sub(), newDst);

            if (FlowGraph.DEBUG) {
                System.out.println("   with " + stmt);
            }
        }
    }

    public void visitRetStmt(final RetStmt stmt) {
        final Iterator paths = stmt.sub().paths().iterator();

        while (paths.hasNext()) {
            final Block[] path = (Block[]) paths.next();

            if (FlowGraph.DEBUG) {
                System.out.println("  path = " + path[0] + " " + path[1]);
            }

            if (path[1] == oldDst) {
                if (FlowGraph.DEBUG) {
                    System.out.println("  replacing ret to " + oldDst
                            + " with ret to " + newDst);
                }

                path[1] = newDst;
                ((JsrStmt) path[0].tree().lastStmt()).setFollow(newDst);
            }
        }
    }

    public void visitGotoStmt(final GotoStmt stmt) {
        if (stmt.target() == oldDst) {
            stmt.setTarget(newDst);
        }
    }

    public void visitSwitchStmt(final SwitchStmt stmt) {
        if (stmt.defaultTarget() == oldDst) {
            stmt.setDefaultTarget(newDst);
        }

        final Block[] targets = stmt.targets();
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == oldDst) {
                targets[i] = newDst;
            }
        }
    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }

    public void visitIfStmt(final LeafExpr.IfStmt stmt) {
        if (stmt.trueTarget() == oldDst) {
            stmt.setTrueTarget(newDst);
        }

        if (stmt.falseTarget() == oldDst) {
            stmt.setFalseTarget(newDst);
        }
    }
}
class CodeGenerator extends TreeVisitor implements Opcode {
    public static boolean DEBUG = false;

    public static boolean USE_PERSISTENT = false; // Generate _nowb

    /**
     * Use information about placement of local variables to eliminate loads and
     * stores in favor of stack manipulations
     */
    public static boolean OPT_STACK = false;

    public static boolean DB_OPT_STACK = false;

    protected MethodEditor method;

    protected Set visited;

    protected Map postponedInstructions;

    protected Block next;

    protected int stackHeight; // The current height of the stack

    StackOptimizer currentSO; // object used to determine where to apply

    // stack optimization

    /**
     * Constructor.
     *
     * @param method
     *            The method for which bytecode is generated.
     */
    public CodeGenerator(final MethodEditor method) {
        this.method = method;
        this.postponedInstructions = new HashMap();
    }

    /**
     * Visits the nodes in the method's control flow graph and ensures that
     * information about the method's basic blocks is consistent and correct.
     *
     * @param cfg
     *            The control flow graph associated with this method.
     */
    public void visitFlowGraph(final FlowGraph cfg) {
        // Generate the code.

        visited = new HashSet();
        visited.add(cfg.source());
        visited.add(cfg.sink());

        final Iterator e = cfg.trace().iterator();

        stackHeight = 0; // At beginning of method stack has height 0

        Block block = (Block) e.next();

        // Visit each block in the method (via the trace in the method's CFG)
        // and ensure that the first (and ONLY the first) label in the code
        // is marked as starting a block.
        while (block != null) {
            if (e.hasNext()) {
                next = (Block) e.next();

            } else {
                next = null;
            }

            if (CodeGenerator.DEBUG) {
                System.out.println("code for " + block);
            }

            // Make sure the first label is marked as starting a block
            // and the rest are marked as not starting a block.
            block.visit(new TreeVisitor() {
                boolean startsBlock = true;

                public void visitLabelStmt(final LabelStmt stmt) {
                    stmt.label().setStartsBlock(startsBlock);
                    startsBlock = false;
                }

                public void visitStmt(final Stmt stmt) {
                }

                @Override
                public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                }
            });

            // Generate the code for each block
            visited.add(block);
            // currentSO is the StackOptimizer object that discerns
            // where dups may be used instead of loads
            if (CodeGenerator.OPT_STACK) {
                currentSO = block.stackOptimizer();
            }
            block.visitChildren(this);

            block = next;
        }

        next = null;
        visited = null;

        // Go through the catch blocks and determine the what the
        // protected regions are that correspond to the catch blocks.
        // Create TryCatch objects to represent the protected regions.

        final Iterator iter = cfg.catchBlocks().iterator();

        while (iter.hasNext()) {
            final Block catchBlock = (Block) iter.next();
            final Handler handler = (Handler) cfg.handlersMap().get(catchBlock);

            Type type = handler.catchType();

            if (type.isNull()) {
                type = null;
            }

            // First block in protected block
            Block begin = null;

            final Iterator blocks = cfg.trace().iterator();

            while (blocks.hasNext()) {
                block = (Block) blocks.next();

                if (handler.protectedBlocks().contains(block)) {
                    if (begin == null) {
                        begin = block;
                    }

                } else if (begin != null) {
                    // The block is no longer protected, its the end of the
                    // protected region
                    final TryCatch tc = new TryCatch(begin.label(), block
                            .label(), catchBlock.label(), type);
                    method.addTryCatch(tc);

                    begin = null;
                }
            }
        }
    }

    /**
     * Simplifies the control flow of a method by changing jump and return
     * statements into gotos where appropriate.
     */
    public void simplifyControlFlow(final FlowGraph cfg) {
        // Remove any blocks from the CFG that consist of solely jumps
        removeEmptyBlocks(cfg);

        cfg.visit(new TreeVisitor() {
            public void visitJsrStmt(final JsrStmt stmt) {
                final Subroutine sub = stmt.sub();

                // If there is only 1 path through the sub, replace both
                // the jsr and the ret with gotos.
                if (sub.numPaths() == 1) {
                    final Block exit = sub.exit();

                    // Remember that it is not required for a subroutine to have
                    // a ret. So, no exit block may be identified and we'll
                    // have to make sure one exists.
                    if (exit != null) {
                        final JumpStmt oldJump = (JumpStmt) exit.tree()
                                .lastStmt();
                        final JumpStmt jump = new JumpStmt(stmt.follow()) {
                            @Override
                            public void visitForceChildren(TreeVisitor var1) {

                            }

                            @Override
                            public void visit(TreeVisitor var1) {

                            }

                            @Override
                            public Object clone() {
                                return null;
                            }
                        };
                        jump.catchTargets().addAll(oldJump.catchTargets());
                        oldJump.replaceWith(jump);
                    }

                    final JumpStmt jump = new JumpStmt(sub.entry()) {
                        @Override
                        public void visitForceChildren(TreeVisitor var1) {

                        }

                        @Override
                        public void visit(TreeVisitor var1) {

                        }

                        @Override
                        public Object clone() {
                            return null;
                        }
                    };
                    jump.catchTargets().addAll(stmt.catchTargets());
                    stmt.replaceWith(jump);

                    // The subroutine is no longer really a subroutine
                    cfg.removeSub(sub);

                    // Clean up the CFG by removing all AddressStoreStmts that
                    // store the address of the "removed" subroutine.
                    cfg.visit(new TreeVisitor() {
                        Iterator iter;

                        public void visitTree(final Tree tree) {
                            iter = tree.stmts().iterator();

                            while (iter.hasNext()) {
                                final Stmt s = (Stmt) iter.next();

                                if (s instanceof AddressStoreStmt) {
                                    final AddressStoreStmt store = (AddressStoreStmt) s;

                                    if (store.sub() == sub) {
                                        iter.remove();
                                    }
                                }
                            }
                        }

                        @Override
                        public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                        }
                    });
                }
            }

            public void visitStmt(final Stmt stmt) {
            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }
        });
    }

    /**
     * Replace PhiStmts with copies that accomplish what the PhiStmts represent.
     * Then remove the PhiStmts from the control flow graph.
     */
    public void replacePhis(final FlowGraph cfg) {
        replaceCatchPhis(cfg);
        replaceJoinPhis(cfg);

        // Remove the phis.
        cfg.visit(new TreeVisitor() {
            public void visitTree(final Tree tree) {
                final Iterator e = tree.stmts().iterator();

                while (e.hasNext()) {
                    final Stmt s = (Stmt) e.next();

                    if (s instanceof PhiStmt) {
                        e.remove();
                    }
                }
            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }
        });
    }

    /**
     * Replace each PhiCatchStmt with assignments at its operands' defs.
     */
    private void replaceCatchPhis(final FlowGraph cfg) {
        cfg.visit(new TreeVisitor() {
            HashMap seen = new HashMap();

            public void visitFlowGraph(final FlowGraph graph) {
                final Iterator iter = graph.catchBlocks().iterator();

                // Examine each block that begins an exception handler
                while (iter.hasNext()) {
                    final Block block = (Block) iter.next();
                    block.visit(this);
                }
            }

            public void visitPhiCatchStmt(final PhiCatchStmt phi) {
                final LocalExpr target = (LocalExpr) phi.target();
                final int index = target.index();

                final Iterator iter = phi.operands().iterator();

                // Examine every operand of the PhiCatchStmt. If necessary,
                // insert copies of the operand to the target after the last
                // occurrence of the operand.
                while (iter.hasNext()) {
                    final LocalExpr expr = (LocalExpr) iter.next();
                    final LocalExpr def = (LocalExpr) expr.def();

                    if (def == null) {
                        continue;
                    }

                    if (CodeGenerator.DEBUG) {
                        System.out.println("inserting for " + phi + " at "
                                + def);
                    }

                    BitSet s = (BitSet) seen.get(def);

                    if (s == null) {
                        s = new BitSet();
                        seen.put(def, s);

                        final BitSet t = s;

                        // Visit the parent expression and make note of which
                        // local variables were encountered in StoreExprs. That
                        // is, have we already generated a copy for the operand
                        // of
                        // interest?
                        def.parent().visit(new TreeVisitor() {
                            public void visitStoreExpr(final StoreExpr expr) {
                                if (CodeGenerator.DEBUG) {
                                    System.out.println("    merging with "
                                            + expr);
                                }

                                final Expr lhs = expr.target();
                                final Expr rhs = expr.expr();

                                if (lhs instanceof LocalExpr) {
                                    t.set(((LocalExpr) lhs).index());
                                }

                                if (rhs instanceof LocalExpr) {
                                    t.set(((LocalExpr) rhs).index());

                                } else if (rhs instanceof StoreExpr) {
                                    // Visit RHS. LHS be ignored by visitNode.
                                    super.visitStoreExpr(expr);
                                }
                            }

                            public void visitNode(final Node node) {
                            }

                            @Override
                            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                            }
                        });
                    }

                    // If we've already inserted a copy (StoreStmt) for the
                    // local variable, skip it
                    if (s.get(index)) {
                        continue;
                    }

                    s.set(index);
                    if (def.parent() instanceof Stmt) {
                        // Insert a new Stmt to copy into the target

                        final Stmt stmt = (Stmt) def.parent();
                        final Stmt store = createStore(target, def);
                        def.block().tree().addStmtAfter(store, stmt);

                    } else {
                        // Replace s := r with s := (t := r)
                        final StoreExpr p = (StoreExpr) def.parent();
                        final Expr rhs = p.expr();

                        if ((rhs instanceof LocalExpr)
                                && (((LocalExpr) rhs).index() == def.index())) {
                            // No need to insert a copy. Just change the index
                            // (local variable to which LocalExpr is assigned)
                            // to be
                            // the same as the target
                            def.setIndex(index);

                        } else {
                            rhs.setParent(null);

                            // Copy the rhs into the target
                            final StoreExpr store = new StoreExpr(
                                    (LocalExpr) target.clone(), rhs, rhs.type());

                            p.visit(new ReplaceVisitor(rhs, store));
                        }
                    }
                }
            }

            public void visitStmt(final Stmt stmt) {
            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }
        });
    }

    /**
     * Replace PhiJoinStmts with assignments at the end of the predecessor
     * blocks. Note that from now on the FUD chains are broken since there can
     * be more than one def of a variable.
     */
    private void replaceJoinPhis(final FlowGraph cfg) {
        // Go in trace order since liveness was computed under this
        // assumption.

        final Iterator iter = cfg.trace().iterator();

        while (iter.hasNext()) {
            final Block block = (Block) iter.next();

            if (block == cfg.sink()) {
                continue;
            }

            block.visit(new TreeVisitor() {
                public void visitPhiJoinStmt(final PhiJoinStmt stmt) {
                    // If an operand of the Phi statement is undefined, insert
                    // code to assign 0 to the operand. The value should never
                    // be used, but the verifier will squawk about using an
                    // undefined local variable.

                    final Iterator preds = cfg.preds(stmt.block()).iterator();

                    while (preds.hasNext()) {
                        final Block pred = (Block) preds.next();

                        final Expr operand = stmt.operandAt(pred);

                        if ((stmt.target() instanceof LocalExpr)
                                && (operand instanceof LocalExpr)) {

                            final LocalExpr t = (LocalExpr) stmt.target();
                            final LocalExpr s = (LocalExpr) operand;

                            if (t.index() == s.index()) {
                                // The target and the operand are already
                                // allocated to
                                // the same variable. Don't bother making a
                                // copy.
                                continue;
                            }
                        }

                        final Tree tree = pred.tree();

                        // Insert stores before the last stmt to ensure
                        // we don't redefine locals used the the branch stmt.
                        final Stmt last = tree.lastStmt();

                        last.visitChildren(new TreeVisitor() {
                            // The last statement in the block should be a jump.
                            // If
                            // the jump statement contains an expression,
                            // replace
                            // that expression with a stack variable. Before the
                            // jump, insert a store of the expression into the
                            // stack
                            // variable. This is done so that the store to the
                            // PhiJoinStmt's operand does not interfere with any
                            // local variables that might appear in the
                            // expression.
                            //
                            // operand = ...
                            // JUMP (exp)
                            // |
                            // v
                            // target = PhiJoin(operand)
                            // ...
                            // Becomes
                            //
                            // operand = ...
                            // var = exp
                            // target = operand
                            // JUMP (var)
                            // |
                            // v
                            // target = PhiJoin(operand) // Removed later
                            // ...

                            public void visitExpr(final Expr expr) {
                                LeafExpr.StackExpr var = tree.newStack(expr.type());
                                var.setValueNumber(expr.valueNumber());

                                final Node p = expr.parent();
                                expr.setParent(null);
                                p.visit(new ReplaceVisitor(expr, var));

                                var = (LeafExpr.StackExpr) var.clone();
                                final StoreExpr store = new StoreExpr(var,
                                        expr, expr.type());
                                store.setValueNumber(expr.valueNumber());

                                final Stmt storeStmt = new ExprStmt(store);
                                storeStmt.setValueNumber(expr.valueNumber());

                                tree.addStmtBeforeJump(storeStmt);
                            }

                            public void visitStackExpr(final StackExpr expr) {
                            }

                            @Override
                            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                            }
                        });

                        final Stmt store = createStore(stmt.target(), operand);

                        if (CodeGenerator.DEBUG) {
                            System.out.println("insert for " + stmt + " "
                                    + store + " in " + pred);
                        }

                        tree.addStmtBeforeJump(store);
                    }
                }

                public void visitStmt(final Stmt stmt) {
                }

                @Override
                public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                }
            });
        }
    }

    /**
     * Removes blocks that contain no other statements than gotos, jumps,
     * returns, or labels. Other blocks that are invovled with the blocks being
     * removed are updated appropriately.
     */
    private void removeEmptyBlocks(final FlowGraph cfg) {
        final Set emptyBlocks = new HashSet();

        Iterator e = cfg.nodes().iterator();

        BLOCKS: while (e.hasNext()) {
            final Block block = (Block) e.next();

            // Collect any blocks that contain only gotos,
            // jsrs, rets, or labels.
            final Iterator stmts = block.tree().stmts().iterator();

            while (stmts.hasNext()) {
                final Stmt stmt = (Stmt) stmts.next();

                if ((stmt instanceof GotoStmt) || (stmt instanceof JsrStmt)
                        || (stmt instanceof RetStmt)
                        || (stmt instanceof LabelStmt)) {
                    continue;
                }

                // The block contains something other than the above, it is
                // not empty.
                continue BLOCKS;
            }

            emptyBlocks.add(block);
        }

        // We want to keep the source, init, and sink blocks even if
        // they're empty
        emptyBlocks.remove(cfg.source());
        emptyBlocks.remove(cfg.init());
        emptyBlocks.remove(cfg.sink());

        // Did the CFG change?
        boolean changed = true;

        while (changed) {
            changed = false;

            // Exclude the blocks that are control dependent on other blocks.
            final Set empty = new HashSet(emptyBlocks);
            empty.removeAll(cfg.iteratedPdomFrontier(cfg.nodes()));

            e = empty.iterator();

            while (e.hasNext()) {
                final Block block = (Block) e.next();

                if (CodeGenerator.DEBUG) {
                    System.out.println("removing empty " + block);
                }

                final Stmt last = block.tree().lastStmt();

                if (last instanceof GotoStmt) {
                    // All a block does is jump to another block
                    //
                    // jmp ... L
                    // L: goto M
                    // =>
                    // jmp ... M
                    final Block target = ((GotoStmt) last).target();

                    final Iterator preds = new ImmutableIterator(cfg
                            .preds(block));

                    while (preds.hasNext()) {
                        final Block pred = (Block) preds.next();
                        final Stmt predLast = pred.tree().lastStmt();
                        predLast.visit(new ReplaceTarget(block, target));

                        cfg.removeEdge(pred, block);
                        cfg.addEdge(pred, target);

                        changed = true;
                    }

                } else if (last instanceof RetStmt) {
                    // All a subroutine does is return

                    final Iterator preds = new ImmutableIterator(cfg
                            .preds(block));

                    while (preds.hasNext()) {
                        final Block pred = (Block) preds.next();

                        final Stmt predLast = pred.tree().lastStmt();

                        if (predLast instanceof JsrStmt) {
                            // The previous block is the jsr...
                            //
                            // jsr L ret to M
                            // M: ...
                            // L: ret // The body of the subroutine is empty
                            // =>
                            // goto M
                            // M: ...

                            final JsrStmt stmt = (JsrStmt) predLast;

                            final JumpStmt jump = new JumpStmt(stmt.follow()) {
                                @Override
                                public void visitForceChildren(TreeVisitor var1) {

                                }

                                @Override
                                public void visit(TreeVisitor var1) {

                                }

                                @Override
                                public Object clone() {
                                    return null;
                                }
                            };
                            jump.catchTargets().addAll(stmt.catchTargets());
                            stmt.replaceWith(jump);

                            stmt.sub().removePathsContaining(pred);

                        } else if (predLast instanceof GotoStmt) {
                            // The previous block ends in a goto. Move the ret
                            // up
                            // into the previous block, update catch targets of
                            // any
                            // exceptions thrown by the block terminated by the
                            // jump, and update the subroutine's exit block to
                            // be
                            // the previous block (in which the ret now
                            // resides).

                            final JumpStmt jump = (RetStmt) last.clone();
                            jump.catchTargets().addAll(
                                    ((JumpStmt) predLast).catchTargets());
                            predLast.replaceWith(jump);
                            ((RetStmt) last).sub().setExit(pred);
                        }

                        // Remove the block from the CFG
                        cfg.succs(pred).remove(block);
                        cfg.succs(pred).addAll(cfg.succs(block));

                        changed = true;
                    }

                } else if (last instanceof JsrStmt) {
                    // All the block does is a jsr
                    //
                    // goto L
                    // L: jsr M
                    // =>
                    // jsr M
                    // L: jsr M
                    final Iterator preds = new ImmutableIterator(cfg
                            .preds(block));

                    while (preds.hasNext()) {
                        final Block pred = (Block) preds.next();
                        final Stmt predLast = pred.tree().lastStmt();

                        if (predLast instanceof GotoStmt) {
                            final JsrStmt stmt = (JsrStmt) last;

                            final JumpStmt jump = new JsrStmt(stmt.sub(), stmt
                                    .follow());
                            jump.catchTargets().addAll(
                                    ((JumpStmt) predLast).catchTargets());
                            predLast.replaceWith(jump);

                            // The block is no longer a viable caller of the
                            // subroutine
                            stmt.sub().removePathsContaining(block);
                            stmt.sub().addPath(pred, stmt.follow());

                            cfg.addEdge(pred, stmt.sub().entry());
                            cfg.removeEdge(pred, block);

                            changed = true;
                        }
                    }

                } else {
                    throw new RuntimeException();
                }
            }

            if (changed) {
                cfg.removeUnreachable();

                // Remove any empty blocks that we've already deleted.
                emptyBlocks.retainAll(cfg.nodes());
            }
        }
    }

    /**
     * Allocate "registers" (LocalVariables) for the return addresses for each
     * subroutine in the method.
     *
     * @param cfg
     *            Control flow graph for the method
     * @param alloc
     *            Allocation (and information about) the local variables in the
     *            method.
     *
     * @see LocalVariable
     * @see LocalExpr
     */
    public void allocReturnAddresses(final FlowGraph cfg,
                                     final RegisterAllocator alloc) {
        // Allocate registers for the returnAddresses. Don't bother trying
        // to minimize the number of locals, just get a new one.
        final Iterator e = cfg.subroutines().iterator();

        while (e.hasNext()) {
            final Subroutine sub = (Subroutine) e.next();
            final LocalVariable var = alloc.newLocal(Type.ADDRESS);
            sub.setReturnAddress(var);
        }
    }

    /**
     * Create a ExprStmt that initializes a target variable to a default value
     * based on the type of the target.
     */
    protected Stmt createUndefinedStore(final VarExpr target) {
        if (target.type().isReference()) {
            return new ExprStmt(new StoreExpr(target, new ConstantExpr(null,
                    Type.OBJECT), target.type()));
        }

        if (target.type().isIntegral()) {
            return new ExprStmt(new StoreExpr(target, new ConstantExpr(
                    new Integer(0), Type.INTEGER), target.type()));
        }

        if (target.type().equals(Type.LONG)) {
            return new ExprStmt(new StoreExpr(target, new ConstantExpr(
                    new Long(0), Type.LONG), target.type()));
        }

        if (target.type().equals(Type.FLOAT)) {
            return new ExprStmt(new StoreExpr(target, new ConstantExpr(
                    new Float(0.0F), Type.FLOAT), target.type()));
        }

        if (target.type().equals(Type.DOUBLE)) {
            return new ExprStmt(new StoreExpr(target, new ConstantExpr(
                    new Double(0.0), Type.DOUBLE), target.type()));
        }

        throw new RuntimeException("Illegal type: " + target.type());
    }

    /**
     * Returns an ExprStmt that contains a store of the source into the target.
     */
    protected Stmt createStore(VarExpr target, final Expr source) {
        target = (VarExpr) target.clone();

        // Source is an undefined variable, initialize it
        if ((source instanceof VarExpr) && (source.def() == null)) {
            return createUndefinedStore(target);
        }

        return new ExprStmt(new StoreExpr(target, (Expr) source.clone(), target
                .type()));
    }

    /*
     * Using an InstructionVisitor generate the code...
     */

    // Several of the visit methods contain code for stack
    // optimization (place dups and swaps and eliminate temporary
    // variables). Nodes where swaps are to be placed are so
    // marked. The markings may appear at IfCmpStmt, InitStmt,
    // StoreExpr, ArithExpr, ArrayRefExpr, CallMethodExpr,
    // CallStaticExpr, NewMultiArrayExpr, ShiftExpr.
    public void visitExpr(final Expr expr) {
        throw new RuntimeException("Unhandled expression type: "
                + expr.getClass().getName());
    }

    public void visitExprStmt(final ExprStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        stmt.visitChildren(this);

        genPostponed(stmt);

        if (!(stmt.expr() instanceof StoreExpr)) {
            if (!stmt.expr().type().isVoid()) {
                if (stmt.expr().type().isWide()) {
                    method.addInstruction(Opcode.opcx_pop2);
                    stackHeight -= 2;

                } else {
                    method.addInstruction(Opcode.opcx_pop);
                    stackHeight -= 1;
                }
            }
        }
    }

    public void visitInitStmt(final InitStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }
    }

    public void visitGotoStmt(final GotoStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        genPostponed(stmt);

        final Block target = stmt.target();

        if (target != next) {
            method.addInstruction(Opcode.opcx_goto, stmt.target().label());
        }
    }

    public void visitIfCmpStmt(final IfCmpStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        final Block t = stmt.trueTarget();
        final Block f = stmt.falseTarget();

        if (f == next) {
            // Fall through to the false branch.
            genIfCmpStmt(stmt);

        } else if (t == next) {
            // Fall through to the true branch.
            stmt.negate();
            genIfCmpStmt(stmt);

        } else {
            // Generate a goto to the false branch after the if statement.
            genIfCmpStmt(stmt);

            method.addLabel(method.newLabelTrue()); // Tom changed to say "True"
            method.addInstruction(Opcode.opcx_goto, f.label());
        }
    }

    private void genIfCmpStmt(final IfCmpStmt stmt) {
        int opcode;

        stmt.visitChildren(this);

        genPostponed(stmt);

        final int cmp = stmt.comparison();

        if (stmt.left().type().isReference()) {

            switch (cmp) {
                case LeafExpr.IfStmt.EQ:
                    opcode = Opcode.opcx_if_acmpeq;
                    break;
                case LeafExpr.IfStmt.NE:
                    opcode = Opcode.opcx_if_acmpne;
                    break;
                default:
                    throw new RuntimeException();
            }

        } else {
            switch (cmp) {
                case LeafExpr.IfStmt.EQ:
                    opcode = Opcode.opcx_if_icmpeq;
                    break;
                case LeafExpr.IfStmt.NE:
                    opcode = Opcode.opcx_if_icmpne;
                    break;
                case LeafExpr.IfStmt.GT:
                    opcode = Opcode.opcx_if_icmpgt;
                    break;
                case LeafExpr.IfStmt.GE:
                    opcode = Opcode.opcx_if_icmpge;
                    break;
                case LeafExpr.IfStmt.LT:
                    opcode = Opcode.opcx_if_icmplt;
                    break;
                case LeafExpr.IfStmt.LE:
                    opcode = Opcode.opcx_if_icmple;
                    break;
                default:
                    throw new RuntimeException();
            }
        }

        method.addInstruction(opcode, stmt.trueTarget().label());
        stackHeight -= 2;
    }

    public void visitIfZeroStmt(final IfZeroStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        final Block t = stmt.trueTarget();
        final Block f = stmt.falseTarget();

        if (f == next) {
            // Fall through to the false branch.
            genIfZeroStmt(stmt);
        } else if (t == next) {
            // Fall through to the true branch.
            stmt.negate();
            genIfZeroStmt(stmt);
        } else {
            // Generate a goto to the false branch after the if statement.
            genIfZeroStmt(stmt);
            method.addLabel(method.newLabelTrue()); // Tom added "True"
            method.addInstruction(Opcode.opcx_goto, f.label());
        }
    }

    private void genIfZeroStmt(final IfZeroStmt stmt) {
        int opcode;

        stmt.expr().visit(this);

        genPostponed(stmt);

        final int cmp = stmt.comparison();

        if (stmt.expr().type().isReference()) {
            switch (cmp) {
                case LeafExpr.IfStmt.EQ:
                    opcode = Opcode.opcx_ifnull;
                    break;
                case LeafExpr.IfStmt.NE:
                    opcode = Opcode.opcx_ifnonnull;
                    break;
                default:
                    throw new RuntimeException();
            }

        } else {
            switch (cmp) {
                case LeafExpr.IfStmt.EQ:
                    opcode = Opcode.opcx_ifeq;
                    break;
                case LeafExpr.IfStmt.NE:
                    opcode = Opcode.opcx_ifne;
                    break;
                case LeafExpr.IfStmt.GT:
                    opcode = Opcode.opcx_ifgt;
                    break;
                case LeafExpr.IfStmt.GE:
                    opcode = Opcode.opcx_ifge;
                    break;
                case LeafExpr.IfStmt.LT:
                    opcode = Opcode.opcx_iflt;
                    break;
                case LeafExpr.IfStmt.LE:
                    opcode = Opcode.opcx_ifle;
                    break;
                default:
                    throw new RuntimeException();
            }
        }
        method.addInstruction(opcode, stmt.trueTarget().label());
        stackHeight -= 1;
    }

    public void visitLabelStmt(final LabelStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        stmt.visitChildren(this);

        genPostponed(stmt);

        method.addLabel(stmt.label());
    }

    public void visitMonitorStmt(final MonitorStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        stmt.visitChildren(this);

        genPostponed(stmt);

        if (stmt.kind() == MonitorStmt.ENTER) {
            method.addInstruction(Opcode.opcx_monitorenter);
            stackHeight -= 1;

        } else if (stmt.kind() == MonitorStmt.EXIT) {
            method.addInstruction(Opcode.opcx_monitorexit);
            stackHeight -= 1;

        } else {
            throw new IllegalArgumentException();
        }
    }

    public void visitPhiStmt(final PhiStmt stmt) {
        throw new RuntimeException("Cannot generate code for " + stmt);
    }

    public void visitRCExpr(final RCExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        // Move the rc forward as far as possible.
        //
        // For example, for the expression:
        //
        // rc(x).m(rc(a).b)
        //
        // we want to generate:
        //
        // aload x
        // aload a
        // rc 0
        // getfield <A.b>
        // rc 1
        // invoke <X.m>
        //
        // rather than:
        //
        // aload x
        // rc 0
        // aload a
        // rc 0
        // getfield <A.b>
        // invoke <X.m>
        //
        InstructionVisitor.Instruction postpone = null;

        Node parent = expr.parent();

        // If the parent is wrapped in a ZeroCheckExpr, then look at the
        // parent's parent.

        if (parent instanceof ZeroCheckExpr) {
            parent = parent.parent();
        }

        // If the depth is going to be > 0, postpone the rc instruction
        // to just before the getfield, putfield, invoke, xaload, xastore, etc.
        // If the stack depth for the rc is going to be 0, the rc will (be)
        // the next instruction generated anyway, so don't postpone.

        if (parent instanceof ArrayRefExpr) {
            final ArrayRefExpr p = (ArrayRefExpr) parent;

            if (expr == p.array()) {
                if (p.isDef()) {
                    // a[i] := r
                    // Stack at the xastore: ... a i r
                    postpone = new InstructionVisitor.Instruction(Opcode.opcx_rc, new Integer(p
                            .type().stackHeight() + 1));
                } else {
                    // use a[i]
                    // Stack at the xaload: ... a i
                    postpone = new InstructionVisitor.Instruction(Opcode.opcx_rc, new Integer(1));
                }
            }

        } else if (parent instanceof CallMethodExpr) {
            final CallMethodExpr p = (CallMethodExpr) parent;

            if (expr == p.receiver()) {
                // a.m(b, c)
                // Stack at the invoke: ... a b c
                final MemberRef method = p.method();
                final int depth = method.nameAndType().type().stackHeight();
                postpone = new InstructionVisitor.Instruction(Opcode.opcx_rc, new Integer(depth));
            }

        } else if (parent instanceof FieldExpr) {
            final FieldExpr p = (FieldExpr) parent;

            if (expr == p.object()) {
                if (p.isDef()) {
                    // a.b := r
                    // Stack at the putfield: ... a r
                    postpone = new InstructionVisitor.Instruction(Opcode.opcx_rc, new Integer(p
                            .type().stackHeight()));
                }
            }
        }

        if (postpone == null) {
            int depth = 0;

            if (expr.expr() instanceof StackExpr) {
                // If the rc works on a StackExpr, calculate its depth in the
                // stack. In all other cases, the rc will operate on whatever
                // is on top of the stack.
                final StackExpr stackVar = (StackExpr) expr.expr();
                depth = stackHeight - stackVar.index() - 1;
            }

            method.addInstruction(Opcode.opcx_rc, new Integer(depth));

        } else {
            postponedInstructions.put(parent, postpone);
        }
    }

    public void visitUCExpr(final UCExpr expr) {
        expr.visitChildren(this);

        if (true) {
            return;
        }

        genPostponed(expr);

        // Move the uc forward as far as possible.
        InstructionVisitor.Instruction postpone = null;

        final Node parent = expr.parent();

        // If the depth is going to be > 0, postpone the uc instruction
        // to just before the putfield. If the stack depth for the
        // uc is going to be 0, the uc will the next instruction
        // generated anyway, so don't postpone.

        if (parent instanceof FieldExpr) {
            final FieldExpr p = (FieldExpr) parent;

            if (expr == p.object()) {
                if (p.isDef()) {
                    // a.b := r
                    // Stack at the putfield: ... a r
                    if (expr.kind() == UCExpr.POINTER) {
                        postpone = new InstructionVisitor.Instruction(Opcode.opcx_aupdate,
                                new Integer(p.type().stackHeight()));

                    } else if (expr.kind() == UCExpr.SCALAR) {
                        postpone = new InstructionVisitor.Instruction(Opcode.opcx_supdate,
                                new Integer(p.type().stackHeight()));
                    } else {
                        throw new RuntimeException();
                    }
                }
            }
        }

        if (postpone == null) {
            int depth = 0;

            if (expr.expr() instanceof StackExpr) {
                // If the UCExpr operates on a stack variable, use that to
                // determine the depth. In all other cases, use 0.
                final StackExpr stackVar = (StackExpr) expr.expr();
                depth = stackHeight - stackVar.index() - 1;
            }

            if (expr.kind() == UCExpr.POINTER) {
                method.addInstruction(Opcode.opcx_aupdate, new Integer(depth));
            } else if (expr.kind() == UCExpr.SCALAR) {
                method.addInstruction(Opcode.opcx_supdate, new Integer(depth));
            } else {
                throw new RuntimeException();
            }

        } else {
            postponedInstructions.put(parent, postpone);
        }
    }

    public void visitRetStmt(final RetStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        genPostponed(stmt);

        final Subroutine sub = stmt.sub();
        method.addInstruction(Opcode.opcx_ret, sub.returnAddress());
    }

    public void visitReturnExprStmt(final ReturnExprStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        stmt.visitChildren(this);

        genPostponed(stmt);

        final Type type = stmt.expr().type();

        // Stack should be empty after return

        if (type.isReference()) {
            method.addInstruction(Opcode.opcx_areturn);
            stackHeight = 0;
        } else if (type.isIntegral()) {
            method.addInstruction(Opcode.opcx_ireturn);
            stackHeight = 0;
        } else if (type.equals(Type.LONG)) {
            method.addInstruction(Opcode.opcx_lreturn);
            stackHeight = 0;
        } else if (type.equals(Type.FLOAT)) {
            method.addInstruction(Opcode.opcx_freturn);
            stackHeight = 0;
        } else if (type.equals(Type.DOUBLE)) {
            method.addInstruction(Opcode.opcx_dreturn);
            stackHeight = 0;
        }
    }

    public void visitReturnStmt(final ReturnStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        genPostponed(stmt);

        stmt.visitChildren(this);
        method.addInstruction(Opcode.opcx_return);

        // Stack height is zero after return
        stackHeight = 0;
    }

    public void visitStoreExpr(final StoreExpr expr) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + expr);
        }

        final MemExpr lhs = expr.target();
        final Expr rhs = expr.expr();

        boolean returnsValue = !(expr.parent() instanceof ExprStmt);

        // eliminate the store if the both sides have the same target

        if (!returnsValue) {
            if ((lhs instanceof LocalExpr) && (rhs instanceof LocalExpr)) {

                // The second condition checks that the right sides have indeed
                // been stored-- otherwise (ie, if we're just keeping it on the
                // stack), we should not eliminate this store

                if ((((LocalExpr) lhs).index() == ((LocalExpr) rhs).index())

                        && (!CodeGenerator.OPT_STACK || currentSO
                        .shouldStore(((LocalExpr) ((LocalExpr) rhs)
                                .def())))) {
                    return;
                }
            }

            // Special case to handle L := L + k.
            // Generate "iinc L, k" instead of "iload L; ldc k; iadd; istore L".
            //
            // TODO: for L := M + k, generate "iload M; istore L; iinc L, k".
            //

            /*
             * This next special case was modified for stack optimization. If L
             * is marked for a dup, the fact that its value is never on the
             * stack means we don't have anything to dup-- we need to load
             * instead. (Things get more complicated if it was marked for a
             * dup_x2, but that's not likely to happen)
             */

            if ((lhs instanceof LocalExpr) && lhs.type().isIntegral()) {
                Integer value = null;
                // eliminate the store if the both sides have the same target

                final int index = ((LocalExpr) lhs).index();

                if (rhs instanceof ArithExpr) {
                    final ArithExpr arith = (ArithExpr) rhs;

                    final Expr left = arith.left();
                    final Expr right = arith.right();

                    if ((left instanceof LocalExpr)
                            && (index == ((LocalExpr) left).index())
                            && (right instanceof ConstantExpr)) {

                        final ConstantExpr c = (ConstantExpr) right;

                        if (c.value() instanceof Integer) {
                            value = (Integer) c.value();
                        }

                    } else if ((right instanceof LocalExpr)
                            && (index == ((LocalExpr) right).index())
                            && (left instanceof ConstantExpr)
                            && (arith.operation() == ArithExpr.ADD)) {

                        // This will not work for x = c - x because it is not
                        // the
                        // same as x = x - c.

                        final ConstantExpr c = (ConstantExpr) left;

                        if (c.value() instanceof Integer) {
                            value = (Integer) c.value();
                        }
                    }

                    if ((value != null) && (arith.operation() == ArithExpr.SUB)) {
                        value = new Integer(-value.intValue());
                    } else if (arith.operation() != ArithExpr.ADD) {
                        value = null;
                    }
                }

                if (value != null) {
                    final int incr = value.intValue();

                    if (incr == 0) {
                        // No need to increment by 0.

                        // for a better understanding of what's going on in
                        // these
                        // additions, see VisitLocalExpr, where we do basically
                        // the
                        // same thing.

                        if (CodeGenerator.OPT_STACK) {
                            int dups, dup_x1s, dup_x2s;
                            dups = currentSO.dups((LocalExpr) lhs);
                            dup_x1s = currentSO.dup_x1s((LocalExpr) lhs);
                            dup_x2s = currentSO.dup_x2s((LocalExpr) lhs);
                            for (int i = 0; i < dup_x2s; i++) {
                                // This is really awful, but be consoled in that
                                // it is
                                // highly improbable to happen... this is just
                                // to make correct code in the chance that we
                                // have something like this.
                                method.addInstruction(Opcode.opcx_ldc,
                                        new Integer(0));
                                method.addInstruction(Opcode.opc_dup_x2);
                                method.addInstruction(Opcode.opc_pop);
                                stackHeight += 1;
                            }
                            for (int i = 0; i < dup_x1s; i++) {
                                method.addInstruction(Opcode.opcx_ldc,
                                        new Integer(0));
                                method.addInstruction(Opcode.opc_swap);
                                stackHeight += 1;
                            }
                            for (int i = 0; i < dups; i++) {
                                method.addInstruction(Opcode.opcx_ldc,
                                        new Integer(0));
                                stackHeight += 1;
                            }
                        }

                        return;

                    } else if ((short) incr == incr) {
                        // Only generate an iinc if the increment fits in
                        // a short.
                        method.addInstruction(Opcode.opcx_iinc, new InstructionVisitor.IncOperand(
                                new LocalVariable(index), incr));
                        if (CodeGenerator.OPT_STACK) {
                            int dups, dup_x1s, dup_x2s;
                            dups = currentSO.dups((LocalExpr) lhs);
                            dup_x1s = currentSO.dup_x1s((LocalExpr) lhs);
                            dup_x2s = currentSO.dup_x2s((LocalExpr) lhs);
                            for (int i = 0; i < dup_x2s; i++) {
                                method.addInstruction(Opcode.opcx_istore,
                                        new LocalVariable(((LocalExpr) lhs)
                                                .index()));
                                method.addInstruction(Opcode.opc_dup_x2);
                                method.addInstruction(Opcode.opc_pop);
                                stackHeight += 1;
                            }
                            for (int i = 0; i < dup_x1s; i++) {
                                method.addInstruction(Opcode.opcx_iload,
                                        new LocalVariable(((LocalExpr) lhs)
                                                .index()));
                                method.addInstruction(Opcode.opc_swap);
                                stackHeight += 1;
                            }
                            for (int i = 0; i < dups; i++) {
                                method.addInstruction(Opcode.opcx_iload,
                                        new LocalVariable(((LocalExpr) lhs)
                                                .index()));
                                stackHeight += 1;
                            }
                        }

                        return;
                    }
                }
            }
        }

        // Generate, and return the value.
        lhs.visitChildren(this);
        rhs.visit(this);

        if (returnsValue) {
            if (lhs instanceof ArrayRefExpr) {
                // array index rhs --> rhs array index rhs
                if (rhs.type().isWide()) {
                    method.addInstruction(Opcode.opcx_dup2_x2);
                    stackHeight += 2;
                } else {
                    method.addInstruction(Opcode.opcx_dup_x2);
                    stackHeight += 1;
                }

            } else if (lhs instanceof FieldExpr) {
                // object rhs --> rhs object rhs
                if (rhs.type().isWide()) {
                    method.addInstruction(Opcode.opcx_dup2_x1);
                    stackHeight += 2;
                } else {
                    method.addInstruction(Opcode.opcx_dup_x1);
                    stackHeight += 1;
                }

            } else {
                // rhs --> rhs rhs
                if (rhs.type().isWide()) {
                    method.addInstruction(Opcode.opcx_dup2);
                    stackHeight += 2;
                } else {
                    method.addInstruction(Opcode.opcx_dup);
                    stackHeight += 1;
                }
            }
        }

        genPostponed(expr);
        lhs.visitOnly(this);
    }

    public void visitAddressStoreStmt(final AddressStoreStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        genPostponed(stmt);

        final Subroutine sub = stmt.sub();
        method.addInstruction(Opcode.opcx_astore, sub.returnAddress());
        stackHeight -= 1;
    }

    public void visitJsrStmt(final JsrStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        genPostponed(stmt);

        final Block entry = stmt.sub().entry();
        method.addInstruction(Opcode.opcx_jsr, entry.label());
        stackHeight += 1;

        if (stmt.follow() != next) {
            method.addLabel(method.newLabelTrue());
            method.addInstruction(Opcode.opcx_goto, stmt.follow().label());
        }
    }

    public void visitSwitchStmt(final SwitchStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        stmt.visitChildren(this);

        genPostponed(stmt);

        final Label[] targets = new Label[stmt.targets().length];

        for (int i = 0; i < targets.length; i++) {
            targets[i] = stmt.targets()[i].label();
        }

        method.addInstruction(Opcode.opcx_switch, new Switch(stmt
                .defaultTarget().label(), targets, stmt.values()));
        stackHeight -= 1;
    }

    public void visitStackManipStmt(final StackManipStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        genPostponed(stmt);

        // All the children are stack variables, so don't so anything.

        switch (stmt.kind()) {
            case StackManipStmt.SWAP:
                method.addInstruction(Opcode.opcx_swap);
                break;
            case StackManipStmt.DUP:
                method.addInstruction(Opcode.opcx_dup);
                stackHeight += 1;
                break;
            case StackManipStmt.DUP_X1:
                method.addInstruction(Opcode.opcx_dup_x1);
                stackHeight += 1;
                break;
            case StackManipStmt.DUP_X2:
                method.addInstruction(Opcode.opcx_dup_x2);
                stackHeight += 1;
                break;
            case StackManipStmt.DUP2:
                method.addInstruction(Opcode.opcx_dup2);
                stackHeight += 2;
                break;
            case StackManipStmt.DUP2_X1:
                method.addInstruction(Opcode.opcx_dup2_x1);
                stackHeight += 2;
                break;
            case StackManipStmt.DUP2_X2:
                method.addInstruction(Opcode.opcx_dup2_x2);
                stackHeight += 2;
                break;
        }
    }

    public void visitThrowStmt(final ThrowStmt stmt) {
        if (CodeGenerator.DEBUG) {
            System.out.println("code for " + stmt);
        }

        stmt.visitChildren(this);

        genPostponed(stmt);

        method.addInstruction(Opcode.opcx_athrow);
    }

    public void visitSCStmt(final SCStmt stmt) {
        stmt.visitChildren(this);
        genPostponed(stmt);
        method.addInstruction(Opcode.opcx_aswizzle);
        stackHeight -= 2;
    }

    public void visitSRStmt(final SRStmt stmt) {
        stmt.visitChildren(this);
        genPostponed(stmt);
        method.addInstruction(Opcode.opcx_aswrange);
        stackHeight -= 3;
    }

    public void visitArithExpr(final ArithExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        final int[][] opcode = new int[][] {
                { Opcode.opcx_iadd, Opcode.opcx_ladd, Opcode.opcx_fadd,
                        Opcode.opcx_dadd },
                { Opcode.opcx_iand, Opcode.opcx_land, Opcode.opcx_nop,
                        Opcode.opcx_nop },
                { Opcode.opcx_idiv, Opcode.opcx_ldiv, Opcode.opcx_fdiv,
                        Opcode.opcx_ddiv },
                { Opcode.opcx_imul, Opcode.opcx_lmul, Opcode.opcx_fmul,
                        Opcode.opcx_dmul },
                { Opcode.opcx_ior, Opcode.opcx_lor, Opcode.opcx_nop,
                        Opcode.opcx_nop },
                { Opcode.opcx_irem, Opcode.opcx_lrem, Opcode.opcx_frem,
                        Opcode.opcx_drem },
                { Opcode.opcx_isub, Opcode.opcx_lsub, Opcode.opcx_fsub,
                        Opcode.opcx_dsub },
                { Opcode.opcx_ixor, Opcode.opcx_lxor, Opcode.opcx_nop,
                        Opcode.opcx_nop },
                { Opcode.opcx_nop, Opcode.opcx_lcmp, Opcode.opcx_nop,
                        Opcode.opcx_nop },
                { Opcode.opcx_nop, Opcode.opcx_nop, Opcode.opcx_fcmpl,
                        Opcode.opcx_dcmpl },
                { Opcode.opcx_nop, Opcode.opcx_nop, Opcode.opcx_fcmpg,
                        Opcode.opcx_dcmpg } };

        final int[][] stackChange = new int[][] { { -1, -2, -1, -2 },
                { -1, -2, 0, 0 }, { -1, -2, -1, -2 }, { -1, -2, -1, -2 },
                { -1, -2, 0, 0 }, { -1, -2, -1, -2 }, { -1, -2, -1, -2 },
                { -1, -2, 0, 0 }, { 0, -3, 0, 0 }, { 0, 0, -1, -3 },
                { 0, 0, -1, -3 } };

        int type;

        if (expr.left().type().isIntegral()) {
            type = 0;

        } else if (expr.left().type().equals(Type.LONG)) {
            type = 1;

        } else if (expr.left().type().equals(Type.FLOAT)) {
            type = 2;

        } else if (expr.left().type().equals(Type.DOUBLE)) {
            type = 3;

        } else {
            throw new IllegalArgumentException("Can't generate code for type: "
                    + expr.left().type() + " (expr " + expr + ")");
        }

        switch (expr.operation()) {
            case ArithExpr.ADD:
                method.addInstruction(opcode[0][type]);
                stackHeight += stackChange[0][type];
                break;
            case ArithExpr.AND:
                method.addInstruction(opcode[1][type]);
                stackHeight += stackChange[1][type];
                break;
            case ArithExpr.DIV:
                method.addInstruction(opcode[2][type]);
                stackHeight += stackChange[2][type];
                break;
            case ArithExpr.MUL:
                method.addInstruction(opcode[3][type]);
                stackHeight += stackChange[3][type];
                break;
            case ArithExpr.IOR:
                method.addInstruction(opcode[4][type]);
                stackHeight += stackChange[4][type];
                break;
            case ArithExpr.REM:
                method.addInstruction(opcode[5][type]);
                stackHeight += stackChange[5][type];
                break;
            case ArithExpr.SUB:
                method.addInstruction(opcode[6][type]);
                stackHeight += stackChange[6][type];
                break;
            case ArithExpr.XOR:
                method.addInstruction(opcode[7][type]);
                stackHeight += stackChange[7][type];
                break;
            case ArithExpr.CMP:
                method.addInstruction(opcode[8][type]);
                stackHeight += stackChange[8][type];
                break;
            case ArithExpr.CMPL:
                method.addInstruction(opcode[9][type]);
                stackHeight += stackChange[9][type];
                break;
            case ArithExpr.CMPG:
                method.addInstruction(opcode[10][type]);
                stackHeight += stackChange[10][type];
                break;
        }
    }

    public void visitArrayLengthExpr(final ArrayLengthExpr expr) {
        expr.visitChildren(this);
        method.addInstruction(Opcode.opcx_arraylength);
    }

    public void visitArrayRefExpr(final ArrayRefExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        int opcode;

        if (expr.isDef()) {
            if (expr.elementType().isReference()) {
                opcode = Opcode.opcx_aastore;
                stackHeight -= 3;
            } else if (expr.elementType().equals(Type.BYTE)) {
                opcode = Opcode.opcx_bastore;
                stackHeight -= 3;
            } else if (expr.elementType().equals(Type.CHARACTER)) {
                opcode = Opcode.opcx_castore;
                stackHeight -= 3;
            } else if (expr.elementType().equals(Type.SHORT)) {
                opcode = Opcode.opcx_sastore;
                stackHeight -= 3;
            } else if (expr.elementType().equals(Type.INTEGER)) {
                opcode = Opcode.opcx_iastore;
                stackHeight -= 3;
            } else if (expr.elementType().equals(Type.LONG)) {
                opcode = Opcode.opcx_lastore;
                stackHeight -= 4;
            } else if (expr.elementType().equals(Type.FLOAT)) {
                opcode = Opcode.opcx_fastore;
                stackHeight -= 3;
            } else if (expr.elementType().equals(Type.DOUBLE)) {
                opcode = Opcode.opcx_dastore;
                stackHeight -= 4;
            } else {
                throw new IllegalArgumentException(
                        "Can't generate code for type: " + expr.type()
                                + " (expr " + expr + ")");
            }
        } else {
            if (expr.elementType().isReference()) {
                opcode = Opcode.opcx_aaload;
                stackHeight -= 1;
            } else if (expr.elementType().equals(Type.BYTE)) {
                opcode = Opcode.opcx_baload;
                stackHeight -= 1;
            } else if (expr.elementType().equals(Type.CHARACTER)) {
                opcode = Opcode.opcx_caload;
                stackHeight -= 1;
            } else if (expr.elementType().equals(Type.SHORT)) {
                opcode = Opcode.opcx_saload;
                stackHeight -= 1;
            } else if (expr.elementType().equals(Type.INTEGER)) {
                opcode = Opcode.opcx_iaload;
                stackHeight -= 1;
            } else if (expr.elementType().equals(Type.LONG)) {
                opcode = Opcode.opcx_laload;
                stackHeight -= 0;
            } else if (expr.elementType().equals(Type.FLOAT)) {
                opcode = Opcode.opcx_faload;
                stackHeight -= 1;
            } else if (expr.elementType().equals(Type.DOUBLE)) {
                opcode = Opcode.opcx_daload;
                stackHeight -= 0;
            } else {
                throw new IllegalArgumentException(
                        "Can't generate code for type: " + expr.type()
                                + " (expr " + expr + ")");
            }
        }

        method.addInstruction(opcode);
    }

    public void visitCallMethodExpr(final CallMethodExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        int opcode;

        if (expr.kind() == CallMethodExpr.VIRTUAL) {
            opcode = Opcode.opcx_invokevirtual;
        } else if (expr.kind() == CallMethodExpr.NONVIRTUAL) {
            opcode = Opcode.opcx_invokespecial;
        } else if (expr.kind() == CallMethodExpr.INTERFACE) {
            opcode = Opcode.opcx_invokeinterface;
        } else {
            throw new IllegalArgumentException();
        }

        method.addInstruction(opcode, expr.method());

        // Pop reciever object off stack
        stackHeight -= 1;

        // Pop each parameter off stack
        final Expr[] params = expr.params();
        for (int i = 0; i < params.length; i++) {
            stackHeight -= params[i].type().stackHeight();
        }
    }

    public void visitCallStaticExpr(final CallStaticExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        method.addInstruction(Opcode.opcx_invokestatic, expr.method());

        // Pop each parameter off stack
        final Expr[] params = expr.params();
        for (int i = 0; i < params.length; i++) {
            stackHeight -= params[i].type().stackHeight();
        }
    }

    public void visitCastExpr(final CastExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        if (expr.castType().isReference()) {
            method.addInstruction(Opcode.opcx_checkcast, expr.castType());
            return;
        }

        final int opType = expr.expr().type().typeCode();
        final int castType = expr.castType().typeCode();

        switch (opType) {
            case Type.BYTE_CODE:
            case Type.SHORT_CODE:
            case Type.CHARACTER_CODE:
            case Type.INTEGER_CODE:
                switch (castType) {
                    case Type.BYTE_CODE:
                        method.addInstruction(Opcode.opcx_i2b);
                        return;
                    case Type.SHORT_CODE:
                        method.addInstruction(Opcode.opcx_i2s);
                        return;
                    case Type.CHARACTER_CODE:
                        method.addInstruction(Opcode.opcx_i2c);
                        return;
                    case Type.INTEGER_CODE:
                        return;
                    case Type.LONG_CODE:
                        method.addInstruction(Opcode.opcx_i2l);
                        stackHeight += 1;
                        return;
                    case Type.FLOAT_CODE:
                        method.addInstruction(Opcode.opcx_i2f);
                        return;
                    case Type.DOUBLE_CODE:
                        method.addInstruction(Opcode.opcx_i2d);
                        stackHeight += 1;
                        return;
                }
                throw new IllegalArgumentException("Can't generate cast for type "
                        + Type.getType(castType));
                // new Type(castType));

            case Type.LONG_CODE:
                switch (castType) {
                    case Type.BYTE_CODE:
                        method.addInstruction(Opcode.opcx_l2i);
                        stackHeight -= 1;
                        method.addInstruction(Opcode.opcx_i2b);
                        return;
                    case Type.SHORT_CODE:
                        method.addInstruction(Opcode.opcx_l2i);
                        stackHeight -= 1;
                        method.addInstruction(Opcode.opcx_i2s);
                        return;
                    case Type.CHARACTER_CODE:
                        method.addInstruction(Opcode.opcx_l2i);
                        stackHeight -= 1;
                        method.addInstruction(Opcode.opcx_i2c);
                        return;
                    case Type.INTEGER_CODE:
                        method.addInstruction(Opcode.opcx_l2i);
                        stackHeight -= 1;
                        return;
                    case Type.LONG_CODE:
                        return;
                    case Type.FLOAT_CODE:
                        method.addInstruction(Opcode.opcx_l2f);
                        stackHeight -= 1;
                        return;
                    case Type.DOUBLE_CODE:
                        method.addInstruction(Opcode.opcx_l2d);
                        return;
                }

                throw new IllegalArgumentException("Can't generate cast for type "
                        + Type.getType(castType));
                // new Type(castType));

            case Type.FLOAT_CODE:
                switch (castType) {
                    case Type.BYTE_CODE:
                        method.addInstruction(Opcode.opcx_f2i);
                        method.addInstruction(Opcode.opcx_i2b);
                        return;
                    case Type.SHORT_CODE:
                        method.addInstruction(Opcode.opcx_f2i);
                        method.addInstruction(Opcode.opcx_i2s);
                        return;
                    case Type.CHARACTER_CODE:
                        method.addInstruction(Opcode.opcx_f2i);
                        method.addInstruction(Opcode.opcx_i2c);
                        return;
                    case Type.INTEGER_CODE:
                        method.addInstruction(Opcode.opcx_f2i);
                        return;
                    case Type.LONG_CODE:
                        method.addInstruction(Opcode.opcx_f2l);
                        stackHeight += 1;
                        return;
                    case Type.FLOAT_CODE:
                        return;
                    case Type.DOUBLE_CODE:
                        method.addInstruction(Opcode.opcx_f2d);
                        stackHeight += 1;
                        return;
                }

                throw new IllegalArgumentException("Can't generate cast for type "
                        + Type.getType(castType));
                // new Type(castType));

            case Type.DOUBLE_CODE:
                switch (castType) {
                    case Type.BYTE_CODE:
                        method.addInstruction(Opcode.opcx_d2i);
                        stackHeight -= 1;
                        method.addInstruction(Opcode.opcx_i2b);
                        return;
                    case Type.SHORT_CODE:
                        method.addInstruction(Opcode.opcx_d2i);
                        stackHeight -= 1;
                        method.addInstruction(Opcode.opcx_i2s);
                        return;
                    case Type.CHARACTER_CODE:
                        method.addInstruction(Opcode.opcx_d2i);
                        stackHeight -= 1;
                        method.addInstruction(Opcode.opcx_i2c);
                        return;
                    case Type.INTEGER_CODE:
                        method.addInstruction(Opcode.opcx_d2i);
                        stackHeight -= 1;
                        return;
                    case Type.LONG_CODE:
                        method.addInstruction(Opcode.opcx_d2l);
                        return;
                    case Type.FLOAT_CODE:
                        method.addInstruction(Opcode.opcx_d2f);
                        return;
                    case Type.DOUBLE_CODE:
                        return;
                }

                throw new IllegalArgumentException("Can't generate cast for type "
                        + Type.getType(castType));
                // new Type(castType));
            default:
                throw new IllegalArgumentException("Can't generate cast from type "
                        + Type.getType(opType));
                // new Type(castType));
        }
    }

    public void visitConstantExpr(final ConstantExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        method.addInstruction(Opcode.opcx_ldc, expr.value());
        stackHeight += expr.type().stackHeight();
    }

    public boolean nowb = false;

    public void visitFieldExpr(final FieldExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);

        if (expr.isDef()) {

            boolean UC = false; // Do we need an UC?

            // Look at the FieldExpr's object for a UCExpr
            Node check = expr.object();
            while (check instanceof CheckExpr) {
                if (check instanceof UCExpr) {
                    UC = true;
                    break;
                }

                final CheckExpr c = (CheckExpr) check;
                check = c.expr();
            }

            // Do we need to perform the write barrier?
            if (!UC && CodeGenerator.USE_PERSISTENT) {
                /*
                 * System.out.println("Emitting a putfield_nowb in " +
                 * this.method.declaringClass().classInfo().name() + "." +
                 * this.method.name());
                 */
                nowb = true;

                // I commented out the next line because it generated a compiler
                // error, and I figured it was just about some unimportant
                // persistance stuff --Tom
                // method.addInstruction(opcx_putfield_nowb, expr.field());

            } else {
                method.addInstruction(Opcode.opcx_putfield, expr.field());

            }

            stackHeight -= 1; // object
            stackHeight -= expr.type().stackHeight();

        } else {
            method.addInstruction(Opcode.opcx_getfield, expr.field());
            stackHeight -= 1; // pop object
            stackHeight += expr.type().stackHeight();
        }
    }

    public void visitInstanceOfExpr(final InstanceOfExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);
        method.addInstruction(Opcode.opcx_instanceof, expr.checkType());
    }

    public void visitLocalExpr(final LocalExpr expr) {

        genPostponed(expr);

        final boolean cat2 = expr.type().isWide(); // how many stack positions
        // does
        // this take up?

        int opcode = -1; // -1 is the flag that it hasn't yet been assigned
        // a real value

        if (CodeGenerator.DB_OPT_STACK) {
            currentSO.infoDisplay(expr);
        }

        if (expr.isDef()) {

            if (!CodeGenerator.OPT_STACK || currentSO.shouldStore(expr)) {

                if (expr.type().isAddress()) {
                    opcode = Opcode.opcx_astore;
                    stackHeight -= 1;
                } else if (expr.type().isReference()) {
                    opcode = Opcode.opcx_astore;
                    stackHeight -= 1;
                } else if (expr.type().isIntegral()) {
                    opcode = Opcode.opcx_istore;
                    stackHeight -= 1;
                } else if (expr.type().equals(Type.LONG)) {
                    opcode = Opcode.opcx_lstore;
                    stackHeight -= 2;
                } else if (expr.type().equals(Type.FLOAT)) {
                    opcode = Opcode.opcx_fstore;
                    stackHeight -= 1;
                } else if (expr.type().equals(Type.DOUBLE)) {
                    opcode = Opcode.opcx_dstore;
                    stackHeight -= 2;
                } else {
                    throw new IllegalArgumentException(
                            "Can't generate code for type: " + expr.type()
                                    + " (expr " + expr + ")");
                }
            }
        }

        else {

            if (CodeGenerator.OPT_STACK && currentSO.onStack(expr)) { // don't
                // load
                // if
                // it's
                // already
                // on
                // the stack
                if (currentSO.shouldSwap(expr)) {
                    if (cat2) {
                        throw new IllegalArgumentException(
                                "Can't swap for wide expression "
                                        + expr.toString() + " of type "
                                        + expr.type().toString());
                    } else {
                        opcode = Opcode.opc_swap;
                        stackHeight -= 1;
                    }
                }
            } else {

                if (expr.type().isReference()) {
                    opcode = Opcode.opcx_aload;
                    stackHeight += 1;
                } else if (expr.type().isIntegral()) {
                    opcode = Opcode.opcx_iload;
                    stackHeight += 1;
                } else if (expr.type().equals(Type.LONG)) {
                    opcode = Opcode.opcx_lload;
                    stackHeight += 2;
                } else if (expr.type().equals(Type.FLOAT)) {
                    opcode = Opcode.opcx_fload;
                    stackHeight += 1;
                } else if (expr.type().equals(Type.DOUBLE)) {
                    opcode = Opcode.opcx_dload;
                    stackHeight += 2;
                } else {
                    throw new IllegalArgumentException(
                            "Can't generate code for type: " + expr.type()
                                    + " (expr " + expr + ")");
                }
            }
        }

        if (opcode == Opcode.opc_swap) {
            method.addInstruction(opcode); // don't give
        } else if ((opcode != -1) && !(expr.isDef())) { // if this is a load, we
            // want
            // the load before any dups.
            method.addInstruction(opcode, new LocalVariable(expr.index()));

            if (MethodEditor.OPT_STACK_2) {
                method.rememberDef(expr);
            }

        }

        if (CodeGenerator.OPT_STACK) {
            // generate dups for this value on top of the stack
            int dups, dup_x1s, dup_x2s;
            dups = currentSO.dups(expr);
            dup_x1s = currentSO.dup_x1s(expr);
            dup_x2s = currentSO.dup_x2s(expr);

            for (int i = 0; i < dup_x2s; i++) {
                if (cat2) { // (cat2 is for wide types)
                    method.addInstruction(Opcode.opc_dup2_x2);
                    stackHeight += 2;
                } else {
                    method.addInstruction(Opcode.opc_dup_x2);
                    stackHeight += 1;
                }
            }
            for (int i = 0; i < dup_x1s; i++) {
                if (cat2) {
                    method.addInstruction(Opcode.opc_dup2_x1);
                    stackHeight += 2;
                } else {
                    method.addInstruction(Opcode.opc_dup_x1);
                    stackHeight += 1;
                }
            }
            for (int i = 0; i < dups; i++) {
                if (cat2) {
                    method.addInstruction(Opcode.opc_dup2);
                    stackHeight += 2;
                } else {
                    method.addInstruction(Opcode.opc_dup);
                    stackHeight += 1;
                }
            }
        }

        // if we have an opcode for a def (i.e., a store), generate it
        if ((opcode != -1) && expr.isDef()) {
            method.addInstruction(opcode, new LocalVariable(expr.index()));

            if (MethodEditor.OPT_STACK_2) {
                method.rememberDef(expr);
            }

        }

        if (CodeGenerator.OPT_STACK // if we shouldn't store,
                && !currentSO.shouldStore(expr)) { // an extra thing will be
            if (cat2) { // on the stack. pop it
                method.addInstruction(Opcode.opc_pop2);
                stackHeight -= 2;
            } else {
                method.addInstruction(Opcode.opc_pop);
                stackHeight -= 1;
            }
        }
        // (if this leaves a useless dup/pop combination, let peephole fix it)

        // method.addInstruction(opcode, new LocalVariable(expr.index()));
    }

    public void visitNegExpr(final NegExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        if (expr.type().isIntegral()) {
            method.addInstruction(Opcode.opcx_ineg);
        } else if (expr.type().equals(Type.FLOAT)) {
            method.addInstruction(Opcode.opcx_fneg);
        } else if (expr.type().equals(Type.LONG)) {
            method.addInstruction(Opcode.opcx_lneg);
        } else if (expr.type().equals(Type.DOUBLE)) {
            method.addInstruction(Opcode.opcx_dneg);
        } else {
            throw new IllegalArgumentException("Can't generate code for type: "
                    + expr.type() + " (expr " + expr + ")");
        }
    }

    public void visitNewArrayExpr(final NewArrayExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        method.addInstruction(Opcode.opcx_newarray, expr.elementType());
    }

    public void visitNewExpr(final NewExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        method.addInstruction(Opcode.opcx_new, expr.objectType());
        stackHeight += 1;
    }

    public void visitNewMultiArrayExpr(final NewMultiArrayExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        method.addInstruction(Opcode.opcx_multianewarray,
                new InstructionVisitor.MultiArrayOperand(expr.elementType().arrayType(
                        expr.dimensions().length), expr.dimensions().length));
        stackHeight -= expr.dimensions().length;
        stackHeight += 1;
    }

    public void visitReturnAddressExpr(final ReturnAddressExpr expr) {
        genPostponed(expr);
    }

    public void visitShiftExpr(final ShiftExpr expr) {
        expr.visitChildren(this);

        genPostponed(expr);

        if (expr.type().isIntegral()) {
            if (expr.dir() == ShiftExpr.LEFT) {
                method.addInstruction(Opcode.opcx_ishl);
                stackHeight -= 1;
            } else if (expr.dir() == ShiftExpr.RIGHT) {
                method.addInstruction(Opcode.opcx_ishr);
                stackHeight -= 1;
            } else {
                method.addInstruction(Opcode.opcx_iushr);
                stackHeight -= 1;
            }
        } else if (expr.type().equals(Type.LONG)) {
            if (expr.dir() == ShiftExpr.LEFT) {
                method.addInstruction(Opcode.opcx_lshl);
                stackHeight -= 1;
            } else if (expr.dir() == ShiftExpr.RIGHT) {
                method.addInstruction(Opcode.opcx_lshr);
                stackHeight -= 1;
            } else {
                method.addInstruction(Opcode.opcx_lushr);
                stackHeight -= 1;
            }
        } else {
            throw new IllegalArgumentException("Can't generate code for type: "
                    + expr.type() + " (expr " + expr + ")");
        }
    }

    public void visitDefExpr(final DefExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);
    }

    public void visitCatchExpr(final CatchExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);
    }

    public void visitStackExpr(final StackExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);
    }

    @Override
    public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

    }

    public void visitStaticFieldExpr(final StaticFieldExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);

        if (expr.isDef()) {
            method.addInstruction(Opcode.opcx_putstatic, expr.field());
            stackHeight -= expr.type().stackHeight();
        } else {
            method.addInstruction(Opcode.opcx_getstatic, expr.field());
            stackHeight += expr.type().stackHeight();
        }
    }

    public void visitZeroCheckExpr(final ZeroCheckExpr expr) {
        expr.visitChildren(this);
        genPostponed(expr);
    }

    private void genPostponed(final Node node) {
        final InstructionVisitor.Instruction inst = (InstructionVisitor.Instruction) postponedInstructions.get(node);

        if (inst != null) {
            method.addInstruction(inst);
            // Luckily, the rc and aupdate don't change the stack!
            postponedInstructions.remove(node);
        }
    }
}
class RegisterAllocator {
    FlowGraph cfg;

    Liveness liveness;

    Map colors;

    int colorsUsed;

    final static float MAX_WEIGHT = Float.MAX_VALUE;

    final static float LOOP_FACTOR = 10.0F;

    final static int MAX_DEPTH = (int) (Math.log(RegisterAllocator.MAX_WEIGHT) / Math
            .log(RegisterAllocator.LOOP_FACTOR));

    /**
     * Constructor. Builds an interference graph based on the expression nodes
     * found in liveness. Traverses the graph and determines which nodes needs
     * to be precolored and which nodes can be coalesced (move statements).
     * Nodes are coalesced and local variables are assigned to expressions.
     *
     * @see FlowGraph
     * @see LocalVariable
     */
    public RegisterAllocator(final FlowGraph cfg, final Liveness liveness) {
        this.cfg = cfg;
        this.liveness = liveness;
        colorsUsed = 0;
        colors = new HashMap();

        // Construct the interference graph.
        final Graph ig = new Graph();

        Iterator iter = liveness.defs().iterator();

        while (iter.hasNext()) {
            final VarExpr def = (VarExpr) iter.next();

            if (!(def instanceof LocalExpr)) {
                // Ignore node in the Liveness IG that are not LocalExprs
                continue;
            }

            // Create a new node in the IG, if one does not already exist
            IGNode defNode = (IGNode) ig.getNode(def);

            if (defNode == null) {
                defNode = new IGNode((LocalExpr) def);
                ig.addNode(def, defNode);
            }

            // Examine each variable that interferes with def
            final Iterator intersections = liveness.intersections(def);

            while (intersections.hasNext()) {
                final VarExpr expr = (VarExpr) intersections.next();

                if (expr == def) {
                    // If for some reason, def interferes with itself, ignore it
                    continue;
                }

                // Add an edge in RegisterAllocator's IG between the variables
                // that interfere
                if (expr instanceof LocalExpr) {
                    IGNode node = (IGNode) ig.getNode(expr);

                    if (node == null) {
                        node = new IGNode((LocalExpr) expr);
                        ig.addNode(expr, node);
                    }

                    ig.addEdge(defNode, node);
                    ig.addEdge(node, defNode);
                }
            }
        }

        // Arrays of expressions that invovle a copy of one local variable
        // to another. Expressions invovled in copies (i.e. "moves") can
        // be coalesced into one expression.
        final ArrayList copies = new ArrayList();

        // Nodes that are the targets of InitStmt are considered to be
        // precolored.
        final ArrayList precolor = new ArrayList();

        cfg.visit(new TreeVisitor() {
            public void visitBlock(final Block block) {
                // Don't visit the sink block. There's nothing interesting
                // there.
                if (block != RegisterAllocator.this.cfg.sink()) {
                    block.visitChildren(this);
                }
            }

            public void visitPhiStmt(final PhiStmt stmt) {
                stmt.visitChildren(this);

                if (!(stmt.target() instanceof LocalExpr)) {
                    return;
                }

                // A PhiStmt invovles an assignment (copy). So note the copy
                // between the target and all of the PhiStmt's operands in the
                // copies list.

                final IGNode lnode = (IGNode) ig.getNode(stmt.target());

                final HashSet set = new HashSet();

                final Iterator e = stmt.operands().iterator();

                while (e.hasNext()) {
                    final Expr op = (Expr) e.next();

                    if ((op instanceof LocalExpr) && (op.def() != null)) {
                        if (!set.contains(op.def())) {
                            set.add(op.def());

                            if (op.def() != stmt.target()) {
                                final IGNode rnode = (IGNode) ig.getNode(op
                                        .def());
                                copies.add(new IGNode[] { lnode, rnode });
                            }
                        }
                    }
                }
            }

            public void visitStoreExpr(final StoreExpr expr) {
                expr.visitChildren(this);

                if (!(expr.target() instanceof LocalExpr)) {
                    return;
                }

                final IGNode lnode = (IGNode) ig.getNode(expr.target());

                if ((expr.expr() instanceof LocalExpr)
                        && (expr.expr().def() != null)) {

                    // A store of a variable into another variable is a copy
                    final IGNode rnode = (IGNode) ig.getNode(expr.expr().def());
                    copies.add(new IGNode[] { lnode, rnode });
                    return;
                }

                // Treat L := L + k as a copy so that they get converted
                // back to iincs.
                if (expr.target().type().equals(Type.INTEGER)) {
                    if (!(expr.expr() instanceof ArithExpr)) {
                        return;
                    }

                    // We're dealing with integer arithmetic. Remember that an
                    // ArithExpr has a left and a right operand. If one of the
                    // operands is a variable and if the other is a constant and
                    // the operation is addition or substraction, we have an
                    // increment.

                    final ArithExpr rhs = (ArithExpr) expr.expr();
                    LocalExpr var = null;

                    Integer value = null;

                    if ((rhs.left() instanceof LocalExpr)
                            && (rhs.right() instanceof ConstantExpr)) {

                        var = (LocalExpr) rhs.left();

                        final ConstantExpr c = (ConstantExpr) rhs.right();

                        if (c.value() instanceof Integer) {
                            value = (Integer) c.value();
                        }

                    } else if ((rhs.right() instanceof LocalExpr)
                            && (rhs.left() instanceof ConstantExpr)) {

                        var = (LocalExpr) rhs.right();

                        final ConstantExpr c = (ConstantExpr) rhs.left();

                        if (c.value() instanceof Integer) {
                            value = (Integer) c.value();
                        }
                    }

                    if (rhs.operation() == ArithExpr.SUB) {
                        if (value != null) {
                            value = new Integer(-value.intValue());
                        }

                    } else if (rhs.operation() != ArithExpr.ADD) {
                        value = null;
                    }

                    if ((value != null) && (var.def() != null)) {
                        final int incr = value.intValue();

                        if ((short) incr == incr) {
                            // Only generate an iinc if the increment
                            // fits in a short
                            final IGNode rnode = (IGNode) ig.getNode(var.def());
                            copies.add(new IGNode[] { lnode, rnode });
                        }
                    }
                }
            }

            @Override
            public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

            }

            public void visitInitStmt(final InitStmt stmt) {
                stmt.visitChildren(this);

                // The initialized variables are precolored.
                final LocalExpr[] t = stmt.targets();

                for (int i = 0; i < t.length; i++) {
                    precolor.add(t[i]);
                }
            }
        });

        // Coalesce move related nodes, maximum weight first.
        while (copies.size() > 0) {
            // We want the copy (v <- w) with the maximum:
            // weight(v) + weight(w)
            // ---------------------
            // size(union)
            // where union is the intersection of the nodes that conflict
            // with v and the nodes that conflict with w. This equation
            // appears to be in conflict with the one given on page 38 of
            // Nate's thesis.

            HashSet union; // The union of neighboring nodes

            int max = 0;

            IGNode[] copy = (IGNode[]) copies.get(max);

            float maxWeight = copy[0].weight + copy[1].weight;
            union = new HashSet();
            union.addAll(ig.succs(copy[0]));
            union.addAll(ig.succs(copy[1]));
            maxWeight /= union.size();

            for (int i = 1; i < copies.size(); i++) {
                copy = (IGNode[]) copies.get(i);

                float weight = copy[0].weight + copy[1].weight;
                union.clear();
                union.addAll(ig.succs(copy[0]));
                union.addAll(ig.succs(copy[1]));
                weight /= union.size();

                if (weight > maxWeight) {
                    // The ith copy has the maximum weight
                    maxWeight = weight;
                    max = i;
                }
            }

            // Remove the copy with the max weight from the copies list. He
            // does it in a rather round-about way.
            copy = (IGNode[]) copies.get(max);
            copies.set(max, copies.get(copies.size() - 1));
            copies.remove(copies.size() - 1);

            if (!ig.hasEdge(copy[0], copy[1])) {
                // If the variables involved in the copy do not interfere with
                // each other, they are coalesced.

                if (CodeGenerator.DEBUG) {
                    System.out.println("coalescing " + copy[0] + " " + copy[1]);
                    System.out.println("    0 conflicts " + ig.succs(copy[0]));
                    System.out.println("    1 conflicts " + ig.succs(copy[1]));
                }

                ig.succs(copy[0]).addAll(ig.succs(copy[1]));
                ig.preds(copy[0]).addAll(ig.preds(copy[1]));

                copy[0].coalesce(copy[1]);

                if (CodeGenerator.DEBUG) {
                    System.out.println("    coalesced " + copy[0]);
                    System.out.println("    conflicts " + ig.succs(copy[0]));
                }

                // Remove coalesced node from the IG
                ig.removeNode(copy[1].key);

                iter = copies.iterator();

                // Examine all copies. If the copy involves the node that was
                // coalesced, the copy is no longer interesting. Remove it.
                while (iter.hasNext()) {
                    final IGNode[] c = (IGNode[]) iter.next();

                    if ((c[0] == copy[1]) || (c[1] == copy[1])) {
                        iter.remove();
                    }
                }
            }
        }

        // Create a list of uncolored nodes.
        final ArrayList uncoloredNodes = new ArrayList();

        Iterator nodes = ig.nodes().iterator();

        while (nodes.hasNext()) {
            final IGNode node = (IGNode) nodes.next();

            final ArrayList p = new ArrayList(precolor);
            p.retainAll(node.defs);

            // See if any node got coalesced with a precolored node.
            if (p.size() == 1) {
                // Precolored
                node.color = ((LocalExpr) p.get(0)).index();

                if (CodeGenerator.DEBUG) {
                    System.out.println("precolored " + node + " " + node.color);
                }

            } else if (p.size() == 0) {
                // Uncolored (i.e. not coalesced with any of the pre-colored
                // nodes.
                node.color = -1;
                uncoloredNodes.add(node);

            } else {
                // If two or more pre-colored nodes were coalesced, we have a
                // problem.
                throw new RuntimeException("coalesced pre-colored defs " + p);
            }
        }

        // Sort the uncolored nodes, by decreasing weight. Wide nodes
        // have half their original weight since they take up two indices
        // and we want to put color nodes with the lower indices.

        Collections.sort(uncoloredNodes, new Comparator() {
            public int compare(final Object a, final Object b) {
                final IGNode na = (IGNode) a;
                final IGNode nb = (IGNode) b;

                float wa = na.weight / ig.succs(na).size();
                float wb = nb.weight / ig.succs(nb).size();

                if (na.wide) {
                    wa /= 2;
                }

                if (nb.wide) {
                    wb /= 2;
                }

                if (wb > wa) {
                    return 1;
                }

                if (wb < wa) {
                    return -1;
                }

                return 0;
            }
        });

        nodes = uncoloredNodes.iterator();

        while (nodes.hasNext()) {
            final IGNode node = (IGNode) nodes.next();

            if (CodeGenerator.DEBUG) {
                System.out.println("coloring " + node);
                System.out.println("    conflicts " + ig.succs(node));
            }

            // Make sure node has not been colored
            // Determine which colors have been assigned to the nodes
            // conflicting with the node of interest
            final BitSet used = new BitSet();

            final Iterator succs = ig.succs(node).iterator();

            while (succs.hasNext()) {
                final IGNode succ = (IGNode) succs.next();

                if (succ.color != -1) {
                    used.set(succ.color);

                    if (succ.wide) {
                        used.set(succ.color + 1);
                    }
                }
            }

            // Find the next available color
            for (int i = 0; node.color == -1; i++) {
                if (!used.get(i)) {
                    if (node.wide) {
                        // Wide variables need two colors
                        if (!used.get(i + 1)) {
                            node.color = i;

                            if (CodeGenerator.DEBUG) {
                                System.out.println("    assigning color " + i
                                        + " to " + node);
                            }

                            if (i + 1 >= colorsUsed) {
                                colorsUsed = i + 2;
                            }
                        }

                    } else {
                        node.color = i;

                        if (CodeGenerator.DEBUG) {
                            System.out.println("    assigning color " + i
                                    + " to " + node);
                        }

                        if (i >= colorsUsed) {
                            colorsUsed = i + 1;
                        }
                    }
                }
            }
        }

        nodes = ig.nodes().iterator();

        while (nodes.hasNext()) {
            final IGNode node = (IGNode) nodes.next();

            // Make sure each node has been colored
            iter = node.defs.iterator();

            // Set the index of the variable and all of its uses to be the
            // chosen color.
            while (iter.hasNext()) {
                final LocalExpr def = (LocalExpr) iter.next();
                def.setIndex(node.color);

                final Iterator uses = def.uses().iterator();

                while (uses.hasNext()) {
                    final LocalExpr use = (LocalExpr) uses.next();
                    use.setIndex(node.color);
                }
            }
        }

        if (CodeGenerator.DEBUG) {
            System.out.println("After allocating locals--------------------");
            cfg.print(System.out);
            System.out.println("End print----------------------------------");
        }
    }

    /**
     * Returns the maximum number of local variables used by the cfg after its
     * "registers" (local variables) have been allocated.
     */
    public int maxLocals() {
        return colorsUsed;
    }

    /**
     * Creates a new local variable in this method (as modeled by the cfg).
     * Updates the number of local variables appropriately.
     */
    public LocalVariable newLocal(final Type type) {
        // Why don't we add Type information to the LocalVariable? Are we
        // assuming that type checking has already been done and so its a
        // moot point?

        final LocalVariable var = new LocalVariable(colorsUsed);
        colorsUsed += type.stackHeight();
        return var;
    }

    /**
     * IGNode is a node in the interference graph. Note that this node is
     * different from the one in Liveness. For instance, this one stores
     * information about a node's color, its weight, etc. Because nodes may be
     * coalesced, an IGNode may represent more than one LocalExpr. That's why
     * there is a list of definitions.
     */
    class IGNode extends GraphNode {
        Set defs;

        LocalExpr key;

        int color;

        boolean wide; // Is the variable wide?

        float weight;

        public IGNode(final LocalExpr def) {
            color = -1;
            key = def;
            defs = new HashSet();
            defs.add(def);
            wide = def.type().isWide();
            computeWeight();
        }

        /**
         * Coalesce two nodes in the interference graph. The weight of the other
         * node is added to that of this node. This node also inherits all of
         * the definitions of the other node.
         */
        void coalesce(final IGNode node) {
            weight += node.weight;

            final Iterator iter = node.defs.iterator();

            while (iter.hasNext()) {
                final LocalExpr def = (LocalExpr) iter.next();
                defs.add(def);
            }
        }

        public String toString() {
            return "(color=" + color + " weight=" + weight + " "
                    + defs.toString() + ")";
        }

        /**
         * Calculates the weight of a Block based on its loop depth. If the
         * block does not exceed the MAX_DEPTH, then the weight is LOOP_FACTOR
         * raised to the depth.
         */
        private float blockWeight(final Block block) {
            int depth = cfg.loopDepth(block);

            if (depth > RegisterAllocator.MAX_DEPTH) {
                return RegisterAllocator.MAX_WEIGHT;
            }

            float w = 1.0F;

            while (depth-- > 0) {
                w *= RegisterAllocator.LOOP_FACTOR;
            }

            return w;
        }

        /**
         * Computes the weight of a node in the interference graph. The weight
         * is based on where the variable represented by this node is used. The
         * method blockWeight is used to determine the weight of a variable used
         * in a block based on the loop depth of the block. Special care must be
         * taken if the variable is used in a PhiStmt.
         */
        private void computeWeight() {
            weight = 0.0F;

            final Iterator iter = defs.iterator();

            // Look at all(?) of the definitions of the IGNode
            while (iter.hasNext()) {
                final LocalExpr def = (LocalExpr) iter.next();

                weight += blockWeight(def.block());

                final Iterator uses = def.uses().iterator();

                // If the variable is used as an operand to a PhiJoinStmt,
                // find the predacessor block to the PhiJoinStmt in which the
                // variable occurs and add the weight of that block to the
                // running total weight.
                while (uses.hasNext()) {
                    final LocalExpr use = (LocalExpr) uses.next();

                    if (use.parent() instanceof PhiJoinStmt) {
                        final PhiJoinStmt phi = (PhiJoinStmt) use.parent();

                        final Iterator preds = cfg.preds(phi.block())
                                .iterator();

                        while (preds.hasNext()) {
                            final Block pred = (Block) preds.next();
                            final Expr op = phi.operandAt(pred);

                            if (use == op) {
                                weight += blockWeight(pred);
                                break;
                            }
                        }

                    } else if (use.parent() instanceof PhiCatchStmt) {
                        // If the variable is used in a PhiCatchStmt, add the
                        // weight of the block in which the variable is defined
                        // to
                        // the running total.
                        weight += blockWeight(use.def().block());

                    } else {
                        // Just add in the weight of the block in which the
                        // variable is used.
                        weight += blockWeight(use.block());
                    }
                }
            }
        }
    }
}
class Liveness {
    public static boolean DEBUG = false;

    public static boolean UNIQUE = false;

    public static final boolean BEFORE = false;

    public static final boolean AFTER = true;

    FlowGraph cfg;

    Graph ig;

    /**
     * Constructor.
     *
     * @param cfg
     *            Control flow graph on which to perform liveness analysis.
     */
    public Liveness(final FlowGraph cfg) {
        this.cfg = cfg;
        computeIntersections();
    }

    /**
     * Removes a local expression from the interference graph.
     */
    public void removeVar(final LocalExpr expr) {
        ig.removeNode(expr);
    }

    /**
     * Should not be called.
     */
    public boolean liveAtUse(final VarExpr isLive, final VarExpr at,
                             final boolean after) {
        throw new RuntimeException();
    }

    /**
     * Should not be called.
     */
    public boolean liveAtStartOfBlock(final VarExpr isLive, final Block block) {
        throw new RuntimeException();
    }

    /**
     * Should not be called.
     */
    public boolean liveAtEndOfBlock(final VarExpr isLive, final Block block) {
        throw new RuntimeException();
    }

    /**
     * Returns the <tt>LocalExpr</tt>s (variables) that occur in the CFG.
     * They correspond to nodes in the interference graph.
     */
    public Collection defs() {
        return ig.keySet();
    }

    /**
     * Returns an <tt>Iterator</tt> of <tt>LocalExpr</tt>s that interfere
     * with a given <tt>VarExpr</tt>.
     */
    public Iterator intersections(final VarExpr a) {
        final GraphNode node = ig.getNode(a);
        return new Iterator() {
            Iterator succs = ig.succs(node).iterator();

            public boolean hasNext() {
                return succs.hasNext();
            }

            public Object next() {
                final IGNode next = (IGNode) succs.next();
                return next.def;
            }

            public void remove() {
                throw new RuntimeException();
            }
        };
    }

    /**
     * Determines whether or not two variables interfere with one another.
     */
    public boolean liveRangesIntersect(final VarExpr a, final VarExpr b) {
        if (a == b) {
            return false;
        }

        // If all locals should have unique colors, return true.
        if (Liveness.UNIQUE) {
            return true;
        }

        final IGNode na = (IGNode) ig.getNode(a);
        final IGNode nb = (IGNode) ig.getNode(b);
        return ig.hasEdge(na, nb);
    }

    /**
     * Constructs the interference graph.
     */
    private void computeIntersections() {
        ig = new Graph(); // The interference graph

        if (Liveness.DEBUG) {
            System.out.println("-----------Computing live ranges-----------");
        }

        // All of the nodes (IGNodes) in the IG
        final List defNodes = new ArrayList();

        // The IGNodes whose local variable is defined by a PhiCatchStmt
        final List phiCatchNodes = new ArrayList();

        // An array of NodeInfo for each node in the CFG (indexed by the
        // node's pre-order index). Gives information about the local
        // variables (nodes in the IG) that are defined in each block.
        // The NodeInfos are stored in reverse order. That is, the
        // NodeInfo for the final variable occurrence in the block is the
        // first element in the list.
        final List[] nodes = new ArrayList[cfg.size()];

        // We need to keep track of the order of the statements in which
        // variables occur. There is an entry in nodeIndices for each
        // block in the CFG. Each entry consists of a mapping between a
        // statement in which a variable occurs and the number of the
        // statement (with respect to the other statements in which
        // variables occur) of interest. This is hard to explain in
        // words. This numbering comes into play in the liveOut method.
        final Map[] nodeIndices = new HashMap[cfg.size()];

        Iterator iter = cfg.nodes().iterator();

        // Initialize nodes and nodeIndices
        while (iter.hasNext()) {
            final Block block = (Block) iter.next();
            final int blockIndex = cfg.preOrderIndex(block);
            nodes[blockIndex] = new ArrayList();
            nodeIndices[blockIndex] = new HashMap();
        }

        // Go in trace order. Code generation for phis in the presence of
        // critical edges depends on it!

        iter = cfg.trace().iterator();

        // When performing liveness analysis, we traverse the tree from
        // the bottom up. That is, we do a REVERSE traversal.
        while (iter.hasNext()) {
            final Block block = (Block) iter.next();

            block.visit(new TreeVisitor(TreeVisitor.REVERSE) {
                public void visitPhiJoinStmt(final PhiJoinStmt stmt) {
                    if (!(stmt.target() instanceof LocalExpr)) {
                        return;
                    }

                    final LocalExpr target = (LocalExpr) stmt.target();

                    // Examine each predacessor and maintain some information
                    // about the definitions. Remember that we're dealing with
                    // a PhiJoinStmt. The predacessors of PhiJoinStmts are
                    // statements that define or use the local (SSA) variable.
                    final Iterator preds = cfg.preds(block).iterator();

                    while (preds.hasNext()) {
                        final Block pred = (Block) preds.next();
                        final int predIndex = cfg.preOrderIndex(pred);

                        final List n = nodes[predIndex];
                        final Map indices = nodeIndices[predIndex];

                        indices.put(stmt, new Integer(n.size()));
                        final NodeInfo info = new NodeInfo(stmt);
                        n.add(info);

                        // Make a new node in the interference graph for target,
                        // if one does not already exists
                        IGNode node = (IGNode) ig.getNode(target);

                        if (node == null) {
                            node = new IGNode(target);
                            ig.addNode(target, node);
                            defNodes.add(node);
                        }

                        info.defNodes.add(node);
                    }
                }

                public void visitPhiCatchStmt(final PhiCatchStmt stmt) {
                }

                public void visitStmt(final Stmt stmt) {
                }

                @Override
                public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                }
            });
        }

        iter = cfg.trace().iterator();

        while (iter.hasNext()) {
            final Block block = (Block) iter.next();
            final int blockIndex = cfg.preOrderIndex(block);

            block.visit(new TreeVisitor(TreeVisitor.REVERSE) {
                Node parent = null;

                public void visitNode(final Node node) {
                    final Node p = parent;
                    parent = node;
                    node.visitChildren(this);
                    parent = p;
                }

                @Override
                public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                }

                public void visitLocalExpr(final LocalExpr expr) {
                    NodeInfo info;

                    final List n = nodes[blockIndex];
                    final Map indices = nodeIndices[blockIndex];

                    final Integer i = (Integer) indices.get(parent);

                    if (i == null) {
                        if (Liveness.DEBUG) {
                            System.out.println("adding " + parent + " at "
                                    + n.size());
                        }

                        indices.put(parent, new Integer(n.size()));
                        info = new NodeInfo(parent);
                        n.add(info);

                    } else {
                        if (Liveness.DEBUG) {
                            System.out.println("found " + parent + " at " + i);
                        }

                        info = (NodeInfo) n.get(i.intValue());
                    }

                    if (expr.isDef()) {
                        IGNode node = (IGNode) ig.getNode(expr);

                        if (node == null) {
                            node = new IGNode(expr);
                            ig.addNode(expr, node);
                            defNodes.add(node);
                        }

                        info.defNodes.add(node);
                    }
                }

                public void visitPhiCatchStmt(final PhiCatchStmt stmt) {
                    NodeInfo info;

                    final List n = nodes[blockIndex];
                    final Map indices = nodeIndices[blockIndex];

                    final Integer i = (Integer) indices.get(stmt);

                    if (i == null) {
                        if (Liveness.DEBUG) {
                            System.out.println("adding " + stmt + " at "
                                    + n.size());
                        }

                        indices.put(stmt, new Integer(n.size()));
                        info = new NodeInfo(stmt);
                        n.add(info);

                    } else {
                        if (Liveness.DEBUG) {
                            System.out.println("found " + parent + " at " + i);
                        }

                        info = (NodeInfo) n.get(i.intValue());
                    }

                    final LocalExpr target = (LocalExpr) stmt.target();

                    IGNode node = (IGNode) ig.getNode(target);

                    if (node == null) {
                        node = new IGNode(target);
                        ig.addNode(target, node);
                        defNodes.add(node);
                        phiCatchNodes.add(node);
                    }

                    info.defNodes.add(node);
                }

                public void visitPhiJoinStmt(final PhiJoinStmt stmt) {
                }
            });
        }

        // Iterate over all of the nodes in the IG
        final int numDefs = defNodes.size();

        for (int i = 0; i < numDefs; i++) {
            final IGNode node = (IGNode) defNodes.get(i);
            final LocalExpr def = node.def;

            // Set of blocks where this variable is live out (i.e. live on
            // any of the block's outgoing edges).
            final BitSet m = new BitSet(cfg.size());

            final Iterator uses = def.uses().iterator();

            // Look at each use of the local variable
            while (uses.hasNext()) {
                final LocalExpr use = (LocalExpr) uses.next();
                Node parent = use.parent();

                if ((parent instanceof LeafExpr.MemRefExpr)
                        && ((LeafExpr.MemRefExpr) parent).isDef()) {
                    parent = parent.parent();
                }

                // Skip catch-phis. We handle this later.
                if (parent instanceof PhiCatchStmt) {
                    // If we want to be less conservative:
                    // Need to search back from the operand from all
                    // points in the protected region where it is live
                    // back to the def of the operand. For each block
                    // in protected region, if the operand def is closest
                    // dominator of the block
                    continue;
                }

                if (Liveness.DEBUG) {
                    System.out.println("searching for " + def + " from "
                            + parent);
                }

                final Block block = parent.block();

                if (parent instanceof PhiJoinStmt) {
                    final PhiJoinStmt phi = (PhiJoinStmt) parent;

                    // The local variable (LocalExpr) occurs within a
                    // PhiJoinStmt. Look at the predacessors of the
                    // PhiJoinStmt. Recall that each predacessor defines one of
                    // the operands to the PhiJoinStmt. Locate the block that
                    // defines the LocalExpr in question. Call liveOut to
                    // determine for which nodes the LocalExpr is live out.

                    // Examine the predacessors of the block containing the
                    // LocalExpr
                    final Iterator preds = cfg.preds(block).iterator();

                    while (preds.hasNext()) {
                        final Block pred = (Block) preds.next();

                        if (phi.operandAt(pred) == use) {
                            final Map indices = nodeIndices[cfg
                                    .preOrderIndex(pred)];
                            final Integer index = (Integer) indices.get(parent);
                            liveOut(m, nodes, pred, index.intValue(), node,
                                    phiCatchNodes);
                            break;
                        }
                    }

                } else {
                    // The LocalExpr is define in a non-Phi statement. Figure
                    // out which number definition define the LocalExpr in quest
                    // and call liveOut to compute the set of block in which the
                    // LocalExpr is live out.

                    final Map indices = nodeIndices[cfg.preOrderIndex(block)];
                    final Integer index = (Integer) indices.get(parent);
                    liveOut(m, nodes, block, index.intValue(), node,
                            phiCatchNodes);
                }
            }
        }

        // Go through all of the variables that are defined by
        // PhiCatchStmts and make them (the variables) conflict with
        // everything that the operands of the PhiCatchStmt conflict
        // with. See liveOut for a discussion.

        final int numPhiCatches = phiCatchNodes.size();

        for (int i = 0; i < numPhiCatches; i++) {
            final IGNode node = (IGNode) phiCatchNodes.get(i);

            final PhiCatchStmt phi = (PhiCatchStmt) node.def.parent();

            final Iterator operands = phi.operands().iterator();

            while (operands.hasNext()) {
                final LocalExpr operand = (LocalExpr) operands.next();
                final LocalExpr def = (LocalExpr) operand.def();

                if (def != null) {
                    final IGNode opNode = (IGNode) ig.getNode(def);

                    // Conflict with everything the operand conflicts with.
                    final Iterator edges = new ImmutableIterator(ig
                            .succs(opNode));

                    while (edges.hasNext()) {
                        final IGNode otherNode = (IGNode) edges.next();

                        if (otherNode != node) {
                            if (Liveness.DEBUG) {
                                System.out.println(otherNode.def
                                        + " conflicts with " + opNode.def
                                        + " and thus with " + node.def);
                            }

                            ig.addEdge(otherNode, node);
                            ig.addEdge(node, otherNode);
                        }
                    }
                }
            }
        }

        if (Liveness.DEBUG) {
            System.out.println("Interference graph =");
            System.out.println(ig);
        }
    }

    /**
     * Computes (a portion of) the "live out" set for a given local variable. If
     * a variable is live on a block's outgoing edge in the CFG, then it is
     * "live out" at that block.
     *
     * @param m
     *            Bit vector that indicates the block for which block the
     *            defNode is live out
     * @param nodes
     *            The NodeInfo for the local variables used or defined in each
     *            block
     * @param block
     *            The block in which the LocalExpr of interest is defined
     * @param nodeIndex
     *            Which number definition in the defining block
     * @param defNode
     *            The node in the IG whose live out set we are interested in
     * @param phiCatchNodes
     *            The nodes in the interference graph that represent local
     *            variables defined by PhiCatchStmts
     */
    // Nate sez:
    //
    // In a PhiJoin pred, add
    // ...
    // phi-target := phi-operand
    // jump with throw succs
    //
    // Don't kill Phi targets in protected blocks
    // The phi target and operand don't conflict
    void liveOut(final BitSet m, final List[] nodes, Block block,
                 int nodeIndex, final IGNode defNode, final Collection phiCatchNodes) {
        boolean firstNode = true;

        int blockIndex = cfg.preOrderIndex(block);

        final ArrayList stack = new ArrayList();

        Pos pos = new Pos();
        pos.block = block;
        pos.blockIndex = blockIndex;
        pos.nodeIndex = nodeIndex;

        stack.add(pos);

        while (!stack.isEmpty()) {
            pos = (Pos) stack.remove(stack.size() - 1);

            block = pos.block;
            blockIndex = pos.blockIndex;
            nodeIndex = pos.nodeIndex;

            if (Liveness.DEBUG) {
                System.out.println(defNode.def + " is live at position "
                        + nodeIndex + " of " + block);
            }

            boolean stop = false;

            // The nodes are sorted in reverse. So, the below gets all of
            // the nodes defined at this block after nodeIndex. I believe
            // this is an optimization so we don't calculate things twice.
            // Or maybe its how we get things to terminate.
            final ListIterator iter = nodes[blockIndex].listIterator(nodeIndex);

            while (!stop && iter.hasNext()) {
                final NodeInfo info = (NodeInfo) iter.next();

                if (Liveness.DEBUG) {
                    System.out
                            .println(defNode.def + " is live at " + info.node);
                }

                if (firstNode) {
                    // We don't care about the definition in the block that
                    // defines the LocalExpr of interest.
                    firstNode = false;
                    continue;
                }

                // Look at all (?) of the definitions of the LocalExpr
                final Iterator e = info.defNodes.iterator();

                while (e.hasNext()) {
                    final IGNode node = (IGNode) e.next();

                    final Iterator catchPhis = phiCatchNodes.iterator();

                    // Calculating the live region of the target of a phi-catch
                    // node is a little tricky. The target (variable) must be
                    // live throughout the protected region as well as after the
                    // PhiCatchStmt (its definition). However, we do not want
                    // the phi-catch target to conflict (interfere) with any of
                    // its operands. So, we make the target conflict with all
                    // of the variables that its operand conflict with. See
                    // page 37 of Nate's Thesis.

                    PHIS: while (catchPhis.hasNext()) {
                        final IGNode catchNode = (IGNode) catchPhis.next();

                        final PhiCatchStmt phi = (PhiCatchStmt) catchNode.def
                                .parent();

                        final Handler handler = (Handler) cfg.handlersMap()
                                .get(phi.block());
                        if (handler.protectedBlocks().contains(block)) {
                            final Iterator operands = phi.operands().iterator();

                            // If the block containing the LocalExpr in question
                            // resides inside a protected region. Make sure that
                            // the LocalExpr is not one of the operands to the
                            // PhiCatchStmt associated with the protected
                            // region.

                            while (operands.hasNext()) {
                                final LocalExpr expr = (LocalExpr) operands
                                        .next();

                                if (expr.def() == node.def) {
                                    continue PHIS;
                                }
                            }

                            if (Liveness.DEBUG) {
                                System.out.println(defNode.def
                                        + " conflicts with " + node.def);
                            }

                            // Hey, wow. The variable defined in the phi-catch
                            // interferes with the variable from the worklist.
                            ig.addEdge(node, catchNode);
                            ig.addEdge(catchNode, node);
                        }
                    }

                    if (node != defNode) {
                        if (Liveness.DEBUG) {
                            System.out.println(defNode.def + " conflicts with "
                                    + node.def);
                        }

                        // If the node in the worklist is not the node we
                        // started
                        // with, then they conflict.
                        ig.addEdge(node, defNode);
                        ig.addEdge(defNode, node);

                    } else {
                        if (Liveness.DEBUG) {
                            System.out.println("def found stopping search");
                        }

                        // We've come across a definition of the LocalExpr in
                        // question, so we don't need to do any more.
                        stop = true;
                    }
                }
            }

            if (!stop) {
                // Propagate the liveness to each of the predacessors of the
                // block in which the variable of interest is defined. This
                // is accomplished by setting the appropriate bit in m. We
                // also add another Pos to the worklist to work on the
                // predacessor block.
                final Iterator preds = cfg.preds(block).iterator();

                while (preds.hasNext()) {
                    final Block pred = (Block) preds.next();
                    final int predIndex = cfg.preOrderIndex(pred);

                    if (Liveness.DEBUG) {
                        System.out.println(defNode.def + " is live at end of "
                                + pred);
                    }

                    if (!m.get(predIndex)) {
                        pos = new Pos();
                        pos.block = pred;
                        pos.blockIndex = predIndex;

                        // Look at all of the statements in which a variable
                        // occur
                        pos.nodeIndex = 0;

                        m.set(predIndex);
                        stack.add(pos);
                    }
                }
            }
        }
    }

    /**
     * Represents a node in the interference graph. Connected nodes in the
     * interference graph interfere with each other. That is, their live regions
     */
    class IGNode extends GraphNode {
        LocalExpr def;

        /**
         * Constructor.
         *
         * @param def
         *            The local variable represented by this node.
         */
        public IGNode(final LocalExpr def) {
            this.def = def;
        }

        public String toString() {
            return def.toString();
        }
    }

    /**
     * Stores information about each Node in an expression tree (!) that defines
     * a local variable (i.e. PhiJoinStmt, PhiCatchStmt, and the parent of a
     * LocalExpr).
     */
    class NodeInfo {
        Node node; // Node in an expression tree in which a variable occurs

        List defNodes; // node(s) in IG that define above Node

        public NodeInfo(final Node node) {
            this.node = node;
            defNodes = new ArrayList();
        }
    }

    class Key {
        int blockIndex;

        Node node;

        public Key(final Node node, final int blockIndex) {
            this.blockIndex = blockIndex;
            this.node = node;
        }

        public int hashCode() {
            return node.hashCode() ^ blockIndex;
        }

        public boolean equals(final Object obj) {
            if (obj instanceof Key) {
                final Key key = (Key) obj;
                return (key.node == node) && (key.blockIndex == blockIndex);
            }

            return false;
        }
    }

    /**
     * A Pos is an element in the worklist used to determine the live out set of
     * a given LocalExpr. It consists of the block in which a local variable
     * definition occurs, the block's index (i.e. pre-order traversal number) in
     * the CFG, and the number of the definition in the block that defines the
     * LocalExpr of interest.
     */
    class Pos {
        Block block;

        int blockIndex;

        int nodeIndex;
    }
}








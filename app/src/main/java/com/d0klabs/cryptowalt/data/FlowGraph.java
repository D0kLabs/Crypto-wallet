package com.d0klabs.cryptowalt.data;

import android.widget.Switch;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
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

        FlowGraph.LoopNode loop;
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

        Instruction lastInst = null;
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
                if (!(curr instanceof Instruction)) {
                    throw new IllegalArgumentException();
                }

                Instruction currInst = (Instruction)curr;
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
            Tree tree = new Tree(catchBlock, new OperandStack());
            catchBlock.setTree(tree);
            tree.addStmt(new ExprStmt(store));
            tree.addStmt(new GotoStmt(catchBody));
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
                LocalExpr copy;
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

                                    if (last instanceof IfZeroStmt) {
                                        IfZeroStmt stmt = (IfZeroStmt)last;
                                        target = null;
                                        if (stmt.trueTarget() != stmt.falseTarget()) {
                                            if (stmt.comparison() == 0) {
                                                target = stmt.trueTarget();
                                            } else if (stmt.comparison() == 1) {
                                                target = stmt.falseTarget();
                                            }

                                            if (target != null) {
                                                left = stmt.expr();
                                                if (!((Expr)left).type().isReference()) {
                                                    if (!(left instanceof LeafExpr)) {
                                                        LocalVariable v = this.method.newLocal(((Expr)left).type());
                                                        tmp = new LocalExpr(v.index(), ((Expr)left).type());
                                                        copy = (Expr)((Expr)left).clone();
                                                        copy.setDef((DefExpr)null);
                                                        ((Expr)left).replaceWith(new StoreExpr(tmp, copy, ((Expr)left).type()));
                                                        left = tmp;
                                                    }

                                                    Object value = null;
                                                    Type type = ((Expr)left).type();
                                                    if (((Expr)left).type().isIntegral()) {
                                                        value = new Integer(0);
                                                    } else {
                                                        Assert.isTrue(((Expr)left).type().isReference());
                                                    }

                                                    if (left instanceof LocalExpr) {
                                                        copy = (LocalExpr)((Expr)left).clone();
                                                        copy.setDef((DefExpr)null);
                                                        insert = new ExprStmt(new StoreExpr(copy, new ConstantExpr(value, type), ((Expr)left).type()));
                                                        target.tree().prependStmt(insert);
                                                    } else {
                                                        Assert.isTrue(left instanceof ConstantExpr);
                                                    }
                                                }
                                            }
                                        }
                                    } else if (last instanceof SwitchStmt) {
                                        SwitchStmt stmt = (SwitchStmt)last;
                                        Expr index = stmt.index();
                                        if (!(index instanceof LeafExpr)) {
                                            LocalVariable v = this.method.newLocal(((Expr)index).type());
                                            LocalExpr tmp = new LocalExpr(v.index(), ((Expr)index).type());
                                            Expr copy = (Expr)((Expr)index).clone();
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
                                                Block target = targets[i];
                                                if (!duplicate.contains(target)) {
                                                    this.splitEdge(block, targets[i]);
                                                    Assert.isTrue(targets[i] != target);
                                                    LocalExpr copy = (LocalExpr)((Expr)index).clone();
                                                    copy.setDef((DefExpr)null);
                                                    Stmt insert = new ExprStmt(new StoreExpr(copy, new ConstantExpr(new Integer(values[i]), ((Expr)index).type()), ((Expr)index).type()));
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
                Expr copy;
                if (!(left instanceof LeafExpr)) {
                    v = this.method.newLocal(((Expr)left).type());
                    copy = new LocalExpr(v.index(), ((Expr)left).type());
                    copy = (Expr)((Expr)left).clone();
                    copy.setDef((DefExpr)null);
                    ((Expr)left).replaceWith(new StoreExpr(copy, copy, ((Expr)left).type()));
                    left = copy;
                }

                if (!(right instanceof LeafExpr)) {
                    v = this.method.newLocal(((Expr)right).type());
                    copy = new LocalExpr(v.index(), ((Expr)right).type());
                    copy = (Expr)((Expr)right).clone();
                    copy.setDef((DefExpr)null);
                    ((Expr)right).replaceWith(new StoreExpr(copy, copy, ((Expr)right).type()));
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
                    Assert.isTrue(left instanceof ConstantExpr && right instanceof ConstantExpr);
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
        });
        if (tryPreds.contains(block)) {
            for(int i = 0; i < defs.size(); ++i) {
                LocalExpr expr = (LocalExpr)defs.get(i);
                if (expr != null) {
                    Stmt last = tree.lastStmt();
                    last.visitChildren(new TreeVisitor() {
                        public void visitExpr(Expr expr) {
                            StackExpr var = tree.newStack(expr.type());
                            var.setValueNumber(expr.valueNumber());
                            Node p = expr.parent();
                            expr.setParent((Node)null);
                            p.visit(new ReplaceVisitor(expr, var));
                            var = (StackExpr)var.clone();
                            var.setDef((DefExpr)null);
                            StoreExpr store = new StoreExpr(var, expr, expr.type());
                            store.setValueNumber(expr.valueNumber());
                            Stmt storeStmt = new ExprStmt(store);
                            storeStmt.setValueNumber(expr.valueNumber());
                            tree.addStmtBeforeJump(storeStmt);
                        }

                        public void visitStackExpr(StackExpr expr) {
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

                        Block h = catchBlock.header();
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
                Block x = (Block)worklist.removeFirst();
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
                        tree.addInstruction(new Instruction(167, w.label()));
                        LeafExpr.JumpStmt newJump = (LeafExpr.JumpStmt)tree.lastStmt();
                        Iterator e = this.handlers.values().iterator();

                        while(e.hasNext()) {
                            Handler handler = (Handler)e.next();
                            if (handler.protectedBlocks().contains(w)) {
                                Assert.isTrue(this.succs(w).contains(handler.catchBlock()));
                                handler.protectedBlocks().add(newBlock);
                                this.addEdge(newBlock, handler.catchBlock());
                                newJump.catchTargets().add(handler.catchBlock());
                            }
                        }

                        ImmutableIterator preds = new ImmutableIterator(this.preds(w));

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
            FlowGraph.LoopNode loop = (FlowGraph.LoopNode)loops.get(i);
            if (this.loopTree.preds(loop).size() > 0 && loop.header.blockType() != 1) {
                headers.add(loop.header);
                peel.add(new Integer(i));
                loop = null;
                Iterator e = this.loopTree.preds(loop).iterator();
                loop = (FlowGraph.LoopNode)e.next();
                int outerIndex = loops.indexOf(loop);
                Assert.isTrue(outerIndex != -1);
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
            Collection loop = (Collection)loops.get(loopIndex);
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
            Block block;
            Block copy;
            Block copy;
            if (canPeel) {
                Set exits = new HashSet();
                exits.addAll(hoistable);
                exits.retainAll(loop);
                e = loop.iterator();

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
                        outerLoop.addAll(loop);
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
                        Assert.isTrue(copySet.contains(jsr.follow()));
                        Assert.isTrue(copySet.contains(jsr.sub().entry()));
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
                outerLoop.addAll(loop);
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
                                        Assert.isFalse(this.hasEdge(src, w));
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
        Assert.isFalse(src == this.srcBlock || dst == this.snkBlock, "Can't split an edge from the source or to the sink");
        if (this.handlers.containsKey(dst)) {
            if (DEBUG) {
                System.out.println("not removing exception edge " + src + " -> " + dst);
            }

        } else {
            Block newBlock = this.newBlock();
            this.trace.add(this.trace.indexOf(dst), newBlock);
            Tree tree = new Tree(newBlock, src.tree().stack());
            newBlock.setTree(tree);
            tree.addInstruction(new Instruction(167, dst.label()));
            if (DEBUG) {
                System.out.println("add edge " + src + " -> " + newBlock);
                System.out.println("add edge " + newBlock + " -> " + dst);
                System.out.println("remove edge " + src + " -> " + dst);
            }

            src.visit(new ReplaceTarget(dst, newBlock));
            this.addEdge(src, newBlock);
            this.addEdge(newBlock, dst);
            this.removeEdge(src, dst);
            Assert.isTrue(this.hasEdge(src, newBlock));
            Assert.isTrue(this.hasEdge(newBlock, dst));
            Assert.isFalse(this.hasEdge(src, dst));
            JumpStmt newJump = (JumpStmt)newBlock.tree().lastStmt();
            Iterator e = this.handlers.values().iterator();

            while(e.hasNext()) {
                Handler handler = (Handler)e.next();
                if (handler.protectedBlocks().contains(dst)) {
                    Assert.isTrue(this.succs(dst).contains(handler.catchBlock()));
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
            tree.addInstruction(new Instruction(167, block.label()));
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
            tree.addInstruction(new Instruction(167, block.label()));
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
                    Assert.isTrue(this.succs(block).contains(handler.catchBlock()));
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
            tree.addInstruction(new Instruction(167, this.method.firstBlock()));
            if (catchBodies != null) {
                this.addHandlerEdges(this.iniBlock, catchBodies, labelPos, (Subroutine)null, new HashSet());
            }
        }

    }

    private void addHandlerEdges(Block block, Map catchBodies, Map labelPos, Subroutine sub, Set visited) {
        if (!visited.contains(block)) {
            visited.add(block);
            Tree tree = block.tree();
            Assert.isTrue(tree != null);
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
                    Assert.isTrue(catchBody != null);
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
                if (!(ce instanceof Instruction)) {
                    if (ce instanceof Label) {
                        Label label = (Label)ce;
                        if (label.startsBlock()) {
                            tree.addInstruction(new Instruction(167, label));
                            target = (Block)this.getNode(label);
                            Assert.isTrue(target != null, "Block for " + label + " not found");
                            this.addEdge(block, target);
                            this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                            break;
                        }

                        tree.addLabel(label);
                    }
                } else {
                    Instruction inst = (Instruction)ce;
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
                            Assert.isTrue(next != null, inst + " not followed by a block");
                            tree.addInstruction(inst, next);
                            label = (Label)inst.operand();
                            target = (Block)this.getNode(label);
                            Assert.isTrue(target != null, inst + " target not found");
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
                            Assert.isTrue(target != null, inst + " target not found");
                            this.addEdge(block, target);
                            this.buildTreeForBlock(target, tree.stack(), sub, labelPos, catchBodies);
                            break;
                        }

                        if (inst.isConditionalJump()) {
                            Assert.isTrue(next != null, inst + " not followed by a block");
                            tree.addInstruction(inst, next);
                            label = (Label)inst.operand();
                            target = (Block)this.getNode(label);
                            Assert.isTrue(target != null, inst + " target not found");
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
        Assert.isTrue(this.trace.size() == this.size() - 2, "trace contains " + this.trace.size() + " " + this.trace + " blocks, not " + (this.size() - 2) + " " + this.nodes());
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
            Assert.isTrue(handler != null);
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
    }
}
public class Subroutine {
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

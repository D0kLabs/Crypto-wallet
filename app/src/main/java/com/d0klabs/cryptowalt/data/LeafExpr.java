package com.d0klabs.cryptowalt.data;


import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

public interface LeafExpr {
    public class Tree extends Node implements InstructionVisitor, Opcode {
        public static boolean DEBUG = false;
        public static boolean FLATTEN = false;
        public static boolean USE_STACK = true;
        public static boolean AUPDATE_FIX_HACK = false;
        public static boolean AUPDATE_FIX_HACK_CHANGED = false;
        public static boolean USE_PERSISTENT = false;
        Block block;
        Subroutine sub;
        Block next;
        OperandStack stack;
        Tree.StmtList stmts;
        Stack savedStack;
        static int stackpos = 0;
        boolean saveValue;
        private int nextIndex = 0;
        Instruction last = null;

        private void db(String s) {
            if (DEBUG) {
                System.out.println(s);
            }

        }

        public Tree(Block block, OperandStack predStack) {
            this.block = block;
            if (DEBUG) {
                System.out.println("    new tree for " + block);
            }

            this.stack = new OperandStack();
            this.stmts = new Tree.StmtList();
            this.appendStmt(new LabelStmt(block.label()));

            for(int i = 0; i < predStack.size(); ++i) {
                Expr expr = predStack.get(i);
                Expr copy = (Expr)expr.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
            }

        }

        public void cleanupOnly() {
        }

        public void initLocals(Collection locals) {
            LocalExpr[] t = new LocalExpr[locals.size()];
            if (t.length != 0) {
                Iterator iter = locals.iterator();

                for(int i = 0; iter.hasNext(); ++i) {
                    t[i] = (LocalExpr)iter.next();
                }

                this.addStmt(new InitStmt(t));
            }
        }

        public void removeStmt(Stmt stmt) {
            this.stmts.remove(stmt);
        }

        public void removeLastStmt() {
            ListIterator iter = this.stmts.listIterator(this.stmts.size());

            while(iter.hasPrevious()) {
                Stmt s = (Stmt)iter.previous();
                if (!(s instanceof LabelStmt)) {
                    iter.remove();
                    return;
                }
            }

        }

        public List stmts() {
            return this.stmts;
        }

        public Stmt lastStmt() {
            ListIterator iter = this.stmts.listIterator(this.stmts.size());

            while(iter.hasPrevious()) {
                Stmt s = (Stmt)iter.previous();
                if (!(s instanceof LabelStmt)) {
                    return s;
                }
            }

            return null;
        }

        public OperandStack stack() {
            return this.stack;
        }

        public void addStmtAfter(Stmt stmt, Stmt after) {
            if (DEBUG) {
                System.out.println("insert: " + stmt + " after " + after);
            }

            ListIterator iter = this.stmts.listIterator();

            while(iter.hasNext()) {
                Stmt s = (Stmt)iter.next();
                if (s == after) {
                    iter.add(stmt);
                    stmt.setParent(this);
                    return;
                }
            }

            throw new RuntimeException(after + " not found");
        }

        public void addStmtBefore(Stmt stmt, Stmt before) {
            if (DEBUG) {
                System.out.println("insert: " + stmt + " before " + before);
            }

            ListIterator iter = this.stmts.listIterator();

            while(iter.hasNext()) {
                Stmt s = (Stmt)iter.next();
                if (s == before) {
                    iter.previous();
                    iter.add(stmt);
                    stmt.setParent(this);
                    return;
                }
            }

            throw new RuntimeException(before + " not found");
        }

        public void prependStmt(Stmt stmt) {
            if (DEBUG) {
                System.out.println("prepend: " + stmt + " in " + this.block);
            }

            ListIterator iter = this.stmts.listIterator();

            while(iter.hasNext()) {
                Stmt s = (Stmt)iter.next();
                if (!(s instanceof LabelStmt)) {
                    iter.previous();
                    iter.add(stmt);
                    stmt.setParent(this);
                    return;
                }
            }

            this.appendStmt(stmt);
        }

        private void saveStack() {
            int height = 0;

            for(int i = 0; i < this.stack.size(); ++i) {
                Expr expr = this.stack.get(i);
                ExprStmt store;
                if (USE_STACK) {
                    if (!(expr instanceof StackExpr) || ((StackExpr)expr).index() != height) {
                        StackExpr target = new StackExpr(height, expr.type());
                        store = new ExprStmt(new StoreExpr(target, expr, expr.type()));
                        this.appendStmt(store);
                        StackExpr copy = (StackExpr)target.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.set(i, copy);
                    }
                } else if (!(expr instanceof LocalExpr) || !((LocalExpr)expr).fromStack() || ((LocalExpr)expr).index() != height) {
                    LocalExpr target = this.newStackLocal(this.nextIndex++, expr.type());
                    store = new ExprStmt(new StoreExpr(target, expr, expr.type()));
                    this.appendStmt(store);
                    LocalExpr copy = (LocalExpr)target.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.set(i, copy);
                }

                height += expr.type().stackHeight();
            }

        }

        private void appendStmt(Stmt stmt) {
            if (DEBUG) {
                System.out.println("      append: " + stmt);
            }

            stmt.setParent(this);
            this.stmts.add(stmt);
        }

        public void addStmt(Stmt stmt) {
            this.saveStack();
            this.appendStmt(stmt);
        }

        public void addStmtBeforeJump(Stmt stmt) {
            Stmt last = this.lastStmt();
            this.addStmtBefore(stmt, last);
        }

        private void throwClassFormatException(String msg) {
            MethodEditor method = this.block.graph().method();
            throw new ClassFormatException("Method " + method.declaringClass().type().className() + "." + method.name() + " " + method.type() + ": " + msg);
        }

        public void addInstruction(Instruction inst, Block next) {
            this.next = next;
            this.addInst(inst);
        }

        public void addInstruction(Instruction inst) {
            this.next = null;
            this.addInst(inst);
        }

        public void addInstruction(Instruction inst, Subroutine sub) {
            this.sub = sub;
            this.next = null;
            this.addInst(inst);
        }

        public void addLabel(Label label) {
            if (this.last != null) {
                switch(this.last.opcodeClass()) {
                    case 89:
                    case 90:
                    case 91:
                    case 92:
                    case 93:
                    case 94:
                        break;
                    default:
                        this.addInst(this.last, false);
                        this.last = null;
                }
            }

            this.addStmt(new LabelStmt(label));
        }

        private void addInst(Instruction inst, boolean saveValue) {
            if (DEBUG) {
                for(int i = 0; i < this.stack.size(); ++i) {
                    Expr exp = this.stack.peek(i);
                    System.out.println((i > 0 ? "-" + i : " " + i) + ": " + exp);
                }
            }

            if (DEBUG) {
                System.out.println("    add " + inst + " save=" + saveValue);
            }

            try {
                this.saveValue = saveValue;
                if (FLATTEN) {
                    this.saveStack();
                }

                inst.visit(this);
            } catch (EmptyStackException var5) {
                this.throwClassFormatException("Empty operand stack at " + inst);
            }
        }

        private void addInst(Instruction inst) {
            if (this.last == null) {
                this.last = inst;
            } else {
                label57:
                switch(this.last.opcodeClass()) {
                    case 89:
                        switch(inst.opcodeClass()) {
                            case 54:
                            case 56:
                            case 58:
                            case 179:
                                this.addInst(inst, true);
                                this.last = null;
                            default:
                                break label57;
                        }
                    case 90:
                        switch(inst.opcodeClass()) {
                            case 181:
                            case 204:
                                this.addInst(inst, true);
                                this.last = null;
                            default:
                                break label57;
                        }
                    case 91:
                        switch(inst.opcodeClass()) {
                            case 79:
                            case 81:
                            case 83:
                            case 84:
                            case 85:
                            case 86:
                                this.addInst(inst, true);
                                this.last = null;
                            case 80:
                            case 82:
                            default:
                                break label57;
                        }
                    case 92:
                        switch(inst.opcodeClass()) {
                            case 55:
                            case 57:
                            case 179:
                                this.addInst(inst, true);
                                this.last = null;
                            default:
                                break label57;
                        }
                    case 93:
                        switch(inst.opcodeClass()) {
                            case 181:
                            case 204:
                                this.addInst(inst, true);
                                this.last = null;
                            default:
                                break label57;
                        }
                    case 94:
                        switch(inst.opcodeClass()) {
                            case 80:
                            case 82:
                                this.addInst(inst, true);
                                this.last = null;
                            case 81:
                        }
                }

                if (this.last != null) {
                    this.addInst(this.last, false);
                    this.last = inst;
                }
            }

            if (inst.isJump() || inst.isSwitch() || inst.isThrow() || inst.isReturn() || inst.isJsr() || inst.isRet()) {
                this.addInst(inst, false);
                this.last = null;
            }

        }

        public StackExpr newStack(Type type) {
            return new StackExpr(stackpos++, type);
        }

        public LocalExpr newStackLocal(int index, Type type) {
            if (index >= this.nextIndex) {
                this.nextIndex = index + 1;
            }

            return new LocalExpr(index, true, type);
        }

        public LocalExpr newLocal(int index, Type type) {
            return new LocalExpr(index, false, type);
        }

        public LocalExpr newLocal(Type type) {
            LocalVariable var = this.block.graph().method().newLocal(type);
            return new LocalExpr(var.index(), type);
        }

        public String toString() {
            String x = "(TREE " + this.block + " stack=";

            for(int i = 0; i < this.stack.size(); ++i) {
                Expr expr = this.stack.get(i);
                x = x + expr.type().shortName();
            }

            return x + ")";
        }

        public void visit_nop(Instruction inst) {
        }

        public void visit_ldc(Instruction inst) {
            Object value = inst.operand();
            Type type;
            if (value == null) {
                type = Type.NULL;
            } else if (value instanceof Integer) {
                type = Type.INTEGER;
            } else if (value instanceof Long) {
                type = Type.LONG;
            } else if (value instanceof Float) {
                type = Type.FLOAT;
            } else if (value instanceof Double) {
                type = Type.DOUBLE;
            } else if (value instanceof String) {
                type = Type.STRING;
            } else {
                if (!(value instanceof Type)) {
                    this.throwClassFormatException("Illegal constant type: " + value.getClass().getName() + ": " + value);
                    return;
                }

                type = Type.CLASS;
            }

            Expr top = new ConstantExpr(value, type);
            this.stack.push(top);
        }

        public void visit_iload(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr top = new Expr(operand.index(), Type.INTEGER) {
                @Override
                public void visitForceChildren(TreeVisitor var1) {

                }

                @Override
                public void visit(TreeVisitor var1) {

                }

                @Override
                public int exprHashCode() {
                    return 0;
                }

                @Override
                public boolean equalsExpr(Expr var1) {
                    return false;
                }

                @Override
                public Object clone() {
                    return null;
                }
            };
            this.stack.push(top);
        }

        public void visit_lload(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr top = new Expr(operand.index(), Type.LONG) {
                @Override
                public void visitForceChildren(TreeVisitor var1) {

                }

                @Override
                public void visit(TreeVisitor var1) {

                }

                @Override
                public int exprHashCode() {
                    return 0;
                }

                @Override
                public boolean equalsExpr(Expr var1) {
                    return false;
                }

                @Override
                public Object clone() {
                    return null;
                }
            };
            this.stack.push(top);
        }

        public void visit_fload(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr top = new Expr(operand.index(), Type.FLOAT) {
                @Override
                public void visitForceChildren(TreeVisitor var1) {

                }

                @Override
                public void visit(TreeVisitor var1) {

                }

                @Override
                public int exprHashCode() {
                    return 0;
                }

                @Override
                public boolean equalsExpr(Expr var1) {
                    return false;
                }

                @Override
                public Object clone() {
                    return null;
                }
            };
            this.stack.push(top);
        }

        public void visit_dload(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr top = new Expr(operand.index(), Type.DOUBLE) {
                @Override
                public void visitForceChildren(TreeVisitor var1) {

                }

                @Override
                public void visit(TreeVisitor var1) {

                }

                @Override
                public int exprHashCode() {
                    return 0;
                }

                @Override
                public boolean equalsExpr(Expr var1) {
                    return false;
                }

                @Override
                public Object clone() {
                    return null;
                }
            };
            this.stack.push(top);
        }

        public void visit_aload(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr top = new Expr(operand.index(), Type.OBJECT) {
                @Override
                public void visitForceChildren(TreeVisitor var1) {

                }

                @Override
                public void visit(TreeVisitor var1) {

                }

                @Override
                public int exprHashCode() {
                    return 0;
                }

                @Override
                public boolean equalsExpr(Expr var1) {
                    return false;
                }

                @Override
                public Object clone() {
                    return null;
                }
            };
            this.stack.push(top);
            this.db("      aload: " + top);
        }

        public void visit_iaload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.INTEGER.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.INTEGER, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_laload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.LONG.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.LONG, Type.LONG);
            this.stack.push(top);
        }

        public void visit_faload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.FLOAT.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.FLOAT, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_daload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.DOUBLE.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.DOUBLE, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_aaload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.OBJECT.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.OBJECT, Type.OBJECT);
            this.stack.push(top);
        }

        public void visit_baload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.BYTE.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.BYTE, Type.BYTE);
            this.stack.push(top);
        }

        public void visit_caload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.CHARACTER.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.CHARACTER, Type.CHARACTER);
            this.stack.push(top);
        }

        public void visit_saload(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.SHORT.arrayType());
            Expr top = new ArrayRefExpr(array, index, Type.SHORT, Type.SHORT);
            this.stack.push(top);
        }

        private void addStore(MemExpr target, Expr expr) {
            if (this.saveValue) {
                this.stack.push(new StoreExpr(target, expr, expr.type()));
            } else {
                this.addStmt(new ExprStmt(new StoreExpr(target, expr, expr.type())));
            }

        }

        public void visit_istore(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr expr = this.stack.pop(Type.INTEGER);
            LocalExpr target = new LocalExpr(operand.index(), expr.type());
            this.addStore(target, expr);
        }

        public void visit_lstore(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr expr = this.stack.pop(Type.LONG);
            LocalExpr target = new LocalExpr(operand.index(), expr.type());
            this.addStore(target, expr);
        }

        public void visit_fstore(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr expr = this.stack.pop(Type.FLOAT);
            LocalExpr target = new LocalExpr(operand.index(), expr.type());
            this.addStore(target, expr);
        }

        public void visit_dstore(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr expr = this.stack.pop(Type.DOUBLE);
            LocalExpr target = new LocalExpr(operand.index(), expr.type());
            this.addStore(target, expr);
        }

        public void visit_astore(Instruction inst) {
            LocalVariable operand = (LocalVariable)inst.operand();
            Expr expr = this.stack.peek();
            if (expr.type().isAddress()) {
                expr = this.stack.pop(Type.ADDRESS);
                this.sub.setReturnAddress(operand);
                this.addStmt(new AddressStoreStmt(this.sub));
            } else {
                expr = this.stack.pop(Type.OBJECT);
                LocalExpr target = new LocalExpr(operand.index(), expr.type());
                this.addStore(target, expr);
            }

        }

        public void visit_iastore(Instruction inst) {
            Expr value = this.stack.pop(Type.INTEGER);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.INTEGER.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.INTEGER, Type.INTEGER);
            this.addStore(target, value);
        }

        public void visit_lastore(Instruction inst) {
            Expr value = this.stack.pop(Type.LONG);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.LONG.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.LONG, Type.LONG);
            this.addStore(target, value);
        }

        public void visit_fastore(Instruction inst) {
            Expr value = this.stack.pop(Type.FLOAT);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.FLOAT.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.FLOAT, Type.FLOAT);
            this.addStore(target, value);
        }

        public void visit_dastore(Instruction inst) {
            Expr value = this.stack.pop(Type.DOUBLE);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.DOUBLE.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.DOUBLE, Type.DOUBLE);
            this.addStore(target, value);
        }

        public void visit_aastore(Instruction inst) {
            Expr value = this.stack.pop(Type.OBJECT);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.OBJECT.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.OBJECT, Type.OBJECT);
            this.addStore(target, value);
        }

        public void visit_bastore(Instruction inst) {
            Expr value = this.stack.pop(Type.BYTE);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.BYTE.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.BYTE, Type.BYTE);
            this.addStore(target, value);
        }

        public void visit_castore(Instruction inst) {
            Expr value = this.stack.pop(Type.CHARACTER);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.CHARACTER.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.CHARACTER, Type.CHARACTER);
            this.addStore(target, value);
        }

        public void visit_sastore(Instruction inst) {
            Expr value = this.stack.pop(Type.SHORT);
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.SHORT.arrayType());
            ArrayRefExpr target = new ArrayRefExpr(array, index, Type.SHORT, Type.SHORT);
            this.addStore(target, value);
        }

        public void visit_pop(Instruction inst) {
            Expr expr = this.stack.pop1();
            this.addStmt(new ExprStmt(expr));
        }

        public void visit_pop2(Instruction inst) {
            Expr[] expr = this.stack.pop2();
            if (expr.length == 1) {
                this.addStmt(new ExprStmt(expr[0]));
            } else {
                this.addStmt(new ExprStmt(expr[0]));
                this.addStmt(new ExprStmt(expr[1]));
            }

        }

        public void visit_dup(Instruction inst) {
            this.db("      dup");
            if (USE_STACK) {
                this.saveStack();
                StackExpr s0 = (StackExpr)this.stack.pop1();
                StackExpr[] s = new StackExpr[]{s0};
                this.manip(s, new int[2], 1);
            } else {
                Expr s0 = this.stack.pop1();
                LocalExpr t0 = this.newStackLocal(this.stack.height(), s0.type());
                this.db("        s0: " + s0);
                this.db("        t0: " + t0);
                if (!t0.equalsExpr(s0)) {
                    this.db("          t0 <- s0");
                    this.addStore(t0, s0);
                }

                Expr copy = (Expr)t0.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
                copy = (Expr)t0.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
            }

        }

        public void visit_dup_x1(Instruction inst) {
            if (USE_STACK) {
                this.saveStack();
                StackExpr s1 = (StackExpr)this.stack.pop1();
                StackExpr s0 = (StackExpr)this.stack.pop1();
                StackExpr[] s = new StackExpr[]{s0, s1};
                this.manip(s, new int[]{1, 0, 1}, 2);
            } else {
                Expr s1 = this.stack.pop1();
                Expr s0 = this.stack.pop1();
                LocalExpr t0 = this.newStackLocal(this.stack.height(), s0.type());
                LocalExpr t1 = this.newStackLocal(this.stack.height() + 1, s1.type());
                if (!t0.equalsExpr(s0)) {
                    this.addStore(t0, s0);
                }

                if (!t1.equalsExpr(s1)) {
                    this.addStore(t1, s1);
                }

                Expr copy = (Expr)t1.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
                copy = (Expr)t0.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
                copy = (Expr)t1.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
            }

        }

        public void visit_dup_x2(Instruction inst) {
            this.db("      dup_x2");
            Expr[] s01;
            if (USE_STACK) {
                this.saveStack();
                StackExpr s2 = (StackExpr)this.stack.pop1();
                s01 = this.stack.pop2();
                StackExpr[] s;
                if (s01.length == 2) {
                    s = new StackExpr[]{(StackExpr)s01[0], (StackExpr)s01[1], s2};
                    this.manip(s, new int[]{2, 0, 1, 2}, 3);
                } else {
                    s = new StackExpr[]{(StackExpr)s01[0], s2};
                    this.manip(s, new int[]{1, 0, 1}, 3);
                }
            } else {
                Expr s2 = this.stack.pop1();
                s01 = this.stack.pop2();
                this.db("        s2: " + s2);
                this.db("        s01: " + s01[0] + (s01.length > 1 ? " " + s01[1] : ""));
                LocalExpr t1;
                LocalExpr t0;
                if (s01.length == 2) {
                    t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                    t1 = this.newStackLocal(this.stack.height() + 1, s01[1].type());
                    LocalExpr t2 = this.newStackLocal(this.stack.height() + 2, s2.type());
                    this.db("        t0: " + t0);
                    this.db("        t1: " + t1);
                    this.db("        t2: " + t2);
                    if (!t0.equalsExpr(s01[0])) {
                        this.db("          t0 <- s01[0]");
                        this.addStore(t0, s01[0]);
                    }

                    if (!t1.equalsExpr(s01[1])) {
                        this.db("          t1 <- s01[1]");
                        this.addStore(t1, s01[1]);
                    }

                    if (!t2.equalsExpr(s2)) {
                        this.db("          t2 <- s2");
                        this.addStore(t2, s2);
                    }

                    Expr copy = (Expr)t2.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t2.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                } else {
                    t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                    t1 = this.newStackLocal(this.stack.height() + 2, s2.type());
                    if (!t0.equalsExpr(s01[0])) {
                        this.addStore(t0, s01[0]);
                    }

                    if (!t1.equalsExpr(s2)) {
                        this.addStore(t1, s2);
                    }

                    Expr copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                }
            }

        }

        public void visit_dup2(Instruction inst) {
            Expr[] s01;
            if (USE_STACK) {
                this.saveStack();
                s01 = this.stack.pop2();
                StackExpr[] s;
                if (s01.length == 1) {
                    s = new StackExpr[]{(StackExpr)s01[0]};
                    this.manip(s, new int[2], 4);
                } else {
                    s = new StackExpr[]{(StackExpr)s01[0], (StackExpr)s01[1]};
                    this.manip(s, new int[]{0, 1, 0, 1}, 4);
                }
            } else {
                s01 = this.stack.pop2();
                LocalExpr t0;
                if (s01.length == 1) {
                    t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                    if (!t0.equalsExpr(s01[0])) {
                        this.addStore(t0, s01[0]);
                    }

                    Expr copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                } else {
                    t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                    LocalExpr t1 = this.newStackLocal(this.stack.height() + 1, s01[1].type());
                    if (!t0.equalsExpr(s01[0])) {
                        this.addStore(t0, s01[0]);
                    }

                    if (!t1.equalsExpr(s01[1])) {
                        this.addStore(t1, s01[1]);
                    }

                    Expr copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                }
            }

        }

        public void visit_dup2_x1(Instruction inst) {
            Expr[] s12;
            StackExpr s0;
            if (USE_STACK) {
                this.saveStack();
                s12 = this.stack.pop2();
                s0 = (StackExpr)this.stack.pop1();
                StackExpr[] s;
                if (s12.length == 2) {
                    s = new StackExpr[]{s0, (StackExpr)s12[0], (StackExpr)s12[1]};
                    this.manip(s, new int[]{1, 2, 0, 1, 2}, 5);
                } else {
                    s = new StackExpr[]{s0, (StackExpr)s12[0]};
                    this.manip(s, new int[]{1, 0, 1}, 5);
                }
            } else {
                s12 = this.stack.pop2();
                s0 = (StackExpr)this.stack.pop1();
                LocalExpr t1;
                LocalExpr t0;
                if (s12.length == 2) {
                    t0 = this.newStackLocal(this.stack.height(), s0.type());
                    t1 = this.newStackLocal(this.stack.height() + 1, s12[0].type());
                    LocalExpr t2 = this.newStackLocal(this.stack.height() + 2, s12[1].type());
                    if (!t0.equalsExpr(s0)) {
                        this.addStore(t0, s0);
                    }

                    if (!t1.equalsExpr(s12[0])) {
                        this.addStore(t1, s12[0]);
                    }

                    if (!t2.equalsExpr(s12[1])) {
                        this.addStore(t2, s12[1]);
                    }

                    Expr copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t2.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t2.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                } else {
                    t0 = this.newStackLocal(this.stack.height(), s0.type());
                    t1 = this.newStackLocal(this.stack.height() + 1, s12[0].type());
                    if (!t0.equalsExpr(s0)) {
                        this.addStore(t0, s0);
                    }

                    if (!t1.equalsExpr(s12[0])) {
                        this.addStore(t1, s12[0]);
                    }

                    Expr copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t1.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                }
            }

        }

        public void visit_dup2_x2(Instruction inst) {
            Expr[] s23;
            Expr[] s01;
            if (USE_STACK) {
                this.saveStack();
                s23 = this.stack.pop2();
                s01 = this.stack.pop2();
                StackExpr[] s;
                if (s01.length == 2 && s23.length == 2) {
                    s = new StackExpr[]{(StackExpr)s01[0], (StackExpr)s01[1], (StackExpr)s23[0], (StackExpr)s23[1]};
                    this.manip(s, new int[]{2, 3, 0, 1, 2, 3}, 6);
                } else if (s01.length == 2 && s23.length == 1) {
                    s = new StackExpr[]{(StackExpr)s01[0], (StackExpr)s01[1], (StackExpr)s23[0]};
                    this.manip(s, new int[]{2, 0, 1, 2}, 6);
                } else if (s01.length == 1 && s23.length == 2) {
                    s = new StackExpr[]{(StackExpr)s01[0], (StackExpr)s23[0], (StackExpr)s23[1]};
                    this.manip(s, new int[]{1, 2, 0, 1, 2}, 6);
                } else if (s01.length == 1 && s23.length == 2) {
                    s = new StackExpr[]{(StackExpr)s01[0], (StackExpr)s23[0]};
                    this.manip(s, new int[]{1, 0, 1}, 6);
                }
            } else {
                s23 = this.stack.pop2();
                s01 = this.stack.pop2();
                LocalExpr t2;
                LocalExpr t0;
                LocalExpr t3;
                if (s01.length == 2 && s23.length == 2) {
                    t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                    t2 = this.newStackLocal(this.stack.height() + 1, s01[1].type());
                    t3 = this.newStackLocal(this.stack.height() + 2, s23[0].type());
                    t3 = this.newStackLocal(this.stack.height() + 3, s23[1].type());
                    if (!t0.equalsExpr(s01[0])) {
                        this.addStore(t0, s01[0]);
                    }

                    if (!t2.equalsExpr(s01[1])) {
                        this.addStore(t2, s01[1]);
                    }

                    if (!t3.equalsExpr(s23[0])) {
                        this.addStore(t3, s23[0]);
                    }

                    if (!t3.equalsExpr(s23[1])) {
                        this.addStore(t3, s23[1]);
                    }

                    Expr copy = (Expr)t3.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t3.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t0.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t2.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t3.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                    copy = (Expr)t3.clone();
                    copy.setDef((DefExpr)null);
                    this.stack.push(copy);
                } else {
                    Expr copy;
                    if (s01.length == 2 && s23.length == 1) {
                        t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                        t2 = this.newStackLocal(this.stack.height() + 1, s01[1].type());
                        t3 = this.newStackLocal(this.stack.height() + 2, s23[0].type());
                        if (!t0.equalsExpr(s01[0])) {
                            this.addStore(t0, s01[0]);
                        }

                        if (!t2.equalsExpr(s01[1])) {
                            this.addStore(t2, s01[1]);
                        }

                        if (!t3.equalsExpr(s23[0])) {
                            this.addStore(t3, s23[0]);
                        }

                        copy = (Expr)t3.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t0.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t2.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t3.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                    } else if (s01.length == 1 && s23.length == 2) {
                        t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                        t2 = this.newStackLocal(this.stack.height() + 2, s23[0].type());
                        t3 = this.newStackLocal(this.stack.height() + 3, s23[1].type());
                        if (!t0.equalsExpr(s01[0])) {
                            this.addStore(t0, s01[0]);
                        }

                        if (!t2.equalsExpr(s23[0])) {
                            this.addStore(t2, s23[0]);
                        }

                        if (!t3.equalsExpr(s23[1])) {
                            this.addStore(t3, s23[1]);
                        }

                        copy = (Expr)t2.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t3.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t0.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t2.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t3.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                    } else if (s01.length == 1 && s23.length == 2) {
                        t0 = this.newStackLocal(this.stack.height(), s01[0].type());
                        t2 = this.newStackLocal(this.stack.height() + 2, s23[0].type());
                        if (!t0.equalsExpr(s01[0])) {
                            this.addStore(t0, s01[0]);
                        }

                        if (!t2.equalsExpr(s23[0])) {
                            this.addStore(t2, s23[0]);
                        }

                        copy = (Expr)t2.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t0.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                        copy = (Expr)t2.clone();
                        copy.setDef((DefExpr)null);
                        this.stack.push(copy);
                    }
                }
            }

        }

        public void visit_swap(Instruction inst) {
            if (USE_STACK) {
                this.saveStack();
                StackExpr s1 = (StackExpr)this.stack.pop1();
                StackExpr s0 = (StackExpr)this.stack.pop1();
                StackExpr[] s = new StackExpr[]{s0, s1};
                this.manip(s, new int[]{1, 0}, 0);
            } else {
                Expr s1 = this.stack.pop1();
                Expr s0 = this.stack.pop1();
                LocalExpr t0 = this.newStackLocal(this.stack.height(), s0.type());
                LocalExpr t1 = this.newStackLocal(this.stack.height() + 1, s1.type());
                if (!t0.equalsExpr(s0)) {
                    this.addStore(t0, s0);
                }

                if (!t1.equalsExpr(s1)) {
                    this.addStore(t1, s1);
                }

                Expr copy = (Expr)t1.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
                copy = (Expr)t0.clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
            }

        }

        private void manip(StackExpr[] source, int[] s, int kind) {
            int height = 0;

            for(int i = 0; i < this.stack.size(); ++i) {
                Expr expr = this.stack.get(i);
                height += expr.type().stackHeight();
            }

            StackExpr[] target = new StackExpr[s.length];

            for(int i = 0; i < s.length; ++i) {
                target[i] = new StackExpr(height, source[s[i]].type());
                StackExpr copy = (StackExpr)target[i].clone();
                copy.setDef((DefExpr)null);
                this.stack.push(copy);
                height += target[i].type().stackHeight();
            }

            this.appendStmt(new StackManipStmt(target, source, kind));
        }

        public void visit_iadd(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ArithExpr('+', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_ladd(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('+', left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_fadd(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('+', left, right, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_dadd(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('+', left, right, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_isub(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ArithExpr('-', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lsub(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('-', left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_fsub(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('-', left, right, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_dsub(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('-', left, right, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_imul(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ArithExpr('*', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lmul(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('*', left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_fmul(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('*', left, right, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_dmul(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('*', left, right, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_idiv(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr check = new ZeroCheckExpr(right, Type.INTEGER);
            Expr top = new ArithExpr('/', left, check, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_ldiv(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr check = new ZeroCheckExpr(right, Type.LONG);
            Expr top = new ArithExpr('/', left, check, Type.LONG);
            this.stack.push(top);
        }

        public void visit_fdiv(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('/', left, right, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_ddiv(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('/', left, right, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_irem(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr check = new ZeroCheckExpr(right, Type.INTEGER);
            Expr top = new ArithExpr('%', left, check, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lrem(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr check = new ZeroCheckExpr(right, Type.LONG);
            Expr top = new ArithExpr('%', left, check, Type.LONG);
            this.stack.push(top);
        }

        public void visit_frem(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('%', left, right, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_drem(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('%', left, right, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_ineg(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new NegExpr(expr, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lneg(Instruction inst) {
            Expr expr = this.stack.pop(Type.LONG);
            Expr top = new NegExpr(expr, Type.LONG);
            this.stack.push(top);
        }

        public void visit_fneg(Instruction inst) {
            Expr expr = this.stack.pop(Type.FLOAT);
            Expr top = new NegExpr(expr, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_dneg(Instruction inst) {
            Expr expr = this.stack.pop(Type.DOUBLE);
            Expr top = new NegExpr(expr, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_ishl(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ShiftExpr(0, left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lshl(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ShiftExpr(0, left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_ishr(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ShiftExpr(1, left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lshr(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ShiftExpr(1, left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_iushr(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ShiftExpr(2, left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lushr(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ShiftExpr(2, left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_iand(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ArithExpr('&', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_land(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('&', left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_ior(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ArithExpr('|', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lor(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('|', left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_ixor(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Expr top = new ArithExpr('^', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lxor(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('^', left, right, Type.LONG);
            this.stack.push(top);
        }

        public void visit_iinc(Instruction inst) {
            IncOperand operand = (IncOperand)inst.operand();
            int incr = operand.incr();
            ConstantExpr right;
            LocalExpr left;
            ArithExpr top;
            LocalExpr copy;
            if (incr < 0) {
                right = new ConstantExpr(new Integer(-incr), Type.INTEGER);
                left = new LocalExpr(operand.var().index(), Type.INTEGER);
                top = new ArithExpr('-', left, right, Type.INTEGER);
                copy = (LocalExpr)left.clone();
                copy.setDef((DefExpr)null);
                this.addStmt(new ExprStmt(new StoreExpr(copy, top, left.type())));
            } else if (incr > 0) {
                right = new ConstantExpr(new Integer(incr), Type.INTEGER);
                left = new LocalExpr(operand.var().index(), Type.INTEGER);
                top = new ArithExpr('+', left, right, Type.INTEGER);
                copy = (LocalExpr)left.clone();
                copy.setDef((DefExpr)null);
                this.addStmt(new ExprStmt(new StoreExpr(copy, top, left.type())));
            }

        }

        public void visit_i2l(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new CastExpr(expr, Type.LONG, Type.LONG);
            this.stack.push(top);
        }

        public void visit_i2f(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new CastExpr(expr, Type.FLOAT, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_i2d(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new CastExpr(expr, Type.DOUBLE, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_l2i(Instruction inst) {
            Expr expr = this.stack.pop(Type.LONG);
            Expr top = new CastExpr(expr, Type.INTEGER, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_l2f(Instruction inst) {
            Expr expr = this.stack.pop(Type.LONG);
            Expr top = new CastExpr(expr, Type.FLOAT, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_l2d(Instruction inst) {
            Expr expr = this.stack.pop(Type.LONG);
            Expr top = new CastExpr(expr, Type.DOUBLE, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_f2i(Instruction inst) {
            Expr expr = this.stack.pop(Type.FLOAT);
            Expr top = new CastExpr(expr, Type.INTEGER, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_f2l(Instruction inst) {
            Expr expr = this.stack.pop(Type.FLOAT);
            Expr top = new CastExpr(expr, Type.LONG, Type.LONG);
            this.stack.push(top);
        }

        public void visit_f2d(Instruction inst) {
            Expr expr = this.stack.pop(Type.FLOAT);
            Expr top = new CastExpr(expr, Type.DOUBLE, Type.DOUBLE);
            this.stack.push(top);
        }

        public void visit_d2i(Instruction inst) {
            Expr expr = this.stack.pop(Type.DOUBLE);
            Expr top = new CastExpr(expr, Type.INTEGER, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_d2l(Instruction inst) {
            Expr expr = this.stack.pop(Type.DOUBLE);
            Expr top = new CastExpr(expr, Type.LONG, Type.LONG);
            this.stack.push(top);
        }

        public void visit_d2f(Instruction inst) {
            Expr expr = this.stack.pop(Type.DOUBLE);
            Expr top = new CastExpr(expr, Type.FLOAT, Type.FLOAT);
            this.stack.push(top);
        }

        public void visit_i2b(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new CastExpr(expr, Type.BYTE, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_i2c(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new CastExpr(expr, Type.CHARACTER, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_i2s(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            Expr top = new CastExpr(expr, Type.SHORT, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_lcmp(Instruction inst) {
            Expr right = this.stack.pop(Type.LONG);
            Expr left = this.stack.pop(Type.LONG);
            Expr top = new ArithExpr('?', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_fcmpl(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('<', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_fcmpg(Instruction inst) {
            Expr right = this.stack.pop(Type.FLOAT);
            Expr left = this.stack.pop(Type.FLOAT);
            Expr top = new ArithExpr('>', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_dcmpl(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('<', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_dcmpg(Instruction inst) {
            Expr right = this.stack.pop(Type.DOUBLE);
            Expr left = this.stack.pop(Type.DOUBLE);
            Expr top = new ArithExpr('>', left, right, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_ifeq(Instruction inst) {
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(0, left, t, this.next));
        }

        public void visit_ifne(Instruction inst) {
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(1, left, t, this.next));
        }

        public void visit_iflt(Instruction inst) {
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(4, left, t, this.next));
        }

        public void visit_ifge(Instruction inst) {
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(3, left, t, this.next));
        }

        public void visit_ifgt(Instruction inst) {
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(2, left, t, this.next));
        }

        public void visit_ifle(Instruction inst) {
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(5, left, t, this.next));
        }

        public void visit_if_icmpeq(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(0, left, right, t, this.next));
        }

        public void visit_if_icmpne(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(1, left, right, t, this.next));
        }

        public void visit_if_icmplt(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(4, left, right, t, this.next));
        }

        public void visit_if_icmpge(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(3, left, right, t, this.next));
        }

        public void visit_if_icmpgt(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(2, left, right, t, this.next));
        }

        public void visit_if_icmple(Instruction inst) {
            Expr right = this.stack.pop(Type.INTEGER);
            Expr left = this.stack.pop(Type.INTEGER);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(5, left, right, t, this.next));
        }

        public void visit_if_acmpeq(Instruction inst) {
            Expr right = this.stack.pop(Type.OBJECT);
            Expr left = this.stack.pop(Type.OBJECT);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(0, left, right, t, this.next));
        }

        public void visit_if_acmpne(Instruction inst) {
            Expr right = this.stack.pop(Type.OBJECT);
            Expr left = this.stack.pop(Type.OBJECT);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfCmpStmt(1, left, right, t, this.next));
        }

        public void visit_goto(Instruction inst) {
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new GotoStmt(t));
        }

        public void visit_jsr(Instruction inst) {
            Subroutine sub = this.block.graph().labelSub((Label)inst.operand());
            this.addStmt(new JsrStmt(sub, this.next));
            this.stack.push(new ReturnAddressExpr(Type.ADDRESS));
        }

        public void visit_ret(Instruction inst) {
            this.addStmt(new RetStmt(this.sub));
        }

        public void visit_switch(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Switch sw = (Switch)inst.operand();
            Block defaultTarget = (Block)this.block.graph().getNode(sw.defaultTarget());
            Block[] targets = new Block[sw.targets().length];

            for(int i = 0; i < targets.length; ++i) {
                targets[i] = (Block)this.block.graph().getNode(sw.targets()[i]);
            }

            this.addStmt(new SwitchStmt(index, defaultTarget, targets, sw.values()));
        }

        public void visit_ireturn(Instruction inst) {
            Expr expr = this.stack.pop(Type.INTEGER);
            this.addStmt(new ReturnExprStmt(expr));
        }

        public void visit_lreturn(Instruction inst) {
            Expr expr = this.stack.pop(Type.LONG);
            this.addStmt(new ReturnExprStmt(expr));
        }

        public void visit_freturn(Instruction inst) {
            Expr expr = this.stack.pop(Type.FLOAT);
            this.addStmt(new ReturnExprStmt(expr));
        }

        public void visit_dreturn(Instruction inst) {
            Expr expr = this.stack.pop(Type.DOUBLE);
            this.addStmt(new ReturnExprStmt(expr));
        }

        public void visit_areturn(Instruction inst) {
            Expr expr = this.stack.pop(Type.OBJECT);
            this.addStmt(new ReturnExprStmt(expr));
        }

        public void visit_return(Instruction inst) {
            this.addStmt(new ReturnStmt());
        }

        public void visit_getstatic(Instruction inst) {
            MemberRef field = (MemberRef)inst.operand();
            Type type = field.nameAndType().type();

            try {
                EditorContext context = this.block.graph().method().declaringClass().context();
                EditorContext.FieldEditor e = context.editField(field);
                if (e.isFinal() && e.constantValue() != null) {
                    Expr top = new ConstantExpr(e.constantValue(), type);
                    this.stack.push(top);
                    context.release(e.fieldInfo());
                    return;
                }

                context.release(e.fieldInfo());
            } catch (NoSuchFieldException var7) {
            }

            Expr top = new StaticFieldExpr(field, type);
            this.stack.push(top);
        }

        public void visit_putstatic(Instruction inst) {
            MemberRef field = (MemberRef)inst.operand();
            Type type = field.nameAndType().type();
            Expr value = this.stack.pop(type);
            StaticFieldExpr target = new StaticFieldExpr(field, type);
            this.addStore(target, value);
        }

        public void visit_putstatic_nowb(Instruction inst) {
            this.visit_putstatic(inst);
        }

        public void visit_getfield(Instruction inst) {
            MemberRef field = (MemberRef)inst.operand();
            Type type = field.nameAndType().type();
            Expr obj = this.stack.pop(Type.OBJECT);
            Expr check = new ZeroCheckExpr(obj, obj.type());
            Expr top = new FieldExpr(check, field, type);
            this.stack.push(top);
        }

        public void visit_putfield(Instruction inst) {
            MemberRef field = (MemberRef)inst.operand();
            Type type = field.nameAndType().type();
            Expr value = this.stack.pop(type);
            Expr obj = this.stack.pop(Type.OBJECT);
            Expr ucCheck = obj;
            if (USE_PERSISTENT) {
                ucCheck = new UCExpr(obj, 1, obj.type());
            }

            Expr check = new ZeroCheckExpr((Expr)ucCheck, obj.type());
            FieldExpr target = new FieldExpr(check, field, type);
            this.addStore(target, value);
        }

        public void visit_putfield_nowb(Instruction inst) {
            MemberRef field = (MemberRef)inst.operand();
            Type type = field.nameAndType().type();
            Expr value = this.stack.pop(type);
            Expr obj = this.stack.pop(Type.OBJECT);
            Expr check = new ZeroCheckExpr(obj, obj.type());
            FieldExpr target = new FieldExpr(check, field, type);
            this.addStore(target, value);
        }

        public void visit_invokevirtual(Instruction inst) {
            this.addCall(inst, 0);
        }

        public void visit_invokespecial(Instruction inst) {
            this.addCall(inst, 1);
        }

        public void visit_invokestatic(Instruction inst) {
            this.addCall(inst, 0);
        }

        public void visit_invokeinterface(Instruction inst) {
            this.addCall(inst, 2);
        }

        private void addCall(Instruction inst, int kind) {
            MemberRef method = (MemberRef)inst.operand();
            Type type = method.nameAndType().type();
            Type[] paramTypes = type.paramTypes();
            Expr[] params = new Expr[paramTypes.length];

            for(int i = paramTypes.length - 1; i >= 0; --i) {
                params[i] = this.stack.pop(paramTypes[i]);
            }

            Object top;
            if (inst.opcodeClass() != 184) {
                Expr obj = this.stack.pop(Type.OBJECT);
                top = new CallMethodExpr(kind, obj, params, method, type.returnType());
            } else {
                top = new CallStaticExpr(params, method, type.returnType());
            }

            if (type.returnType().equals(Type.VOID)) {
                this.addStmt(new ExprStmt((Expr)top));
            } else {
                this.stack.push((Expr)top);
            }

        }

        public void visit_new(Instruction inst) {
            Type type = (Type)inst.operand();
            Expr top = new NewExpr(type, Type.OBJECT);
            this.stack.push(top);
            this.db("      new: " + top);
        }

        public void visit_newarray(Instruction inst) {
            Type type = (Type)inst.operand();
            Expr size = this.stack.pop(Type.INTEGER);
            Expr top = new NewArrayExpr(size, type, type.arrayType());
            this.stack.push(top);
        }

        public void visit_arraylength(Instruction inst) {
            Expr array = this.stack.pop(Type.OBJECT);
            Expr top = new ArrayLengthExpr(array, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_athrow(Instruction inst) {
            Expr expr = this.stack.pop(Type.THROWABLE);
            this.addStmt(new ThrowStmt(expr));
        }

        public void visit_checkcast(Instruction inst) {
            Expr expr = this.stack.pop(Type.OBJECT);
            Type type = (Type)inst.operand();
            Expr top = new CastExpr(expr, type, type);
            this.stack.push(top);
        }

        public void visit_instanceof(Instruction inst) {
            Type type = (Type)inst.operand();
            Expr expr = this.stack.pop(Type.OBJECT);
            Expr top = new InstanceOfExpr(expr, type, Type.INTEGER);
            this.stack.push(top);
        }

        public void visit_monitorenter(Instruction inst) {
            Expr obj = this.stack.pop(Type.OBJECT);
            this.addStmt(new MonitorStmt(0, obj));
        }

        public void visit_monitorexit(Instruction inst) {
            Expr obj = this.stack.pop(Type.OBJECT);
            this.addStmt(new MonitorStmt(1, obj));
        }

        public void visit_multianewarray(Instruction inst) {
            MultiArrayOperand operand = (MultiArrayOperand)inst.operand();
            Expr[] dim = new Expr[operand.dimensions()];

            for(int i = dim.length - 1; i >= 0; --i) {
                dim[i] = this.stack.pop(Type.INTEGER);
            }

            Type type = operand.type();
            Expr top = new NewMultiArrayExpr(dim, type.elementType(dim.length), type);
            this.stack.push(top);
        }

        public void visit_ifnull(Instruction inst) {
            Expr left = this.stack.pop(Type.OBJECT);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(0, left, t, this.next));
        }

        public void visit_ifnonnull(Instruction inst) {
            Expr left = this.stack.pop(Type.OBJECT);
            Block t = (Block)this.block.graph().getNode(inst.operand());
            this.addStmt(new IfZeroStmt(1, left, t, this.next));
        }

        public void visit_rc(Instruction inst) {
            Integer depth = (Integer)inst.operand();
            Expr object = this.stack.peek(depth);
            this.stack.replace(depth, new RCExpr(object, object.type()));
        }

        public void visit_aupdate(Instruction inst) {
            Integer depth = (Integer)inst.operand();
            Expr object;
            if (AUPDATE_FIX_HACK && depth == 1) {
                object = this.stack.peek();
                if (object.type().isWide()) {
                    depth = new Integer(2);
                    inst.setOperand(depth);
                    AUPDATE_FIX_HACK_CHANGED = true;
                }
            }

            object = this.stack.peek(depth);
            this.stack.replace(depth, new UCExpr(object, 1, object.type()));
        }

        public void visit_supdate(Instruction inst) {
            Integer depth = (Integer)inst.operand();
            Expr object = this.stack.peek(depth);
            this.stack.replace(depth, new UCExpr(object, 2, object.type()));
        }

        public void visit_aswizzle(Instruction inst) {
            Expr index = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.OBJECT.arrayType());
            this.addStmt(new SCStmt(array, index));
        }

        public void visit_aswrange(Instruction inst) {
            Expr end = this.stack.pop(Type.INTEGER);
            Expr start = this.stack.pop(Type.INTEGER);
            Expr array = this.stack.pop(Type.OBJECT.arrayType());
            this.addStmt(new SRStmt(array, start, end));
        }

        public void visitForceChildren(TreeVisitor visitor) {
            LinkedList list = new LinkedList(this.stmts);
            ListIterator iter;
            Stmt s;
            if (visitor.reverse()) {
                iter = list.listIterator(this.stmts.size());

                while(iter.hasPrevious()) {
                    s = (Stmt)iter.previous();
                    s.visit(visitor);
                }
            } else {
                iter = list.listIterator();

                while(iter.hasNext()) {
                    s = (Stmt)iter.next();
                    s.visit(visitor);
                }
            }

        }

        public void visit(TreeVisitor visitor) {
            visitor.visitTree(this);
        }

        public Node parent() {
            return null;
        }

        public Block block() {
            return this.block;
        }

        class StmtList extends LinkedList {
            StmtList() {
            }

            public void clear() {
                Iterator iter = this.iterator();

                while(iter.hasNext()) {
                    ((Stmt)iter.next()).cleanup();
                }

                super.clear();
            }

            public boolean remove(Object o) {
                if (super.remove(o)) {
                    ((Stmt)o).cleanup();
                    return true;
                } else {
                    return false;
                }
            }

            public boolean removeAll(Collection c) {
                boolean changed = false;
                if (c == this) {
                    changed = this.size() > 0;
                    this.clear();
                } else {
                    for(Iterator iter = c.iterator(); iter.hasNext(); changed = this.remove(iter.next()) || changed) {
                    }
                }

                return changed;
            }

            public boolean retainAll(Collection c) {
                boolean changed = false;
                if (c == this) {
                    return false;
                } else {
                    Iterator iter = this.iterator();

                    while(iter.hasNext()) {
                        if (!c.contains(iter.next())) {
                            changed = true;
                            iter.remove();
                        }
                    }

                    return changed;
                }
            }

            public Object set(int index, Object element) {
                if (index < this.size()) {
                    Stmt s = (Stmt)this.get(index);
                    if (s != element) {
                        s.cleanup();
                    }
                }

                return super.set(index, element);
            }

            public Object remove(int index) {
                Object o = super.remove(index);
                if (o != null) {
                    ((Stmt)o).cleanup();
                }

                return o;
            }

            public ListIterator listIterator() {
                return this.listIterator(0);
            }

            public ListIterator listIterator(int index) {
                final ListIterator iter = super.listIterator(index);
                return new ListIterator() {
                    Object last = null;

                    public boolean hasNext() {
                        return iter.hasNext();
                    }

                    public Object next() {
                        this.last = iter.next();
                        return this.last;
                    }

                    public boolean hasPrevious() {
                        return iter.hasPrevious();
                    }

                    public Object previous() {
                        this.last = iter.previous();
                        return this.last;
                    }

                    public int nextIndex() {
                        return iter.nextIndex();
                    }

                    public int previousIndex() {
                        return iter.previousIndex();
                    }

                    public void add(Object obj) {
                        ((Stmt)obj).setParent(Tree.this);
                        this.last = null;
                        iter.add(obj);
                    }

                    public void set(Object obj) {
                        if (this.last == null) {
                            throw new NoSuchElementException();
                        } else {
                            ((Stmt)obj).setParent(Tree.this);
                            ((Stmt)this.last).cleanup();
                            this.last = null;
                            iter.set(obj);
                        }
                    }

                    public void remove() {
                        if (this.last == null) {
                            throw new NoSuchElementException();
                        } else {
                            ((Stmt)this.last).cleanup();
                            this.last = null;
                            iter.remove();
                        }
                    }
                };
            }

            public Iterator iterator() {
                final Iterator iter = super.iterator();
                return new Iterator() {
                    Object last = null;

                    public boolean hasNext() {
                        return iter.hasNext();
                    }

                    public Object next() {
                        this.last = iter.next();
                        return this.last;
                    }

                    public void remove() {
                        if (this.last == null) {
                            throw new NoSuchElementException();
                        } else {
                            ((Stmt)this.last).cleanup();
                            this.last = null;
                            iter.remove();
                        }
                    }
                };
            }
        }
    }
    public abstract class TreeVisitor {
        public static final int FORWARD = 0;
        public static final int REVERSE = 1;
        boolean prune;
        int direction;

        public TreeVisitor() {
            this(0);
        }

        public TreeVisitor(int direction) {
            this.direction = direction;
        }

        public void setPrune(boolean prune) {
            this.prune = prune;
        }

        public boolean prune() {
            return this.prune;
        }

        public int direction() {
            return this.direction;
        }

        public boolean forward() {
            return this.direction == 0;
        }

        public boolean reverse() {
            return this.direction == 1;
        }

        public void visitFlowGraph(FlowGraph graph) {
            graph.visitChildren(this);
        }

        public void visitBlock(Block block) {
            block.visitChildren(this);
        }

        public void visitTree(Tree tree) {
            this.visitNode(tree);
        }

        public void visitExprStmt(ExprStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitIfStmt(IfStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitIfCmpStmt(IfCmpStmt stmt) {
            this.visitIfStmt(stmt);
        }

        public void visitIfZeroStmt(IfZeroStmt stmt) {
            this.visitIfStmt(stmt);
        }

        public void visitInitStmt(InitStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitGotoStmt(GotoStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitLabelStmt(LabelStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitMonitorStmt(MonitorStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitPhiStmt(PhiStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitCatchExpr(CatchExpr expr) {
            this.visitExpr(expr);
        }

        public void visitDefExpr(DefExpr expr) {
            this.visitExpr(expr);
        }

        public void visitStackManipStmt(StackManipStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitPhiCatchStmt(PhiCatchStmt stmt) {
            this.visitPhiStmt(stmt);
        }

        public void visitPhiJoinStmt(PhiJoinStmt stmt) {
            this.visitPhiStmt(stmt);
        }

        public void visitRetStmt(RetStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitReturnExprStmt(ReturnExprStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitReturnStmt(ReturnStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitAddressStoreStmt(AddressStoreStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitStoreExpr(StoreExpr expr) {
            this.visitExpr(expr);
        }

        public void visitJsrStmt(JsrStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitSwitchStmt(SwitchStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitThrowStmt(ThrowStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitStmt(Stmt stmt) {
            this.visitNode(stmt);
        }

        public void visitSCStmt(SCStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitSRStmt(SRStmt stmt) {
            this.visitStmt(stmt);
        }

        public void visitArithExpr(ArithExpr expr) {
            this.visitExpr(expr);
        }

        public void visitArrayLengthExpr(ArrayLengthExpr expr) {
            this.visitExpr(expr);
        }

        public void visitMemExpr(MemExpr expr) {
            this.visitDefExpr(expr);
        }

        public void visitMemRefExpr(MemRefExpr expr) {
            this.visitMemExpr(expr);
        }

        public void visitArrayRefExpr(ArrayRefExpr expr) {
            this.visitMemRefExpr(expr);
        }

        public void visitCallExpr(CallExpr expr) {
            this.visitExpr(expr);
        }

        public void visitCallMethodExpr(CallMethodExpr expr) {
            this.visitCallExpr(expr);
        }

        public void visitCallStaticExpr(CallStaticExpr expr) {
            this.visitCallExpr(expr);
        }

        public void visitCastExpr(CastExpr expr) {
            this.visitExpr(expr);
        }

        public void visitConstantExpr(ConstantExpr expr) {
            this.visitExpr(expr);
        }

        public void visitFieldExpr(FieldExpr expr) {
            this.visitMemRefExpr(expr);
        }

        public void visitInstanceOfExpr(InstanceOfExpr expr) {
            this.visitExpr(expr);
        }

        public void visitLocalExpr(LocalExpr expr) {
            this.visitVarExpr(expr);
        }

        public void visitNegExpr(NegExpr expr) {
            this.visitExpr(expr);
        }

        public void visitNewArrayExpr(NewArrayExpr expr) {
            this.visitExpr(expr);
        }

        public void visitNewExpr(NewExpr expr) {
            this.visitExpr(expr);
        }

        public void visitNewMultiArrayExpr(NewMultiArrayExpr expr) {
            this.visitExpr(expr);
        }

        public void visitCheckExpr(CheckExpr expr) {
            this.visitExpr(expr);
        }

        public void visitZeroCheckExpr(ZeroCheckExpr expr) {
            this.visitCheckExpr(expr);
        }

        public void visitRCExpr(RCExpr expr) {
            this.visitCheckExpr(expr);
        }

        public void visitUCExpr(UCExpr expr) {
            this.visitCheckExpr(expr);
        }

        public void visitReturnAddressExpr(ReturnAddressExpr expr) {
            this.visitExpr(expr);
        }

        public void visitShiftExpr(ShiftExpr expr) {
            this.visitExpr(expr);
        }

        public void visitStackExpr(StackExpr expr) {
            this.visitVarExpr(expr);
        }

        public void visitVarExpr(VarExpr expr) {
            this.visitMemExpr(expr);
        }

        public void visitStaticFieldExpr(StaticFieldExpr expr) {
            this.visitMemRefExpr(expr);
        }

        public void visitExpr(Expr expr) {
            this.visitNode(expr);
        }

        public void visitNode(Node node) {
            node.visitChildren(this);
        }

        public void visitStackExpr(com.d0klabs.cryptowalt.data.StackExpr stackExpr) {this.visitVarExpr(stackExpr); }

        public abstract void visitTree(com.d0klabs.cryptowalt.data.Tree tree);
    }
    public abstract class Expr extends Node implements Cloneable {
        protected Type type;
        private DefExpr def = null;
        private Object comparator = new Expr.ExprComparator();

        public Expr(int index, Type type) {
            this.type = type;
        }

        public boolean setType(Type type) {
            if (!this.type.equals(type)) {
                this.type = type;
                return true;
            } else {
                return false;
            }
        }

        public boolean isDef() {
            return false;
        }

        public Stmt stmt() {
            Node p;
            for(p = this.parent; !(p instanceof Stmt); p = p.parent) {
                }

            return (Stmt)p;
        }

        public Type type() {
            return this.type;
        }

        public void cleanupOnly() {
            this.setDef((DefExpr)null);
        }

        public void setDef(DefExpr def) {
            if (this.def != def) {
                if (this.def != null) {
                    this.def.removeUse(this);
                }

                if (!this.isDef()) {
                    this.def = def;
                    if (this.def != null) {
                        this.def.addUse(this);
                    }

                } else {
                    this.def = null;
                }
            }
        }

        public DefExpr def() {
            return this.def;
        }

        public abstract int exprHashCode();

        public abstract boolean equalsExpr(Expr var1);

        public abstract Object clone();

        protected Expr copyInto(Expr expr) {
            expr = (Expr)super.copyInto(expr);
            DefExpr def = this.def();
            if (this.isDef()) {
                expr.setDef((DefExpr)null);
            } else {
                expr.setDef(def);
            }

            return expr;
        }

        public Object comparator() {
            return this.comparator;
        }

        private class ExprComparator {
            Expr expr;

            private ExprComparator() {
                this.expr = Expr.this;
            }

            public boolean equals(Object obj) {
                if (obj instanceof Expr.ExprComparator) {
                    Expr other = ((Expr.ExprComparator)obj).expr;
                    return this.expr.equalsExpr(other) && this.expr.type.simple().equals(other.type.simple());
                } else {
                    return false;
                }
            }

            public int hashCode() {
                return Expr.this.exprHashCode();
            }
        }
    }
    public class OperandStack {
        ArrayList stack = new ArrayList();
        int height = 0;

        public OperandStack() {
        }

        public boolean isEmpty() {
            return this.stack.isEmpty();
        }

        public Expr pop(Type type) {
            Expr top = (Expr)this.stack.remove(this.stack.size() - 1);
            Type topType = top.type();
            this.height -= topType.stackHeight();
            if (type.isAddress()) {
                if (!topType.isAddress()) {
                    throw new IllegalArgumentException("Expected " + type + ", stack = " + this.toString());
                }
            } else if (type.isReference()) {
                if (!topType.isReference()) {
                    throw new IllegalArgumentException("Expected " + type + ", stack = " + this.toString());
                }
            } else if (type.isIntegral()) {
                if (!topType.isIntegral()) {
                    throw new IllegalArgumentException("Expected " + type + ", stack = " + this.toString());
                }
            } else if (!type.equals(topType)) {
                throw new IllegalArgumentException("Expected " + type + ", stack = " + this.toString());
            }

            return top;
        }

        public Expr peek() {
            return (Expr)this.stack.get(this.stack.size() - 1);
        }

        public void set(int index, Expr expr) {
            this.stack.set(index, expr);
        }

        public int height() {
            return this.height;
        }

        public void replace(int depth, Expr expr) {
            for(int i = this.stack.size() - 1; i >= 0; --i) {
                Expr top = (Expr)this.stack.get(i);
                if (depth == 0) {
                    this.stack.set(i, expr);
                    return;
                }

                depth -= top.type().stackHeight();
            }

            throw new IllegalArgumentException("Can't replace below stack bottom.");
        }

        public Expr peek(int depth) {
            for(int i = this.stack.size() - 1; i >= 0; --i) {
                Expr top = (Expr)this.stack.get(i);
                if (depth == 0) {
                    return top;
                }

                depth -= top.type().stackHeight();
            }

            throw new IllegalArgumentException("Can't peek below stack bottom.");
        }

        public Expr pop1() {
            Expr top = (Expr)this.stack.remove(this.stack.size() - 1);
            Type type = top.type();
            if (type.isWide()) {
                throw new IllegalArgumentException("Expected a word , got a long");
            } else {
                --this.height;
                return top;
            }
        }

        public Expr[] pop2() {
            Expr top = (Expr)this.stack.remove(this.stack.size() - 1);
            Type type = top.type();
            Expr[] a;
            if (type.isWide()) {
                a = new Expr[]{top};
            } else {
                a = new Expr[]{(Expr)this.stack.remove(this.stack.size() - 1), top};
            }

            this.height -= 2;
            return a;
        }

        public void push(Expr expr) {
            this.height += expr.type().stackHeight();
            this.stack.add(expr);
        }

        public int size() {
            return this.stack.size();
        }

        public Expr get(int index) {
            Expr expr = (Expr)this.stack.get(index);
            return expr;
        }

        public String toString() {
            return this.stack.toString();
        }
    }
    public abstract class DefExpr extends Expr {
        Set uses = new HashSet();
        int version;
        static int next = 0;

        public DefExpr(final Type type) {
            super(next, type);
            uses = new HashSet();
            version = DefExpr.next++;
        }

        public void cleanupOnly() {
            super.cleanupOnly();
            List a = new ArrayList(this.uses);
            this.uses.clear();
            Iterator e = a.iterator();

            while(e.hasNext()) {
                Expr use = (Expr)e.next();
                use.setDef((DefExpr)null);
            }

        }

        public int version() {
            return this.version;
        }

        public boolean isDef() {
                final DefExpr[] defs = new DefExpr[this.parent.valueNumber];
            if (defs != null) {
                    for(int i = 0; i < defs.length; ++i) {
                        if (defs[i].key == this.parent.key) {
                            return true;
                        }
                    }
                }
            return false;
        }

        public Collection uses() {
            return new HashSet(this.uses);
        }

        public boolean hasUse(Expr use) {
            return this.uses.contains(use);
        }

        protected void addUse(Expr use) {
            this.uses.add(use);
        }

        protected void removeUse(Expr use) {
            this.uses.remove(use);
        }
    }
    public abstract class Stmt extends Node {
        public Stmt() {
        }

        public void cleanupOnly() {
        }

        public abstract Object clone();
    }
    public abstract class Node {
        protected Node parent = null;
        int key = 0;
        int valueNumber = -1;

        public Node() {
        }

        public int valueNumber() {
            return this.valueNumber;
        }

        public void setValueNumber(int valueNumber) {
            this.valueNumber = valueNumber;
        }

        public int key() {
            return this.key;
        }

        public void setKey(int key) {
            this.key = key;
        }

        public abstract void visitForceChildren(TreeVisitor var1);

        public abstract void visit(TreeVisitor var1);

        public void visitChildren(TreeVisitor visitor) {
            if (!visitor.prune()) {
                this.visitForceChildren(visitor);
            }

        }

        public void visitOnly(TreeVisitor visitor) {
            visitor.setPrune(true);
            this.visit(visitor);
            visitor.setPrune(false);
        }

        public Block block() {
            for(Node p = this; p != null; p = p.parent) {
                if (p instanceof Tree) {
                    return ((Tree)p).block();
                }
            }

            throw new RuntimeException(this + " is not in a block");
        }

        public void setParent(Node parent) {
            this.parent = parent;
        }

        public boolean hasParent() {
            return this.parent != null;
        }

        public Node parent() {
            return this.parent;
        }

        protected Node copyInto(Node node) {
            node.setValueNumber(this.valueNumber);
            return node;
        }

        public abstract void cleanupOnly();

        public void cleanup() {
            this.visit(new TreeVisitor() {
                public void visitNode(Node node) {
                    node.setParent((Node)null);
                    node.cleanupOnly();
                    node.visitChildren(this);
                }

                @Override
                public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {

                }
            });
        }

        public void replaceWith(Node node) {
            this.replaceWith(node, true);
        }

        public void replaceWith(Node node, boolean cleanup) {
            Node oldParent = this.parent;
            if (this instanceof Stmt) {
               }

            if (this instanceof Expr) {
                Expr expr1 = (Expr)this;
                Expr expr2 = (Expr)node;
                }

            this.parent.visit(new ReplaceVisitor(this, node));
            if (cleanup) {
                this.cleanup();
            }

        }

        public String toString() {
            StringWriter w = new StringWriter();
            this.visit(new PrintVisitor(w) {
                protected void println(Object s) {
                    this.print(s);
                }

                protected void println() {
                }
            });
            w.flush();
            return w.toString();
        }
    }
    public class StaticFieldExpr extends MemRefExpr {
        MemberRef field;

        public StaticFieldExpr(MemberRef field, Type type) {
            super(type);
            this.field = field;
        }

        public MemberRef field() {
            return this.field;
        }

        public void visitForceChildren(TreeVisitor visitor) {
        }

        public void visit(TreeVisitor visitor) {
            visitor.visitStaticFieldExpr(this);
        }

        public int exprHashCode() {
            return 21 + this.field.hashCode() ^ this.type.simple().hashCode();
        }

        public boolean equalsExpr(Expr other) {
            return other != null && other instanceof StaticFieldExpr && ((StaticFieldExpr)other).field.equals(this.field);
        }

        public Object clone() {
            return this.copyInto(new StaticFieldExpr(this.field, this.type));
        }
    }
    public abstract class MemRefExpr extends MemExpr {
        public MemRefExpr(Type type) {
            super(type);
        }
    }
    public abstract class MemExpr extends DefExpr {
        public MemExpr(Type type) {
            super(type);
        }
    }
    public class PrintVisitor extends TreeVisitor {
        protected PrintWriter out;

        public PrintVisitor() {
            this(System.out);
        }

        public PrintVisitor(Writer out) {
            this.out = new PrintWriter(out);
        }

        public PrintVisitor(PrintStream out) {
            this.out = new PrintWriter(out);
        }

        protected void println() {
            this.out.println();
        }

        protected void println(Object s) {
            this.out.println(s);
        }

        protected void print(Object s) {
            this.out.print(s);
        }

        public void visitFlowGraph(FlowGraph cfg) {
            cfg.source().visit(this);
            Iterator e = cfg.trace().iterator();

            while(e.hasNext()) {
                Block block = (Block)e.next();
                block.visit(this);
            }

            cfg.sink().visit(this);
            this.out.flush();
        }

        public void visitBlock(Block block) {
            this.println();
            this.println(block);
            Handler handler = (Handler)block.graph().handlersMap().get(block);
            if (handler != null) {
                this.println("catches " + handler.catchType());
                this.println("protects " + handler.protectedBlocks());
            }

            block.visitChildren(this);
        }

        public void visitExprStmt(ExprStmt stmt) {
            this.print("eval ");
            stmt.expr().visit(this);
            this.println();
        }

        public void visitIfZeroStmt(IfZeroStmt stmt) {
            this.print("if0 (");
            stmt.expr().visit(this);
            this.print(" ");
            switch(stmt.comparison()) {
                case 0:
                    this.print("==");
                    break;
                case 1:
                    this.print("!=");
                    break;
                case 2:
                    this.print(">");
                    break;
                case 3:
                    this.print(">=");
                    break;
                case 4:
                    this.print("<");
                    break;
                case 5:
                    this.print("<=");
            }

            if (stmt.expr().type().isReference()) {
                this.print(" null");
            } else {
                this.print(" 0");
            }

            this.print(") then " + stmt.trueTarget() + " else " + stmt.falseTarget());
            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitIfCmpStmt(IfCmpStmt stmt) {
            this.print("if (");
            stmt.left().visit(this);
            this.print(" ");
            switch(stmt.comparison()) {
                case 0:
                    this.print("==");
                    break;
                case 1:
                    this.print("!=");
                    break;
                case 2:
                    this.print(">");
                    break;
                case 3:
                    this.print(">=");
                    break;
                case 4:
                    this.print("<");
                    break;
                case 5:
                    this.print("<=");
            }

            this.print(" ");
            if (stmt.right() != null) {
                stmt.right().visit(this);
            }

            this.print(") then " + stmt.trueTarget() + " else " + stmt.falseTarget());
            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitInitStmt(InitStmt stmt) {
            this.print("INIT");
            LocalExpr[] t = stmt.targets();
            if (t != null) {
                for(int i = 0; i < t.length; ++i) {
                    if (t[i] != null) {
                        this.print(" ");
                        t[i].visit(this);
                    }
                }
            }

            this.println();
        }

        public void visitGotoStmt(GotoStmt stmt) {
            this.print("goto " + stmt.target().label());
            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitLabelStmt(LabelStmt stmt) {
            if (stmt.label() != null) {
                this.println(stmt.label());
            }

        }

        public void visitMonitorStmt(MonitorStmt stmt) {
            if (stmt.kind() == 0) {
                this.print("enter ");
            } else {
                this.print("exit ");
            }

            this.print("monitor (");
            if (stmt.object() != null) {
                stmt.object().visit(this);
            }

            this.println(")");
        }

        public void visitCatchExpr(CatchExpr expr) {
            this.print("Catch(" + expr.catchType() + ")");
        }

        public void visitStackManipStmt(StackManipStmt stmt) {
            this.print("(");
            com.d0klabs.cryptowalt.data.StackExpr[] target = stmt.target();
            if (target != null) {
                for(int i = 0; i < target.length; ++i) {
                    target[i].visit(this);
                    if (i != target.length - 1) {
                        this.print(", ");
                    }
                }
            }
        }

        public void visitPhiJoinStmt(PhiJoinStmt stmt) {
            if (stmt.target() != null) {
                stmt.target().visit(this);
            }

            this.print(" := Phi(");
            if (stmt.hasParent()) {
                Tree tree = (Tree)stmt.parent();
                Block block = tree.block();
                Iterator e = block.graph().preds(block).iterator();

                while(e.hasNext()) {
                    Block pred = (Block)e.next();
                    Expr operand = stmt.operandAt(pred);
                    this.print(pred.label() + "=");
                    operand.visit(this);
                    if (e.hasNext()) {
                        this.print(", ");
                    }
                }
            } else {
                Iterator e = stmt.operands().iterator();

                while(e.hasNext()) {
                    Expr operand = (Expr)e.next();
                    operand.visit(this);
                    if (e.hasNext()) {
                        this.print(", ");
                    }
                }
            }

            this.println(")");
        }

        public void visitPhiCatchStmt(PhiCatchStmt stmt) {
            if (stmt.target() != null) {
                stmt.target().visit(this);
            }

            this.print(" := Phi-Catch(");
            Iterator e = stmt.operands().iterator();

            while(e.hasNext()) {
                Expr operand = (Expr)e.next();
                operand.visit(this);
                if (e.hasNext()) {
                    this.print(", ");
                }
            }

            this.println(")");
        }

        public void visitRetStmt(RetStmt stmt) {
            this.print("ret from " + stmt.sub());
            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitReturnExprStmt(ReturnExprStmt stmt) {
            this.print("return ");
            if (stmt.expr() != null) {
                stmt.expr().visit(this);
            }

            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitReturnStmt(ReturnStmt stmt) {
            this.print("return");
            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitStoreExpr(StoreExpr expr) {
            this.print("(");
            if (expr.target() != null) {
                expr.target().visit(this);
            }

            this.print(" := ");
            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            this.print(")");
        }

        public void visitAddressStoreStmt(AddressStoreStmt stmt) {
            this.print("La");
            if (stmt.sub() != null) {
                this.print(new Integer(stmt.sub().returnAddress().index()));
            } else {
                this.print("???");
            }

            this.println(" := returnAddress");
        }

        public void visitJsrStmt(JsrStmt stmt) {
            this.print("jsr ");
            if (stmt.sub() != null) {
                this.print(stmt.sub().entry());
            }

            if (stmt.follow() != null) {
                this.print(" ret to " + stmt.follow());
            }

            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitSwitchStmt(SwitchStmt stmt) {
            this.print("switch (");
            if (stmt.index() != null) {
                stmt.index().visit(this);
            }

            this.print(")");
            this.println(" caught by " + stmt.catchTargets());
            if (stmt.values() != null && stmt.targets() != null) {
                for(int i = 0; i < stmt.values().length; ++i) {
                    this.println("    case " + stmt.values()[i] + ": " + stmt.targets()[i]);
                }
            }

            this.println("    default: " + stmt.defaultTarget());
        }

        public void visitThrowStmt(ThrowStmt stmt) {
            this.print("throw ");
            if (stmt.expr() != null) {
                stmt.expr().visit(this);
            }

            this.println(" caught by " + stmt.catchTargets());
        }

        public void visitSCStmt(SCStmt stmt) {
            this.print("aswizzle ");
            if (stmt.array() != null) {
                stmt.array().visit(this);
            }

            if (stmt.index() != null) {
                stmt.index().visit(this);
            }

        }

        public void visitSRStmt(SRStmt stmt) {
            this.print("aswrange array: ");
            if (stmt.array() != null) {
                stmt.array().visit(this);
            }

            this.print(" start: ");
            if (stmt.start() != null) {
                stmt.start().visit(this);
            }

            this.print(" end: ");
            if (stmt.end() != null) {
                stmt.end().visit(this);
            }

            this.println("");
        }

        public void visitArithExpr(ArithExpr expr) {
            this.print("(");
            if (expr.left() != null) {
                expr.left().visit(this);
            }

            this.print(" ");
            switch(expr.operation()) {
                case 37:
                    this.print("%");
                    break;
                case 38:
                    this.print("&");
                    break;
                case 42:
                    this.print("*");
                    break;
                case 43:
                    this.print("+");
                    break;
                case 45:
                    this.print("-");
                    break;
                case 47:
                    this.print("/");
                    break;
                case 60:
                    this.print("<l=>");
                    break;
                case 62:
                    this.print("<g=>");
                    break;
                case 63:
                    this.print("<=>");
                    break;
                case 94:
                    this.print("^");
                    break;
                case 124:
                    this.print("|");
            }

            this.print(" ");
            if (expr.right() != null) {
                expr.right().visit(this);
            }

            this.print(")");
        }

        public void visitArrayLengthExpr(ArrayLengthExpr expr) {
            if (expr.array() != null) {
                expr.array().visit(this);
            }

            this.print(".length");
        }

        public void visitArrayRefExpr(ArrayRefExpr expr) {
            if (expr.array() != null) {
                expr.array().visit(this);
            }

            this.print("[");
            if (expr.index() != null) {
                expr.index().visit(this);
            }

            this.print("]");
        }

        public void visitCallMethodExpr(CallMethodExpr expr) {
            if (expr.receiver() != null) {
                expr.receiver().visit(this);
            }

            this.print(".");
            if (expr.method() != null) {
                this.print(expr.method().nameAndType().name());
            }

            this.print("(");
            if (expr.params() != null) {
                for(int i = 0; i < expr.params().length; ++i) {
                    expr.params()[i].visit(this);
                    if (i != expr.params().length - 1) {
                        this.print(", ");
                    }
                }
            }

            this.print(")");
        }

        public void visitCallStaticExpr(CallStaticExpr expr) {
            if (expr.method() != null) {
                this.print(expr.method().declaringClass());
            }

            this.print(".");
            if (expr.method() != null) {
                this.print(expr.method().nameAndType().name());
            }

            this.print("(");
            if (expr.params() != null) {
                for(int i = 0; i < expr.params().length; ++i) {
                    expr.params()[i].visit(this);
                    if (i != expr.params().length - 1) {
                        this.print(", ");
                    }
                }
            }

            this.print(")");
        }

        public void visitCastExpr(CastExpr expr) {
            this.print("((" + expr.castType() + ") ");
            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            this.print(")");
        }

        public void visitConstantExpr(ConstantExpr expr) {
            if (expr.value() instanceof String) {
                StringBuffer sb = new StringBuffer();
                String s = (String)expr.value();

                for(int i = 0; i < s.length(); ++i) {
                    char c = s.charAt(i);
                    if (!Character.isWhitespace(c) && (' ' > c || c > '~')) {
                        sb.append("\\u");
                        sb.append(Integer.toHexString(c));
                    } else {
                        sb.append(c);
                    }

                    if (sb.length() > 50) {
                        sb.append("...");
                        break;
                    }
                }

                this.print("'" + sb.toString() + "'");
            } else if (expr.value() instanceof Float) {
                this.print(expr.value() + "F");
            } else if (expr.value() instanceof Long) {
                this.print(expr.value() + "L");
            } else {
                this.print(expr.value());
            }

        }

        public void visitFieldExpr(FieldExpr expr) {
            if (expr.object() != null) {
                expr.object().visit(this);
            }

            this.print(".");
            if (expr.field() != null) {
                this.print(expr.field().nameAndType().name());
            }

        }

        public void visitInstanceOfExpr(InstanceOfExpr expr) {
            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            this.print(" instanceof " + expr.checkType());
        }

        public void visitLocalExpr(LocalExpr expr) {
            if (expr.fromStack()) {
                this.print("T");
            } else {
                this.print("L");
            }

            this.print(expr.type().shortName().toLowerCase());
            this.print(Integer.toString(expr.index()));
            DefExpr def = expr.def();
            if (def != null && def.version() != -1) {
                this.print("_" + def.version());
            } else {
                this.print("_undef");
            }

        }

        public void visitNegExpr(NegExpr expr) {
            this.print("-");
            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

        }

        public void visitNewArrayExpr(NewArrayExpr expr) {
            this.print("new " + expr.elementType() + "[");
            if (expr.size() != null) {
                expr.size().visit(this);
            }

            this.print("]");
        }

        public void visitNewExpr(NewExpr expr) {
            this.print("new " + expr.objectType());
        }

        public void visitNewMultiArrayExpr(NewMultiArrayExpr expr) {
            this.print("new " + expr.elementType());
            if (expr.dimensions() != null) {
                for(int i = 0; i < expr.dimensions().length; ++i) {
                    this.print("[" + expr.dimensions()[i] + "]");
                }
            }

        }

        public void visitZeroCheckExpr(ZeroCheckExpr expr) {
            if (expr.expr().type().isReference()) {
                this.print("notNull(");
            } else {
                this.print("notZero(");
            }

            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            this.print(")");
        }

        public void visitRCExpr(RCExpr expr) {
            this.print("rc(");
            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            this.print(")");
        }

        public void visitUCExpr(UCExpr expr) {
            if (expr.kind() == 1) {
                this.print("aupdate(");
            } else {
                this.print("supdate(");
            }

            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            this.print(")");
        }

        public void visitReturnAddressExpr(ReturnAddressExpr expr) {
            this.print("returnAddress");
        }

        public void visitShiftExpr(ShiftExpr expr) {
            this.print("(");
            if (expr.expr() != null) {
                expr.expr().visit(this);
            }

            if (expr.dir() == 0) {
                this.print("<<");
            } else if (expr.dir() == 1) {
                this.print(">>");
            } else if (expr.dir() == 2) {
                this.print(">>>");
            }

            if (expr.bits() != null) {
                expr.bits().visit(this);
            }

            this.print(")");
        }

        public void visitStackExpr(StackExpr expr) {
            this.print("S" + expr.type().shortName().toLowerCase() + expr.index());
            DefExpr def = expr.def();
            if (def != null && def.version() != -1) {
                this.print("_" + def.version());
            } else {
                this.print("_undef");
            }

        }

        public void visitStaticFieldExpr(StaticFieldExpr expr) {
            if (expr.field() != null) {
                this.print(expr.field().declaringClass() + "." + expr.field().nameAndType().name());
            }

        }

        public void visitExpr(Expr expr) {
            this.print("EXPR");
        }

        @Override
        public void visitTree(com.d0klabs.cryptowalt.data.Tree tree) {
            final Iterator iter = tree.stmts().iterator();

            while (iter.hasNext()) {
                final Stmt stmt = (Stmt) iter.next();

                if (stmt instanceof PhiStmt) {
                    iter.remove();

                } else {
                    stmt.visit(this);
                }
            }
        }

        public void visitStmt(Stmt stmt) {
            this.print("STMT");
        }
    }
    public class IfZeroStmt extends IfStmt {
        Expr expr;

        public IfZeroStmt(int comparison, Expr expr, Block trueTarget, Block falseTarget) {
            super(comparison, trueTarget, falseTarget);
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
            visitor.visitIfZeroStmt(this);
        }

        public Object clone() {
            return this.copyInto(new IfZeroStmt(this.comparison, (Expr)this.expr.clone(), this.trueTarget, this.falseTarget));
        }
    }
    public abstract class IfStmt extends JumpStmt {
        int comparison;
        Block trueTarget;
        Block falseTarget;
        public static final int EQ = 0;
        public static final int NE = 1;
        public static final int GT = 2;
        public static final int GE = 3;
        public static final int LT = 4;
        public static final int LE = 5;

        public IfStmt(int comparison, Block trueTarget, Block falseTarget) {
            this.comparison = comparison;
            this.trueTarget = trueTarget;
            this.falseTarget = falseTarget;
        }

        public int comparison() {
            return this.comparison;
        }

        public void negate() {
            switch(this.comparison) {
                case 0:
                    this.comparison = 1;
                    break;
                case 1:
                    this.comparison = 0;
                    break;
                case 2:
                    this.comparison = 5;
                    break;
                case 3:
                    this.comparison = 4;
                    break;
                case 4:
                    this.comparison = 3;
                    break;
                case 5:
                    this.comparison = 2;
            }

            Block t = this.trueTarget;
            this.trueTarget = this.falseTarget;
            this.falseTarget = t;
        }

        public void setTrueTarget(Block target) {
            this.trueTarget = target;
        }

        public void setFalseTarget(Block target) {
            this.falseTarget = target;
        }

        public Block trueTarget() {
            return this.trueTarget;
        }

        public Block falseTarget() {
            return this.falseTarget;
        }
    }
    public abstract class JumpStmt extends Stmt {
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
    class LabelStmt extends Stmt {
        Label label;

        public LabelStmt(Label label) {
            this.label = label;
        }

        public Label label() {
            return this.label;
        }

        public void visitForceChildren(TreeVisitor visitor) {
        }

        public void visit(TreeVisitor visitor) {
            visitor.visitLabelStmt(this);
        }

        public Object clone() {
            return this.copyInto(new LabelStmt(this.label));
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
    class MonitorStmt extends Stmt {
        public static final int ENTER = 0;
        public static final int EXIT = 1;
        int kind;
        Expr object;

        public MonitorStmt(int kind, Expr object) {
            this.kind = kind;
            this.object = object;
            object.setParent(this);
        }

        public Expr object() {
            return this.object;
        }

        public int kind() {
            return this.kind;
        }

        public void visitForceChildren(TreeVisitor visitor) {
            this.object.visit(visitor);
        }

        public void visit(TreeVisitor visitor) {
            visitor.visitMonitorStmt(this);
        }

        public Object clone() {
            return this.copyInto(new MonitorStmt(this.kind, (Expr)this.object.clone()));
        }
    }
    class GotoStmt extends JumpStmt {
        Block target;

        public GotoStmt(Block target) {
            this.target = target;
        }

        public void setTarget(Block target) {
            this.target = target;
        }

        public Block target() {
            return this.target;
        }

        public void visitForceChildren(TreeVisitor visitor) {
        }

        public void visit(TreeVisitor visitor) {
            visitor.visitGotoStmt(this);
        }

        public Object clone() {
            return this.copyInto(new GotoStmt(this.target));
        }
    }

    public class Liveness {
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

                        // Recall that a LocalExpr represents a use or a definition
                        // of a local variable. If the LocalExpr is defined by a
                        // PhiJoinStmt, the block in which it resides should already
                        // have some information about it.

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

                    if ((parent instanceof MemRefExpr)
                            && ((MemRefExpr) parent).isDef()) {
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
        static class IGNode extends GraphNode {
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

            @Override
            public boolean contains(Block pred) {
                return false;
            }

            @Override
            public void retainAll(Collection nodes) {

            }

            @Override
            public Iterator iterator() {
                return null;
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





}

package com.d0klabs.cryptowalt.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class CodeArray implements InstructionVisitor, Opcode {
    public static boolean DEBUG = Boolean.getBoolean("CodeArray.DEBUG");
    private ByteCell codeTail;
    private int codeLength;
    private Map branches;
    private Map longBranches;
    private Map branchInsts;
    private Map labels;
    private int lastInst;
    private int maxStack;
    private int stackHeight;
    private int maxLocals;
    private ConstantPool constants;
    private MethodEditor method;
    private boolean longBranch;
    private List insts;

    public CodeArray(MethodEditor method, ConstantPool constants, List insts) {
        this.constants = constants;
        this.method = method;
        this.insts = insts;
        this.maxStack = 0;
        this.maxLocals = 0;
        this.longBranch = false;
        this.buildCode();
    }

    private void buildCode() {
        this.codeTail = null;
        this.codeLength = 0;
        this.branches = new HashMap();
        this.longBranches = new HashMap();
        this.branchInsts = new HashMap();
        this.labels = new HashMap();
        this.maxLocals = this.method.type().stackHeight();
        if (!this.method.isStatic()) {
            ++this.maxLocals;
        }

        this.stackHeight = 0;
        Map labelPos = new HashMap();
        int[] heights = new int[this.insts.size()];
        Map retTargets = new HashMap();
        Map retInsts = new HashMap();
        Iterator iter;
        if (DEBUG) {
            System.out.println("Building code for " + this.method.declaringClass().name() + "." + this.method.name());
            iter = this.insts.iterator();

            while (iter.hasNext()) {
                Object o = iter.next();
                System.out.println("  " + o);
            }
        }

        iter = this.insts.iterator();
        int i = 0;

        Label subLabel;
        Label label;
        Object targets;
        while (iter.hasNext()) {
            Object ce = iter.next();
            if (ce instanceof Label) {
                subLabel = (Label) ce;
                this.stackHeight = 0;
                labelPos.put(subLabel, new Integer(i));
                this.addLabel(subLabel);
                heights[i++] = this.stackHeight;
                retTargets.containsKey(subLabel);
            } else {
                if (!(ce instanceof Instruction)) {
                    throw new IllegalArgumentException();
                }

                Instruction inst = (Instruction) ce;
                inst.visit(this);
                if (inst.isJsr()) {
                    heights[i++] = this.stackHeight;
                    Object x = iter.next();
                    label = (Label) inst.operand();
                    Label target = (Label) x;
                    targets = (Set) retTargets.get(label);
                    if (targets == null) {
                        targets = new HashSet();
                        retTargets.put(label, targets);
                    }

                    ((Set) targets).add(target);
                    this.stackHeight = 0;
                    labelPos.put(target, new Integer(i));
                    this.addLabel(target);
                    heights[i++] = this.stackHeight;
                } else {
                    heights[i++] = this.stackHeight;
                }
            }
        }

        boolean foundRet;
        for (Iterator subLabels = retTargets.keySet().iterator(); subLabels.hasNext(); ) {
            subLabel = (Label) subLabels.next();
            int pos = this.insts.indexOf(subLabel);
            foundRet = false;
            ListIterator liter = this.insts.listIterator(pos);

            while (liter.hasNext()) {
                targets = liter.next();
                if (targets instanceof Instruction) {
                    Instruction inst = (Instruction) targets;
                    if (inst.isRet()) {
                        retInsts.put(inst, subLabel);
                        foundRet = true;
                        break;
                    }
                }
            }
        }

        Iterator rets;
        if (DEBUG) {
            System.out.println("Subroutines and return targets:");
            Iterator subs = retTargets.keySet().iterator();

            while (subs.hasNext()) {
                Label sub = (Label) subs.next();
                System.out.print("  " + sub + ": ");
                Set s = (Set) retTargets.get(sub);
                rets = s.iterator();

                while (rets.hasNext()) {
                    Label ret = (Label) rets.next();
                    System.out.print(ret.toString());
                    if (rets.hasNext()) {
                        System.out.print(", ");
                    }
                }

                System.out.println("");
            }
        }

        Set visited = new HashSet();
        Stack stack = new Stack();
        if (this.insts.size() > 0) {
            label = (Label) this.insts.get(0);
            visited.add(label);
            stack.push(new HeightRecord(label, 0));
        }

        rets = this.method.tryCatches().iterator();

        while (rets.hasNext()) {
            TryCatch tc = (TryCatch) rets.next();
            visited.add(tc.handler());
            stack.push(new HeightRecord(tc.handler(), 1));
        }

        while (true) {
            label243:
            while (!stack.isEmpty()) {
                HeightRecord h = (HeightRecord) stack.pop();
                Integer labelIndex = (Integer) labelPos.get(h.label);
                int start = labelIndex;
                int diff = h.height - heights[start];
                heights[start] = h.height;
                ListIterator blockIter = this.insts.listIterator(start + 1);
                i = start;

                while (true) {
                    while (true) {
                        if (!blockIter.hasNext()) {
                            continue label243;
                        }

                        Object ce = blockIter.next();
                        ++i;
                        if (ce instanceof Instruction) {
                            Instruction inst = (Instruction) ce;
                            if (inst.isReturn() || inst.isThrow()) {
                                heights[i] = 0;
                                continue label243;
                            }

                            if (!inst.isConditionalJump()) {
                                if (inst.isGoto() || inst.isJsr()) {
                                    heights[i] += diff;
                                    label = (Label) inst.operand();
                                    if (diff > 0 || !visited.contains(label)) {
                                        visited.add(label);
                                        stack.push(new HeightRecord(label, heights[i]));
                                    }
                                    continue label243;
                                }

                                if (inst.isRet()) {
                                    heights[i] += diff;
                                    subLabel = (Label) retInsts.get(inst);
                                    targets = (Set) retTargets.get(subLabel);
                                    Iterator retIter = ((Set<?>) targets).iterator();
                                    while (true) {
                                        int idx;
                                        do {
                                            if (!retIter.hasNext()) {
                                                continue label243;
                                            }
                                            label = (Label) retIter.next();
                                            labelIndex = (Integer) labelPos.get(label);
                                            idx = labelIndex;
                                        } while (heights[idx] >= heights[i] && visited.contains(label));

                                        visited.add(label);
                                        stack.push(new HeightRecord(label, heights[i]));
                                    }
                                }

                                if (inst.isSwitch()) {
                                    heights[i] += diff;

                                    Switch sw = (Switch) inst.operand();
                                    label = sw.defaultTarget();
                                    if (diff > 0 || !visited.contains(label)) {
                                        visited.add(label);
                                        stack.push(new HeightRecord(label, heights[i]));
                                    }

                                    Label[] tgets = sw.targets();
                                    int j = 0;

                                    while (true) {
                                        if (j >= tgets.length) {
                                            continue label243;
                                        }

                                        label = tgets[j];
                                        if (diff > 0 || !visited.contains(label)) {
                                            visited.add(label);
                                            stack.push(new HeightRecord(label, heights[i]));
                                        }

                                        ++j;
                                    }
                                }

                                heights[i] += diff;
                            } else {
                                heights[i] += diff;
                                label = (Label) inst.operand();
                                if (diff > 0 || !visited.contains(label)) {
                                    visited.add(label);
                                    stack.push(new HeightRecord(label, heights[i]));
                                }
                            }
                        } else if (ce instanceof Label) {
                            label = (Label) ce;
                            diff = heights[i - 1] - heights[i];
                            if (diff > 0 || !visited.contains(label)) {
                                visited.add(label);
                                heights[i] = heights[i - 1];
                            }
                        }
                    }
                }
            }

            this.maxStack = 0;

            for (i = 0; i < heights.length; ++i) {
                int h = heights[i];
                if (h > this.maxStack) {
                    this.maxStack = h;
                }
            }

            return;
        }
    }

    public int maxLocals() {
        return this.maxLocals;
    }

    public int maxStack() {
        return this.maxStack;
    }

    public int labelIndex(Label label) {
        Integer i = (Integer) this.labels.get(label);
        if (i != null) {
            return i;
        } else {
            throw new IllegalArgumentException("Label " + label + " not found");
        }
    }

    public byte[] array() {
        if (this.branches.size() > 0 && !this.longBranch && this.codeLength >= 65536) {
            this.longBranch = true;
            this.buildCode();
        }

        byte[] c = new byte[this.codeLength];
        int i = this.codeLength;

        for (ByteCell p = this.codeTail; p != null; p = p.prev) {
            --i;
            c[i] = p.value;
        }

        Integer branch;
        int branchIndex;
        Integer inst;
        int instIndex;
        Label label;
        Integer target;
        int diff;
        Iterator e;
        for (e = this.branches.keySet().iterator(); e.hasNext(); c[branchIndex + 1] = (byte) (diff & 255)) {
            branch = (Integer) e.next();
            branchIndex = branch;
            inst = (Integer) this.branchInsts.get(branch);
            instIndex = inst;
            label = (Label) this.branches.get(branch);
            target = (Integer) this.labels.get(label);
            diff = target - instIndex;
            c[branchIndex] = (byte) (diff >>> 8 & 255);
        }

        for (e = this.longBranches.keySet().iterator(); e.hasNext(); c[branchIndex + 3] = (byte) (diff & 255)) {
            branch = (Integer) e.next();
            branchIndex = branch;
            inst = (Integer) this.branchInsts.get(branch);
            instIndex = inst;
            label = (Label) this.longBranches.get(branch);
            target = (Integer) this.labels.get(label);
            diff = target - instIndex;
            c[branchIndex] = (byte) (diff >>> 24 & 255);
            c[branchIndex + 1] = (byte) (diff >>> 16 & 255);
            c[branchIndex + 2] = (byte) (diff >>> 8 & 255);
        }

        return c;
    }

    public void addLabel(Label label) {
        this.labels.put(label, new Integer(this.codeLength));
    }

    public void addLongBranch(Label label) {
        this.branchInsts.put(new Integer(this.codeLength), new Integer(this.lastInst));
        this.longBranches.put(new Integer(this.codeLength), label);
        this.addByte(0);
        this.addByte(0);
        this.addByte(0);
        this.addByte(0);
    }

    public void addBranch(Label label) {
        this.branchInsts.put(new Integer(this.codeLength), new Integer(this.lastInst));
        this.branches.put(new Integer(this.codeLength), label);
        this.addByte(0);
        this.addByte(0);
    }

    public void addOpcode(int opcode) {
        this.lastInst = this.codeLength;
        this.addByte(opcode);
        if (opcode == 170 || opcode == 171) {
            while (this.codeLength % 4 != 0) {
                this.addByte(0);
            }
        }

    }

    public void addByte(int i) {
        ByteCell p = new ByteCell();
        p.value = (byte) (i & 255);
        p.prev = this.codeTail;
        this.codeTail = p;
        ++this.codeLength;
    }

    public void addShort(int i) {
        this.addByte(i >>> 8);
        this.addByte(i);
    }

    public void addInt(int i) {
        this.addByte(i >>> 24);
        this.addByte(i >>> 16);
        this.addByte(i >>> 8);
        this.addByte(i);
    }

    public void visit_nop(Instruction inst) {
        this.addOpcode(0);
        this.stackHeight += 0;
    }

    public void visit_ldc(Instruction inst) {
        Object operand = inst.operand();
        if (operand == null) {
            this.addOpcode(1);
            ++this.stackHeight;
        } else {
            int index;
            if (operand instanceof Integer) {
                index = (Integer) operand;
                switch (index) {
                    case -1:
                        this.addOpcode(2);
                        break;
                    case 0:
                        this.addOpcode(3);
                        break;
                    case 1:
                        this.addOpcode(4);
                        break;
                    case 2:
                        this.addOpcode(5);
                        break;
                    case 3:
                        this.addOpcode(6);
                        break;
                    case 4:
                        this.addOpcode(7);
                        break;
                    case 5:
                        this.addOpcode(8);
                        break;
                    default:
                        if ((byte) index == index) {
                            this.addOpcode(16);
                            this.addByte(index);
                        } else if ((short) index == index) {
                            this.addOpcode(17);
                            this.addShort(index);
                        } else {
                            index = this.constants.addConstant(3, operand);
                            if (index < 256) {
                                this.addOpcode(18);
                                this.addByte(index);
                            } else {
                                this.addOpcode(19);
                                this.addShort(index);
                            }
                        }
                }

                ++this.stackHeight;
            } else if (operand instanceof Float) {
                float v = (Float) operand;
                if (v == 0.0F) {
                    this.addOpcode(11);
                } else if (v == 1.0F) {
                    this.addOpcode(12);
                } else if (v == 2.0F) {
                    this.addOpcode(13);
                } else {
                    index = this.constants.addConstant(4, operand);
                    if (index < 256) {
                        this.addOpcode(18);
                        this.addByte(index);
                    } else {
                        this.addOpcode(19);
                        this.addShort(index);
                    }
                }

                ++this.stackHeight;
            } else {
                if (operand instanceof Long) {
                    long v = (Long) operand;
                    if (v == 0L) {
                        this.addOpcode(9);
                    } else if (v == 1L) {
                        this.addOpcode(10);
                    } else {
                        index = this.constants.addConstant(5, operand);
                        this.addOpcode(20);
                        this.addShort(index);
                    }

                    this.stackHeight += 2;
                } else if (operand instanceof Double) {
                    double v = (Double) operand;
                    if (v == 0.0D) {
                        this.addOpcode(14);
                    } else if (v == 1.0D) {
                        this.addOpcode(15);
                    } else {
                        index = this.constants.addConstant(6, operand);
                        this.addOpcode(20);
                        this.addShort(index);
                    }

                    this.stackHeight += 2;
                } else if (operand instanceof String) {
                    index = this.constants.addConstant(8, operand);
                    this.createLDC(index);
                } else {
                    if (!(operand instanceof Type)) {
                        throw new RuntimeException();
                    }

                    index = this.constants.addConstant(7, operand);
                    this.createLDC(index);
                }
            }
        }

    }

    private void createLDC(int index) {
        if (index < 256) {
            this.addOpcode(18);
            this.addByte(index);
        } else {
            this.addOpcode(19);
            this.addShort(index);
        }

        ++this.stackHeight;
    }

    public void visit_iload(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(21);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(21);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(26);
                    break;
                case 1:
                    this.addOpcode(27);
                    break;
                case 2:
                    this.addOpcode(28);
                    break;
                case 3:
                    this.addOpcode(29);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(21);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(21);
                        this.addShort(index);
                    }
            }

            ++this.stackHeight;
        }
    }

    public void visit_lload(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(22);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(22);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(30);
                    break;
                case 1:
                    this.addOpcode(31);
                    break;
                case 2:
                    this.addOpcode(32);
                    break;
                case 3:
                    this.addOpcode(33);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(22);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(22);
                        this.addShort(index);
                    }
            }

            this.stackHeight += 2;
        }
    }

    public void visit_fload(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(23);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(23);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(34);
                    break;
                case 1:
                    this.addOpcode(35);
                    break;
                case 2:
                    this.addOpcode(36);
                    break;
                case 3:
                    this.addOpcode(37);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(23);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(23);
                        this.addShort(index);
                    }
            }

            ++this.stackHeight;
        }
    }

    public void visit_dload(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(24);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(24);
                this.addShort(index);
            }

            this.stackHeight += 2;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(38);
                    break;
                case 1:
                    this.addOpcode(39);
                    break;
                case 2:
                    this.addOpcode(40);
                    break;
                case 3:
                    this.addOpcode(41);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(24);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(24);
                        this.addShort(index);
                    }
            }

            this.stackHeight += 2;
        }
    }

    public void visit_aload(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(25);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(25);
                this.addShort(index);
            }

            ++this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(42);
                    break;
                case 1:
                    this.addOpcode(43);
                    break;
                case 2:
                    this.addOpcode(44);
                    break;
                case 3:
                    this.addOpcode(45);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(25);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(25);
                        this.addShort(index);
                    }
            }

            ++this.stackHeight;
        }
    }

    public void visit_iaload(Instruction inst) {
        this.addOpcode(46);
        --this.stackHeight;
    }

    public void visit_laload(Instruction inst) {
        this.addOpcode(47);
        this.stackHeight += 0;
    }

    public void visit_faload(Instruction inst) {
        this.addOpcode(48);
        --this.stackHeight;
    }

    public void visit_daload(Instruction inst) {
        this.addOpcode(49);
        this.stackHeight += 0;
    }

    public void visit_aaload(Instruction inst) {
        this.addOpcode(50);
        --this.stackHeight;
    }

    public void visit_baload(Instruction inst) {
        this.addOpcode(51);
        --this.stackHeight;
    }

    public void visit_caload(Instruction inst) {
        this.addOpcode(52);
        --this.stackHeight;
    }

    public void visit_saload(Instruction inst) {
        this.addOpcode(53);
        --this.stackHeight;
    }

    public void visit_istore(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(54);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(54);
                this.addShort(index);
            }

            --this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(59);
                    break;
                case 1:
                    this.addOpcode(60);
                    break;
                case 2:
                    this.addOpcode(61);
                    break;
                case 3:
                    this.addOpcode(62);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(54);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(54);
                        this.addShort(index);
                    }
            }

            --this.stackHeight;
        }
    }

    public void visit_lstore(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(55);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(55);
                this.addShort(index);
            }

            this.stackHeight -= 2;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(63);
                    break;
                case 1:
                    this.addOpcode(64);
                    break;
                case 2:
                    this.addOpcode(65);
                    break;
                case 3:
                    this.addOpcode(66);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(55);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(55);
                        this.addShort(index);
                    }
            }

            this.stackHeight -= 2;
        }
    }

    public void visit_fstore(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(56);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(56);
                this.addShort(index);
            }

            --this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(67);
                    break;
                case 1:
                    this.addOpcode(68);
                    break;
                case 2:
                    this.addOpcode(69);
                    break;
                case 3:
                    this.addOpcode(70);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(56);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(56);
                        this.addShort(index);
                    }
            }

            --this.stackHeight;
        }
    }

    public void visit_dstore(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 2 > this.maxLocals) {
            this.maxLocals = index + 2;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(57);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(57);
                this.addShort(index);
            }

            this.stackHeight -= 2;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(71);
                    break;
                case 1:
                    this.addOpcode(72);
                    break;
                case 2:
                    this.addOpcode(73);
                    break;
                case 3:
                    this.addOpcode(74);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(57);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(57);
                        this.addShort(index);
                    }
            }

            this.stackHeight -= 2;
        }
    }

    public void visit_astore(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (inst.useSlow()) {
            if (index < 256) {
                this.addOpcode(58);
                this.addByte(index);
            } else {
                this.addOpcode(196);
                this.addByte(58);
                this.addShort(index);
            }

            --this.stackHeight;
        } else {
            switch (index) {
                case 0:
                    this.addOpcode(75);
                    break;
                case 1:
                    this.addOpcode(76);
                    break;
                case 2:
                    this.addOpcode(77);
                    break;
                case 3:
                    this.addOpcode(78);
                    break;
                default:
                    if (index < 256) {
                        this.addOpcode(58);
                        this.addByte(index);
                    } else {
                        this.addOpcode(196);
                        this.addByte(58);
                        this.addShort(index);
                    }
            }

            --this.stackHeight;
        }
    }

    public void visit_iastore(Instruction inst) {
        this.addOpcode(79);
        this.stackHeight -= 3;
    }

    public void visit_lastore(Instruction inst) {
        this.addOpcode(80);
        this.stackHeight -= 4;
    }

    public void visit_fastore(Instruction inst) {
        this.addOpcode(81);
        this.stackHeight -= 3;
    }

    public void visit_dastore(Instruction inst) {
        this.addOpcode(82);
        this.stackHeight -= 4;
    }

    public void visit_aastore(Instruction inst) {
        this.addOpcode(83);
        this.stackHeight -= 3;
    }

    public void visit_bastore(Instruction inst) {
        this.addOpcode(84);
        this.stackHeight -= 3;
    }

    public void visit_castore(Instruction inst) {
        this.addOpcode(85);
        this.stackHeight -= 3;
    }

    public void visit_sastore(Instruction inst) {
        this.addOpcode(86);
        this.stackHeight -= 3;
    }

    public void visit_pop(Instruction inst) {
        this.addOpcode(87);
        --this.stackHeight;
    }

    public void visit_pop2(Instruction inst) {
        this.addOpcode(88);
        this.stackHeight -= 2;
    }

    public void visit_dup(Instruction inst) {
        this.addOpcode(89);
        ++this.stackHeight;
    }

    public void visit_dup_x1(Instruction inst) {
        this.addOpcode(90);
        ++this.stackHeight;
    }

    public void visit_dup_x2(Instruction inst) {
        this.addOpcode(91);
        ++this.stackHeight;
    }

    public void visit_dup2(Instruction inst) {
        this.addOpcode(92);
        this.stackHeight += 2;
    }

    public void visit_dup2_x1(Instruction inst) {
        this.addOpcode(93);
        this.stackHeight += 2;
    }

    public void visit_dup2_x2(Instruction inst) {
        this.addOpcode(94);
        this.stackHeight += 2;
    }

    public void visit_swap(Instruction inst) {
        this.addOpcode(95);
    }

    public void visit_iadd(Instruction inst) {
        this.addOpcode(96);
        --this.stackHeight;
    }

    public void visit_ladd(Instruction inst) {
        this.addOpcode(97);
        this.stackHeight -= 2;
    }

    public void visit_fadd(Instruction inst) {
        this.addOpcode(98);
        --this.stackHeight;
    }

    public void visit_dadd(Instruction inst) {
        this.addOpcode(99);
        this.stackHeight -= 2;
    }

    public void visit_isub(Instruction inst) {
        this.addOpcode(100);
        --this.stackHeight;
    }

    public void visit_lsub(Instruction inst) {
        this.addOpcode(101);
        this.stackHeight -= 2;
    }

    public void visit_fsub(Instruction inst) {
        this.addOpcode(102);
        --this.stackHeight;
    }

    public void visit_dsub(Instruction inst) {
        this.addOpcode(103);
        this.stackHeight -= 2;
    }

    public void visit_imul(Instruction inst) {
        this.addOpcode(104);
        --this.stackHeight;
    }

    public void visit_lmul(Instruction inst) {
        this.addOpcode(105);
        this.stackHeight -= 2;
    }

    public void visit_fmul(Instruction inst) {
        this.addOpcode(106);
        --this.stackHeight;
    }

    public void visit_dmul(Instruction inst) {
        this.addOpcode(107);
        this.stackHeight -= 2;
    }

    public void visit_idiv(Instruction inst) {
        this.addOpcode(108);
        --this.stackHeight;
    }

    public void visit_ldiv(Instruction inst) {
        this.addOpcode(109);
        this.stackHeight -= 2;
    }

    public void visit_fdiv(Instruction inst) {
        this.addOpcode(110);
        --this.stackHeight;
    }

    public void visit_ddiv(Instruction inst) {
        this.addOpcode(111);
        this.stackHeight -= 2;
    }

    public void visit_irem(Instruction inst) {
        this.addOpcode(112);
        --this.stackHeight;
    }

    public void visit_lrem(Instruction inst) {
        this.addOpcode(113);
        this.stackHeight -= 2;
    }

    public void visit_frem(Instruction inst) {
        this.addOpcode(114);
        --this.stackHeight;
    }

    public void visit_drem(Instruction inst) {
        this.addOpcode(115);
        this.stackHeight -= 2;
    }

    public void visit_ineg(Instruction inst) {
        this.addOpcode(116);
        this.stackHeight += 0;
    }

    public void visit_lneg(Instruction inst) {
        this.addOpcode(117);
        this.stackHeight += 0;
    }

    public void visit_fneg(Instruction inst) {
        this.addOpcode(118);
        this.stackHeight += 0;
    }

    public void visit_dneg(Instruction inst) {
        this.addOpcode(119);
        this.stackHeight += 0;
    }

    public void visit_ishl(Instruction inst) {
        this.addOpcode(120);
        --this.stackHeight;
    }

    public void visit_lshl(Instruction inst) {
        this.addOpcode(121);
        --this.stackHeight;
    }

    public void visit_ishr(Instruction inst) {
        this.addOpcode(122);
        --this.stackHeight;
    }

    public void visit_lshr(Instruction inst) {
        this.addOpcode(123);
        --this.stackHeight;
    }

    public void visit_iushr(Instruction inst) {
        this.addOpcode(124);
        --this.stackHeight;
    }

    public void visit_lushr(Instruction inst) {
        this.addOpcode(125);
        --this.stackHeight;
    }

    public void visit_iand(Instruction inst) {
        this.addOpcode(126);
        --this.stackHeight;
    }

    public void visit_land(Instruction inst) {
        this.addOpcode(127);
        this.stackHeight -= 2;
    }

    public void visit_ior(Instruction inst) {
        this.addOpcode(128);
        --this.stackHeight;
    }

    public void visit_lor(Instruction inst) {
        this.addOpcode(129);
        this.stackHeight -= 2;
    }

    public void visit_ixor(Instruction inst) {
        this.addOpcode(130);
        --this.stackHeight;
    }

    public void visit_lxor(Instruction inst) {
        this.addOpcode(131);
        this.stackHeight -= 2;
    }

    public void visit_iinc(Instruction inst) {
        IncOperand operand = (IncOperand) inst.operand();
        int index = operand.var().index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        int incr = operand.incr();
        if (index < 256 && (byte) incr == incr) {
            this.addOpcode(132);
            this.addByte(index);
            this.addByte(incr);
        } else {
            this.addOpcode(196);
            this.addByte(132);
            this.addShort(index);
            this.addShort(incr);
        }

        this.stackHeight += 0;
    }

    public void visit_i2l(Instruction inst) {
        this.addOpcode(133);
        ++this.stackHeight;
    }

    public void visit_i2f(Instruction inst) {
        this.addOpcode(134);
        this.stackHeight += 0;
    }

    public void visit_i2d(Instruction inst) {
        this.addOpcode(135);
        ++this.stackHeight;
    }

    public void visit_l2i(Instruction inst) {
        this.addOpcode(136);
        --this.stackHeight;
    }

    public void visit_l2f(Instruction inst) {
        this.addOpcode(137);
        --this.stackHeight;
    }

    public void visit_l2d(Instruction inst) {
        this.addOpcode(138);
        this.stackHeight += 0;
    }

    public void visit_f2i(Instruction inst) {
        this.addOpcode(139);
        this.stackHeight += 0;
    }

    public void visit_f2l(Instruction inst) {
        this.addOpcode(140);
        ++this.stackHeight;
    }

    public void visit_f2d(Instruction inst) {
        this.addOpcode(141);
        ++this.stackHeight;
    }

    public void visit_d2i(Instruction inst) {
        this.addOpcode(142);
        --this.stackHeight;
    }

    public void visit_d2l(Instruction inst) {
        this.addOpcode(143);
        this.stackHeight += 0;
    }

    public void visit_d2f(Instruction inst) {
        this.addOpcode(144);
        --this.stackHeight;
    }

    public void visit_i2b(Instruction inst) {
        this.addOpcode(145);
        this.stackHeight += 0;
    }

    public void visit_i2c(Instruction inst) {
        this.addOpcode(146);
        this.stackHeight += 0;
    }

    public void visit_i2s(Instruction inst) {
        this.addOpcode(147);
        this.stackHeight += 0;
    }

    public void visit_lcmp(Instruction inst) {
        this.addOpcode(148);
        this.stackHeight -= 3;
    }

    public void visit_fcmpl(Instruction inst) {
        this.addOpcode(149);
        --this.stackHeight;
    }

    public void visit_fcmpg(Instruction inst) {
        this.addOpcode(150);
        --this.stackHeight;
    }

    public void visit_dcmpl(Instruction inst) {
        this.addOpcode(151);
        this.stackHeight -= 3;
    }

    public void visit_dcmpg(Instruction inst) {
        this.addOpcode(152);
        this.stackHeight -= 3;
    }

    public void visit_ifeq(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(154);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(153);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifne(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(153);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(154);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_iflt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(156);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(155);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifge(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(155);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(156);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifgt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(158);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(157);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifle(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(157);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(158);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_if_icmpeq(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(160);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(159);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmpne(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(159);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(160);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmplt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(162);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(161);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmpge(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(161);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(162);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmpgt(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(164);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(163);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_icmple(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(163);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(164);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_acmpeq(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(166);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(165);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_if_acmpne(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(165);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(166);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight -= 2;
    }

    public void visit_goto(Instruction inst) {
        if (this.longBranch) {
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
        } else {
            this.addOpcode(167);
            this.addBranch((Label) inst.operand());
        }

        this.stackHeight += 0;
    }

    public void visit_jsr(Instruction inst) {
        if (this.longBranch) {
            this.addOpcode(201);
            this.addLongBranch((Label) inst.operand());
        } else {
            this.addOpcode(168);
            this.addBranch((Label) inst.operand());
        }

        ++this.stackHeight;
    }

    public void visit_ret(Instruction inst) {
        int index = ((LocalVariable) inst.operand()).index();
        if (index + 1 > this.maxLocals) {
            this.maxLocals = index + 1;
        }

        if (index < 256) {
            this.addOpcode(169);
            this.addByte(index);
        } else {
            this.addOpcode(196);
            this.addByte(169);
            this.addShort(index);
        }

        this.stackHeight += 0;
    }

    public void visit_switch(Instruction inst) {
        Switch sw = (Switch) inst.operand();
        int[] values = sw.values();
        Label[] targets = sw.targets();
        if (values.length == 0) {
            if (this.longBranch) {
                this.addOpcode(87);
                this.addOpcode(200);
                this.addLongBranch(sw.defaultTarget());
            } else {
                this.addOpcode(87);
                this.addOpcode(167);
                this.addBranch(sw.defaultTarget());
            }
        } else {
            int i;
            if (sw.hasContiguousValues()) {
                this.addOpcode(170);
                this.addLongBranch(sw.defaultTarget());
                this.addInt(values[0]);
                this.addInt(values[values.length - 1]);

                for (i = 0; i < targets.length; ++i) {
                    this.addLongBranch(targets[i]);
                }
            } else {
                this.addOpcode(171);
                this.addLongBranch(sw.defaultTarget());
                this.addInt(values.length);

                for (i = 0; i < targets.length; ++i) {
                    this.addInt(values[i]);
                    this.addLongBranch(targets[i]);
                }
            }
        }

        --this.stackHeight;
    }

    public void visit_ireturn(Instruction inst) {
        this.addOpcode(172);
        this.stackHeight = 0;
    }

    public void visit_lreturn(Instruction inst) {
        this.addOpcode(173);
        this.stackHeight = 0;
    }

    public void visit_freturn(Instruction inst) {
        this.addOpcode(174);
        this.stackHeight = 0;
    }

    public void visit_dreturn(Instruction inst) {
        this.addOpcode(175);
        this.stackHeight = 0;
    }

    public void visit_areturn(Instruction inst) {
        this.addOpcode(176);
        this.stackHeight = 0;
    }

    public void visit_return(Instruction inst) {
        this.addOpcode(177);
        this.stackHeight = 0;
    }

    public void visit_getstatic(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(178);
        this.addShort(index);
        Type type = ((MemberRef) inst.operand()).nameAndType().type();
        this.stackHeight += type.stackHeight();
    }

    public void visit_putstatic(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(179);
        this.addShort(index);
        Type type = ((MemberRef) inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight();
    }

    public void visit_putstatic_nowb(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(205);
        this.addShort(index);
        Type type = ((MemberRef) inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight();
    }

    public void visit_getfield(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(180);
        this.addShort(index);
        Type type = ((MemberRef) inst.operand()).nameAndType().type();
        this.stackHeight += type.stackHeight() - 1;
    }

    public void visit_putfield(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(181);
        this.addShort(index);
        Type type = ((MemberRef) inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight() + 1;
    }

    public void visit_putfield_nowb(Instruction inst) {
        int index = this.constants.addConstant(9, inst.operand());
        this.addOpcode(204);
        this.addShort(index);
        Type type = ((MemberRef) inst.operand()).nameAndType().type();
        this.stackHeight -= type.stackHeight() + 1;
    }

    public void visit_invokevirtual(Instruction inst) {
        int index = this.constants.addConstant(10, inst.operand());
        this.addOpcode(182);
        this.addShort(index);
        MemberRef method = (MemberRef) inst.operand();
        Type type = method.nameAndType().type();
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight() - 1;
    }

    public void visit_invokespecial(Instruction inst) {
        int index = this.constants.addConstant(10, inst.operand());
        this.addOpcode(183);
        this.addShort(index);
        MemberRef method = (MemberRef) inst.operand();
        Type type = method.nameAndType().type();
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight() - 1;
    }

    public void visit_invokestatic(Instruction inst) {
        int index = this.constants.addConstant(10, inst.operand());
        this.addOpcode(184);
        this.addShort(index);
        MemberRef method = (MemberRef) inst.operand();
        Type type = method.nameAndType().type();
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight();
    }

    public void visit_invokeinterface(Instruction inst) {
        int index = this.constants.addConstant(11, inst.operand());
        MemberRef method = (MemberRef) this.constants.constantAt(index);
        Type type = method.nameAndType().type();
        this.addOpcode(185);
        this.addShort(index);
        this.addByte(type.stackHeight() + 1);
        this.addByte(0);
        this.stackHeight += type.returnType().stackHeight() - type.stackHeight() - 1;
    }

    public void visit_new(Instruction inst) {
        int index = this.constants.addConstant(7, inst.operand());
        this.addOpcode(187);
        this.addShort(index);
        ++this.stackHeight;
    }

    public void visit_newarray(Instruction inst) {
        Type type = (Type) inst.operand();
        if (type.isReference()) {
            int index = this.constants.addConstant(7, type);
            this.addOpcode(189);
            this.addShort(index);
        } else {
            this.addOpcode(188);
            this.addByte(type.typeCode());
        }

        this.stackHeight += 0;
    }

    public void visit_arraylength(Instruction inst) {
        this.addOpcode(190);
        this.stackHeight += 0;
    }

    public void visit_athrow(Instruction inst) {
        this.addOpcode(191);
        this.stackHeight = 0;
    }

    public void visit_checkcast(Instruction inst) {
        int index = this.constants.addConstant(7, inst.operand());
        this.addOpcode(192);
        this.addShort(index);
        this.stackHeight += 0;
    }

    public void visit_instanceof(Instruction inst) {
        int index = this.constants.addConstant(7, inst.operand());
        this.addOpcode(193);
        this.addShort(index);
        this.stackHeight += 0;
    }

    public void visit_monitorenter(Instruction inst) {
        this.addOpcode(194);
        --this.stackHeight;
    }

    public void visit_monitorexit(Instruction inst) {
        this.addOpcode(195);
        --this.stackHeight;
    }

    public void visit_multianewarray(Instruction inst) {
        MultiArrayOperand operand = (MultiArrayOperand) inst.operand();
        Type type = operand.type();
        int dim = operand.dimensions();
        int index = this.constants.addConstant(7, type);
        this.addOpcode(197);
        this.addShort(index);
        this.addByte(dim);
        this.stackHeight += 1 - dim;
    }

    public void visit_ifnull(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(199);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(198);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_ifnonnull(Instruction inst) {
        if (this.longBranch) {
            Label tmp = this.method.newLabel();
            this.addOpcode(198);
            this.addBranch(tmp);
            this.addOpcode(200);
            this.addLongBranch((Label) inst.operand());
            this.addLabel(tmp);
        } else {
            this.addOpcode(199);
            this.addBranch((Label) inst.operand());
        }

        --this.stackHeight;
    }

    public void visit_rc(Instruction inst) {
        Integer operand = (Integer) inst.operand();
        this.addOpcode(237);
        this.addByte(operand);
        this.stackHeight += 0;
    }

    public void visit_aswizzle(Instruction inst) {
        this.addOpcode(240);
        this.stackHeight -= 2;
    }

    public void visit_aswrange(Instruction inst) {
        this.addOpcode(241);
        this.stackHeight -= 3;
    }

    public void visit_aupdate(Instruction inst) {
        Integer operand = (Integer) inst.operand();
        this.addOpcode(238);
        this.addByte(operand);
        this.stackHeight += 0;
    }

    public void visit_supdate(Instruction inst) {
        Integer operand = (Integer) inst.operand();
        this.addOpcode(239);
        this.addByte(operand);
        this.stackHeight += 0;
    }

    class ByteCell {
        byte value;
        ByteCell prev;

        ByteCell() {
        }
    }

    class HeightRecord {
        Label label;
        int height;

        public HeightRecord(Label label, int height) {
            this.label = label;
            this.height = height;
        }
    }
}

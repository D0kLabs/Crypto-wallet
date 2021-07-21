package com.d0klabs.cryptowalt.data;

import android.widget.Switch;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface InstructionVisitor {
    void visit_nop(Instruction var1);

    void visit_ldc(Instruction var1);

    void visit_iload(Instruction var1);

    void visit_lload(Instruction var1);

    void visit_fload(Instruction var1);

    void visit_dload(Instruction var1);

    void visit_aload(Instruction var1);

    void visit_iaload(Instruction var1);

    void visit_laload(Instruction var1);

    void visit_faload(Instruction var1);

    void visit_daload(Instruction var1);

    void visit_aaload(Instruction var1);

    void visit_baload(Instruction var1);

    void visit_caload(Instruction var1);

    void visit_saload(Instruction var1);

    void visit_istore(Instruction var1);

    void visit_lstore(Instruction var1);

    void visit_fstore(Instruction var1);

    void visit_dstore(Instruction var1);

    void visit_astore(Instruction var1);

    void visit_iastore(Instruction var1);

    void visit_lastore(Instruction var1);

    void visit_fastore(Instruction var1);

    void visit_dastore(Instruction var1);

    void visit_aastore(Instruction var1);

    void visit_bastore(Instruction var1);

    void visit_castore(Instruction var1);

    void visit_sastore(Instruction var1);

    void visit_pop(Instruction var1);

    void visit_pop2(Instruction var1);

    void visit_dup(Instruction var1);

    void visit_dup_x1(Instruction var1);

    void visit_dup_x2(Instruction var1);

    void visit_dup2(Instruction var1);

    void visit_dup2_x1(Instruction var1);

    void visit_dup2_x2(Instruction var1);

    void visit_swap(Instruction var1);

    void visit_iadd(Instruction var1);

    void visit_ladd(Instruction var1);

    void visit_fadd(Instruction var1);

    void visit_dadd(Instruction var1);

    void visit_isub(Instruction var1);

    void visit_lsub(Instruction var1);

    void visit_fsub(Instruction var1);

    void visit_dsub(Instruction var1);

    void visit_imul(Instruction var1);

    void visit_lmul(Instruction var1);

    void visit_fmul(Instruction var1);

    void visit_dmul(Instruction var1);

    void visit_idiv(Instruction var1);

    void visit_ldiv(Instruction var1);

    void visit_fdiv(Instruction var1);

    void visit_ddiv(Instruction var1);

    void visit_irem(Instruction var1);

    void visit_lrem(Instruction var1);

    void visit_frem(Instruction var1);

    void visit_drem(Instruction var1);

    void visit_ineg(Instruction var1);

    void visit_lneg(Instruction var1);

    void visit_fneg(Instruction var1);

    void visit_dneg(Instruction var1);

    void visit_ishl(Instruction var1);

    void visit_lshl(Instruction var1);

    void visit_ishr(Instruction var1);

    void visit_lshr(Instruction var1);

    void visit_iushr(Instruction var1);

    void visit_lushr(Instruction var1);

    void visit_iand(Instruction var1);

    void visit_land(Instruction var1);

    void visit_ior(Instruction var1);

    void visit_lor(Instruction var1);

    void visit_ixor(Instruction var1);

    void visit_lxor(Instruction var1);

    void visit_iinc(Instruction var1);

    void visit_i2l(Instruction var1);

    void visit_i2f(Instruction var1);

    void visit_i2d(Instruction var1);

    void visit_l2i(Instruction var1);

    void visit_l2f(Instruction var1);

    void visit_l2d(Instruction var1);

    void visit_f2i(Instruction var1);

    void visit_f2l(Instruction var1);

    void visit_f2d(Instruction var1);

    void visit_d2i(Instruction var1);

    void visit_d2l(Instruction var1);

    void visit_d2f(Instruction var1);

    void visit_i2b(Instruction var1);

    void visit_i2c(Instruction var1);

    void visit_i2s(Instruction var1);

    void visit_lcmp(Instruction var1);

    void visit_fcmpl(Instruction var1);

    void visit_fcmpg(Instruction var1);

    void visit_dcmpl(Instruction var1);

    void visit_dcmpg(Instruction var1);

    void visit_ifeq(Instruction var1);

    void visit_ifne(Instruction var1);

    void visit_iflt(Instruction var1);

    void visit_ifge(Instruction var1);

    void visit_ifgt(Instruction var1);

    void visit_ifle(Instruction var1);

    void visit_if_icmpeq(Instruction var1);

    void visit_if_icmpne(Instruction var1);

    void visit_if_icmplt(Instruction var1);

    void visit_if_icmpge(Instruction var1);

    void visit_if_icmpgt(Instruction var1);

    void visit_if_icmple(Instruction var1);

    void visit_if_acmpeq(Instruction var1);

    void visit_if_acmpne(Instruction var1);

    void visit_goto(Instruction var1);

    void visit_jsr(Instruction var1);

    void visit_ret(Instruction var1);

    void visit_switch(Instruction var1);

    void visit_ireturn(Instruction var1);

    void visit_lreturn(Instruction var1);

    void visit_freturn(Instruction var1);

    void visit_dreturn(Instruction var1);

    void visit_areturn(Instruction var1);

    void visit_return(Instruction var1);

    void visit_getstatic(Instruction var1);

    void visit_putstatic(Instruction var1);

    void visit_putstatic_nowb(Instruction var1);

    void visit_getfield(Instruction var1);

    void visit_putfield(Instruction var1);

    void visit_putfield_nowb(Instruction var1);

    void visit_invokevirtual(Instruction var1);

    void visit_invokespecial(Instruction var1);

    void visit_invokestatic(Instruction var1);

    void visit_invokeinterface(Instruction var1);

    void visit_new(Instruction var1);

    void visit_newarray(Instruction var1);

    void visit_arraylength(Instruction var1);

    void visit_athrow(Instruction var1);

    void visit_checkcast(Instruction var1);

    void visit_instanceof(Instruction var1);

    void visit_monitorenter(Instruction var1);

    void visit_monitorexit(Instruction var1);

    void visit_multianewarray(Instruction var1);

    void visit_ifnull(Instruction var1);

    void visit_ifnonnull(Instruction var1);

    void visit_rc(Instruction var1);

    void visit_aupdate(Instruction var1);

    void visit_supdate(Instruction var1);

    void visit_aswizzle(Instruction var1);

    void visit_aswrange(Instruction var1);

    public class Instruction implements Opcode {
        private Object operand;
        private int opcode;
        private int origOpcode;
        private boolean useSlow;

        public Instruction(int opcode) {
            this(opcode, (Object)null);
        }

        public Instruction(int opcode, Object operand) {
            this.useSlow = false;
            this.opcode = opcode;
            this.origOpcode = opcode;
            this.operand = operand;
        }

        public Instruction(byte[] code, int index, int[] targets, int[] lookups, LocalVariable[] locals, ConstantPool constants) {
            int opc;
            super();
            this.useSlow = false;
            opc = toUByte(code[index]);
            int i;
            Label[] t;
            int[] v;
            label208:
            switch(opc) {
                case 1:
                    this.operand = null;
                    break;
                case 2:
                    this.operand = new Integer(-1);
                    break;
                case 3:
                    this.operand = new Integer(0);
                    break;
                case 4:
                    this.operand = new Integer(1);
                    break;
                case 5:
                    this.operand = new Integer(2);
                    break;
                case 6:
                    this.operand = new Integer(3);
                    break;
                case 7:
                    this.operand = new Integer(4);
                    break;
                case 8:
                    this.operand = new Integer(5);
                    break;
                case 9:
                    this.operand = new Long(0L);
                    break;
                case 10:
                    this.operand = new Long(1L);
                    break;
                case 11:
                    this.operand = new Float(0.0F);
                    break;
                case 12:
                    this.operand = new Float(1.0F);
                    break;
                case 13:
                    this.operand = new Float(2.0F);
                    break;
                case 14:
                    this.operand = new Double(0.0D);
                    break;
                case 15:
                    this.operand = new Double(1.0D);
                    break;
                case 16:
                    this.operand = new Integer(code[index + 1]);
                    break;
                case 17:
                    this.operand = new Integer(toShort(code[index + 1], code[index + 2]));
                    break;
                case 18:
                    i = toUByte(code[index + 1]);
                    this.operand = constants.constantAt(i);
                    break;
                case 19:
                case 20:
                    i = toUShort(code[index + 1], code[index + 2]);
                    this.operand = constants.constantAt(i);
                    break;
                case 21:
                case 22:
                case 23:
                case 24:
                case 25:
                    i = toUByte(code[index + 1]);
                    this.operand = i < locals.length && locals[i] != null ? locals[i] : new LocalVariable(i);
                    break;
                case 26:
                case 30:
                case 34:
                case 38:
                case 42:
                    this.operand = locals.length > 0 && locals[0] != null ? locals[0] : new LocalVariable(0);
                    break;
                case 27:
                case 31:
                case 35:
                case 39:
                case 43:
                    this.operand = 1 < locals.length && locals[1] != null ? locals[1] : new LocalVariable(1);
                    break;
                case 28:
                case 32:
                case 36:
                case 40:
                case 44:
                    this.operand = 2 < locals.length && locals[2] != null ? locals[2] : new LocalVariable(2);
                    break;
                case 29:
                case 33:
                case 37:
                case 41:
                case 45:
                    this.operand = 3 < locals.length && locals[3] != null ? locals[3] : new LocalVariable(3);
                case 46:
                case 47:
                case 48:
                case 49:
                case 50:
                case 51:
                case 52:
                case 53:
                case 79:
                case 80:
                case 81:
                case 82:
                case 83:
                case 84:
                case 85:
                case 86:
                case 87:
                case 88:
                case 89:
                case 90:
                case 91:
                case 92:
                case 93:
                case 94:
                case 95:
                case 96:
                case 97:
                case 98:
                case 99:
                case 100:
                case 101:
                case 102:
                case 103:
                case 104:
                case 105:
                case 106:
                case 107:
                case 108:
                case 109:
                case 110:
                case 111:
                case 112:
                case 113:
                case 114:
                case 115:
                case 116:
                case 117:
                case 118:
                case 119:
                case 120:
                case 121:
                case 122:
                case 123:
                case 124:
                case 125:
                case 126:
                case 127:
                case 128:
                case 129:
                case 130:
                case 131:
                case 133:
                case 134:
                case 135:
                case 136:
                case 137:
                case 138:
                case 139:
                case 140:
                case 141:
                case 142:
                case 143:
                case 144:
                case 145:
                case 146:
                case 147:
                case 148:
                case 149:
                case 150:
                case 151:
                case 152:
                case 172:
                case 173:
                case 174:
                case 175:
                case 176:
                case 177:
                case 186:
                case 190:
                case 191:
                case 194:
                case 195:
                case 202:
                case 203:
                case 206:
                case 207:
                case 208:
                case 209:
                case 210:
                case 211:
                case 212:
                case 213:
                case 214:
                case 215:
                case 216:
                case 217:
                case 218:
                case 219:
                case 220:
                case 221:
                case 222:
                case 223:
                case 224:
                case 225:
                case 226:
                case 227:
                case 228:
                case 229:
                case 230:
                case 231:
                case 232:
                case 233:
                case 234:
                case 235:
                case 236:
                default:
                    break;
                case 54:
                case 55:
                case 56:
                case 57:
                case 58:
                    i = toUByte(code[index + 1]);
                    this.operand = i < locals.length && locals[i] != null ? locals[i] : new LocalVariable(i);
                    break;
                case 59:
                case 63:
                case 67:
                case 71:
                case 75:
                    this.operand = locals.length > 0 && locals[0] != null ? locals[0] : new LocalVariable(0);
                    break;
                case 60:
                case 64:
                case 68:
                case 72:
                case 76:
                    this.operand = 1 < locals.length && locals[1] != null ? locals[1] : new LocalVariable(1);
                    break;
                case 61:
                case 65:
                case 69:
                case 73:
                case 77:
                    this.operand = 2 < locals.length && locals[2] != null ? locals[2] : new LocalVariable(2);
                    break;
                case 62:
                case 66:
                case 70:
                case 74:
                case 78:
                    this.operand = 3 < locals.length && locals[3] != null ? locals[3] : new LocalVariable(3);
                    break;
                case 132:
                    i = toUByte(code[index + 1]);
                    int incr = code[index + 2];
                    this.operand = new IncOperand(i < locals.length && locals[i] != null ? locals[i] : new LocalVariable(i), incr);
                    break;
                case 153:
                case 154:
                case 155:
                case 156:
                case 157:
                case 158:
                case 159:
                case 160:
                case 161:
                case 162:
                case 163:
                case 164:
                case 165:
                case 166:
                    this.operand = new Label(targets[0]);
                    break;
                case 167:
                case 168:
                case 198:
                case 199:
                case 200:
                case 201:
                    this.operand = new Label(targets[0]);
                    break;
                case 169:
                    i = toUByte(code[index + 1]);
                    this.operand = i < locals.length && locals[i] != null ? locals[i] : new LocalVariable(i);
                    break;
                case 170:
                    t = new Label[targets.length - 1];
                    v = new int[targets.length - 1];
                    i = 1;

                    for(int j = lookups[0]; i < targets.length; ++j) {
                        t[i - 1] = new Label(targets[i]);
                        v[i - 1] = j;
                        ++i;
                    }

                    this.operand = new Switch(new Label(targets[0]), t, v);
                    break;
                case 171:
                    t = new Label[targets.length - 1];
                    v = new int[targets.length - 1];

                    for(i = 1; i < targets.length; ++i) {
                        t[i - 1] = new Label(targets[i]);
                        v[i - 1] = lookups[i - 1];
                    }

                    this.operand = new Switch(new Label(targets[0]), t, v);
                    break;
                case 178:
                case 179:
                case 180:
                case 181:
                case 182:
                case 183:
                case 184:
                case 185:
                case 187:
                case 189:
                case 192:
                case 193:
                case 204:
                case 205:
                    i = toUShort(code[index + 1], code[index + 2]);
                    this.operand = constants.constantAt(i);
                    break;
                case 188:
                    int atype = code[index + 1];
                    this.operand = Type.getType(atype);
                    break;
                case 196:
                    opc = toUByte(code[index + 1]);
                    switch(opc) {
                        case 21:
                        case 22:
                        case 23:
                        case 24:
                        case 25:
                        case 54:
                        case 55:
                        case 56:
                        case 57:
                        case 58:
                        case 169:
                            i = toUShort(code[index + 2], code[index + 3]);
                            this.operand = i < locals.length && locals[i] != null ? locals[i] : new LocalVariable(i);
                            break label208;
                        case 132:
                            i = toUShort(code[index + 2], code[index + 3]);
                            int incr = toShort(code[index + 4], code[index + 5]);
                            this.operand = new IncOperand(i < locals.length && locals[i] != null ? locals[i] : new LocalVariable(i), incr);
                        default:
                            break label208;
                    }
                case 197:
                    i = toUShort(code[index + 1], code[index + 2]);
                    int dim = toUByte(code[index + 3]);
                    this.operand = new MultiArrayOperand((Type)constants.constantAt(i), dim);
                    break;
                case 237:
                    i = toUByte(code[index + 1]);
                    this.operand = new Integer(i);
                    break;
                case 238:
                    i = toUByte(code[index + 1]);
                    this.operand = new Integer(i);
                    break;
                case 239:
                    i = toUByte(code[index + 1]);
                    this.operand = new Integer(i);
            }

            this.origOpcode = opc;
            this.opcode = Opcode.opcXMap[opc];
        }

        public int origOpcode() {
            return this.origOpcode;
        }

        public void setUseSlow(boolean useSlow) {
            this.useSlow = useSlow;
        }

        public boolean useSlow() {
            return this.useSlow;
        }

        public boolean isLoad() {
            switch(this.opcode) {
                case 21:
                case 22:
                case 23:
                case 24:
                case 25:
                case 26:
                case 27:
                case 28:
                case 29:
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 35:
                case 36:
                case 37:
                case 38:
                case 39:
                case 40:
                case 41:
                case 42:
                case 43:
                case 44:
                case 45:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isStore() {
            switch(this.opcode) {
                case 54:
                case 55:
                case 56:
                case 57:
                case 58:
                case 59:
                case 60:
                case 61:
                case 62:
                case 63:
                case 64:
                case 65:
                case 66:
                case 67:
                case 68:
                case 69:
                case 70:
                case 71:
                case 72:
                case 73:
                case 74:
                case 75:
                case 76:
                case 77:
                case 78:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isInc() {
            return this.opcode == 132;
        }

        public boolean isThrow() {
            return this.opcode == 191;
        }

        public boolean isInvoke() {
            switch(this.opcode) {
                case 182:
                case 183:
                case 184:
                case 185:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isRet() {
            return this.opcode == 169;
        }

        public boolean isReturn() {
            switch(this.opcode) {
                case 172:
                case 173:
                case 174:
                case 175:
                case 176:
                case 177:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isSwitch() {
            return this.opcodeClass() == 170;
        }

        public boolean isJump() {
            return this.isConditionalJump() || this.isGoto();
        }

        public boolean isJsr() {
            return this.opcodeClass() == 168;
        }

        public boolean isGoto() {
            return this.opcodeClass() == 167;
        }

        public boolean isConditionalJump() {
            switch(this.opcode) {
                case 153:
                case 154:
                case 155:
                case 156:
                case 157:
                case 158:
                case 159:
                case 160:
                case 161:
                case 162:
                case 163:
                case 164:
                case 165:
                case 166:
                case 198:
                case 199:
                    return true;
                default:
                    return false;
            }
        }

        public int opcodeClass() {
            return this.opcode;
        }

        public void setOpcodeClass(int opcode) {
            this.opcode = opcode;
        }

        public void setOperand(Object operand) {
            this.operand = operand;
        }

        public Object operand() {
            return this.operand;
        }

        public String toString() {
            if (this.operand == null && this.opcodeClass() != 18) {
                return Opcode.opcNames[this.opcode];
            } else if (this.operand instanceof Float) {
                return Opcode.opcNames[this.opcode] + " " + this.operand + "F";
            } else if (this.operand instanceof Long) {
                return Opcode.opcNames[this.opcode] + " " + this.operand + "L";
            } else if (!(this.operand instanceof String)) {
                return Opcode.opcNames[this.opcode] + " " + this.operand;
            } else {
                StringBuffer sb = new StringBuffer();
                String s = (String)this.operand;

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

                return Opcode.opcNames[this.opcode] + " \"" + sb.toString() + "\"";
            }
        }

        protected static int toUShort(byte b1, byte b2) {
            int x = (short)(toUByte(b1) << 8) | toUByte(b2);
            if (x < 0) {
                x += 65536;
            }

            return x;
        }

        protected static short toShort(byte b1, byte b2) {
            return (short)(toUByte(b1) << 8 | toUByte(b2));
        }

        protected static int toInt(byte b1, byte b2, byte b3, byte b4) {
            return toUByte(b1) << 24 | toUByte(b2) << 16 | toUByte(b3) << 8 | toUByte(b4);
        }

        protected static int toUByte(byte b) {
            return b < 0 ? b + 256 : b;
        }

        public int category() {
            switch(this.opcode) {
                case 18:
                    if (!(this.operand instanceof Long) && !(this.operand instanceof Double)) {
                        return 1;
                    }

                    return 2;
                case 22:
                case 24:
                case 47:
                case 49:
                case 55:
                case 57:
                case 80:
                case 82:
                case 97:
                case 99:
                case 101:
                case 103:
                case 105:
                case 107:
                case 109:
                case 111:
                case 113:
                case 115:
                case 117:
                case 119:
                case 121:
                case 123:
                case 125:
                case 127:
                case 129:
                case 131:
                case 133:
                case 135:
                case 138:
                case 140:
                case 141:
                case 143:
                    return 2;
                case 182:
                case 183:
                case 184:
                case 185:
                    MemberRef callee = (MemberRef)this.operand;
                    if (callee.nameAndType().type().returnType().isWide()) {
                        return 2;
                    }

                    return 1;
                default:
                    return 1;
            }
        }

        public void visit(InstructionVisitor visitor) {
            switch(this.opcodeClass()) {
                case 0:
                    visitor.visit_nop(this);
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                case 16:
                case 17:
                case 19:
                case 20:
                case 26:
                case 27:
                case 28:
                case 29:
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 35:
                case 36:
                case 37:
                case 38:
                case 39:
                case 40:
                case 41:
                case 42:
                case 43:
                case 44:
                case 45:
                case 59:
                case 60:
                case 61:
                case 62:
                case 63:
                case 64:
                case 65:
                case 66:
                case 67:
                case 68:
                case 69:
                case 70:
                case 71:
                case 72:
                case 73:
                case 74:
                case 75:
                case 76:
                case 77:
                case 78:
                case 171:
                case 186:
                case 189:
                case 196:
                case 200:
                case 201:
                case 202:
                case 203:
                case 206:
                case 207:
                case 208:
                case 209:
                case 210:
                case 211:
                case 212:
                case 213:
                case 214:
                case 215:
                case 216:
                case 217:
                case 218:
                case 219:
                case 220:
                case 221:
                case 222:
                case 223:
                case 224:
                case 225:
                case 226:
                case 227:
                case 228:
                case 229:
                case 230:
                case 231:
                case 232:
                case 233:
                case 234:
                case 235:
                case 236:
                default:
                    break;
                case 18:
                    visitor.visit_ldc(this);
                    break;
                case 21:
                    visitor.visit_iload(this);
                    break;
                case 22:
                    visitor.visit_lload(this);
                    break;
                case 23:
                    visitor.visit_fload(this);
                    break;
                case 24:
                    visitor.visit_dload(this);
                    break;
                case 25:
                    visitor.visit_aload(this);
                    break;
                case 46:
                    visitor.visit_iaload(this);
                    break;
                case 47:
                    visitor.visit_laload(this);
                    break;
                case 48:
                    visitor.visit_faload(this);
                    break;
                case 49:
                    visitor.visit_daload(this);
                    break;
                case 50:
                    visitor.visit_aaload(this);
                    break;
                case 51:
                    visitor.visit_baload(this);
                    break;
                case 52:
                    visitor.visit_caload(this);
                    break;
                case 53:
                    visitor.visit_saload(this);
                    break;
                case 54:
                    visitor.visit_istore(this);
                    break;
                case 55:
                    visitor.visit_lstore(this);
                    break;
                case 56:
                    visitor.visit_fstore(this);
                    break;
                case 57:
                    visitor.visit_dstore(this);
                    break;
                case 58:
                    visitor.visit_astore(this);
                    break;
                case 79:
                    visitor.visit_iastore(this);
                    break;
                case 80:
                    visitor.visit_lastore(this);
                    break;
                case 81:
                    visitor.visit_fastore(this);
                    break;
                case 82:
                    visitor.visit_dastore(this);
                    break;
                case 83:
                    visitor.visit_aastore(this);
                    break;
                case 84:
                    visitor.visit_bastore(this);
                    break;
                case 85:
                    visitor.visit_castore(this);
                    break;
                case 86:
                    visitor.visit_sastore(this);
                    break;
                case 87:
                    visitor.visit_pop(this);
                    break;
                case 88:
                    visitor.visit_pop2(this);
                    break;
                case 89:
                    visitor.visit_dup(this);
                    break;
                case 90:
                    visitor.visit_dup_x1(this);
                    break;
                case 91:
                    visitor.visit_dup_x2(this);
                    break;
                case 92:
                    visitor.visit_dup2(this);
                    break;
                case 93:
                    visitor.visit_dup2_x1(this);
                    break;
                case 94:
                    visitor.visit_dup2_x2(this);
                    break;
                case 95:
                    visitor.visit_swap(this);
                    break;
                case 96:
                    visitor.visit_iadd(this);
                    break;
                case 97:
                    visitor.visit_ladd(this);
                    break;
                case 98:
                    visitor.visit_fadd(this);
                    break;
                case 99:
                    visitor.visit_dadd(this);
                    break;
                case 100:
                    visitor.visit_isub(this);
                    break;
                case 101:
                    visitor.visit_lsub(this);
                    break;
                case 102:
                    visitor.visit_fsub(this);
                    break;
                case 103:
                    visitor.visit_dsub(this);
                    break;
                case 104:
                    visitor.visit_imul(this);
                    break;
                case 105:
                    visitor.visit_lmul(this);
                    break;
                case 106:
                    visitor.visit_fmul(this);
                    break;
                case 107:
                    visitor.visit_dmul(this);
                    break;
                case 108:
                    visitor.visit_idiv(this);
                    break;
                case 109:
                    visitor.visit_ldiv(this);
                    break;
                case 110:
                    visitor.visit_fdiv(this);
                    break;
                case 111:
                    visitor.visit_ddiv(this);
                    break;
                case 112:
                    visitor.visit_irem(this);
                    break;
                case 113:
                    visitor.visit_lrem(this);
                    break;
                case 114:
                    visitor.visit_frem(this);
                    break;
                case 115:
                    visitor.visit_drem(this);
                    break;
                case 116:
                    visitor.visit_ineg(this);
                    break;
                case 117:
                    visitor.visit_lneg(this);
                    break;
                case 118:
                    visitor.visit_fneg(this);
                    break;
                case 119:
                    visitor.visit_dneg(this);
                    break;
                case 120:
                    visitor.visit_ishl(this);
                    break;
                case 121:
                    visitor.visit_lshl(this);
                    break;
                case 122:
                    visitor.visit_ishr(this);
                    break;
                case 123:
                    visitor.visit_lshr(this);
                    break;
                case 124:
                    visitor.visit_iushr(this);
                    break;
                case 125:
                    visitor.visit_lushr(this);
                    break;
                case 126:
                    visitor.visit_iand(this);
                    break;
                case 127:
                    visitor.visit_land(this);
                    break;
                case 128:
                    visitor.visit_ior(this);
                    break;
                case 129:
                    visitor.visit_lor(this);
                    break;
                case 130:
                    visitor.visit_ixor(this);
                    break;
                case 131:
                    visitor.visit_lxor(this);
                    break;
                case 132:
                    visitor.visit_iinc(this);
                    break;
                case 133:
                    visitor.visit_i2l(this);
                    break;
                case 134:
                    visitor.visit_i2f(this);
                    break;
                case 135:
                    visitor.visit_i2d(this);
                    break;
                case 136:
                    visitor.visit_l2i(this);
                    break;
                case 137:
                    visitor.visit_l2f(this);
                    break;
                case 138:
                    visitor.visit_l2d(this);
                    break;
                case 139:
                    visitor.visit_f2i(this);
                    break;
                case 140:
                    visitor.visit_f2l(this);
                    break;
                case 141:
                    visitor.visit_f2d(this);
                    break;
                case 142:
                    visitor.visit_d2i(this);
                    break;
                case 143:
                    visitor.visit_d2l(this);
                    break;
                case 144:
                    visitor.visit_d2f(this);
                    break;
                case 145:
                    visitor.visit_i2b(this);
                    break;
                case 146:
                    visitor.visit_i2c(this);
                    break;
                case 147:
                    visitor.visit_i2s(this);
                    break;
                case 148:
                    visitor.visit_lcmp(this);
                    break;
                case 149:
                    visitor.visit_fcmpl(this);
                    break;
                case 150:
                    visitor.visit_fcmpg(this);
                    break;
                case 151:
                    visitor.visit_dcmpl(this);
                    break;
                case 152:
                    visitor.visit_dcmpg(this);
                    break;
                case 153:
                    visitor.visit_ifeq(this);
                    break;
                case 154:
                    visitor.visit_ifne(this);
                    break;
                case 155:
                    visitor.visit_iflt(this);
                    break;
                case 156:
                    visitor.visit_ifge(this);
                    break;
                case 157:
                    visitor.visit_ifgt(this);
                    break;
                case 158:
                    visitor.visit_ifle(this);
                    break;
                case 159:
                    visitor.visit_if_icmpeq(this);
                    break;
                case 160:
                    visitor.visit_if_icmpne(this);
                    break;
                case 161:
                    visitor.visit_if_icmplt(this);
                    break;
                case 162:
                    visitor.visit_if_icmpge(this);
                    break;
                case 163:
                    visitor.visit_if_icmpgt(this);
                    break;
                case 164:
                    visitor.visit_if_icmple(this);
                    break;
                case 165:
                    visitor.visit_if_acmpeq(this);
                    break;
                case 166:
                    visitor.visit_if_acmpne(this);
                    break;
                case 167:
                    visitor.visit_goto(this);
                    break;
                case 168:
                    visitor.visit_jsr(this);
                    break;
                case 169:
                    visitor.visit_ret(this);
                    break;
                case 170:
                    visitor.visit_switch(this);
                    break;
                case 172:
                    visitor.visit_ireturn(this);
                    break;
                case 173:
                    visitor.visit_lreturn(this);
                    break;
                case 174:
                    visitor.visit_freturn(this);
                    break;
                case 175:
                    visitor.visit_dreturn(this);
                    break;
                case 176:
                    visitor.visit_areturn(this);
                    break;
                case 177:
                    visitor.visit_return(this);
                    break;
                case 178:
                    visitor.visit_getstatic(this);
                    break;
                case 179:
                    visitor.visit_putstatic(this);
                    break;
                case 180:
                    visitor.visit_getfield(this);
                    break;
                case 181:
                    visitor.visit_putfield(this);
                    break;
                case 182:
                    visitor.visit_invokevirtual(this);
                    break;
                case 183:
                    visitor.visit_invokespecial(this);
                    break;
                case 184:
                    visitor.visit_invokestatic(this);
                    break;
                case 185:
                    visitor.visit_invokeinterface(this);
                    break;
                case 187:
                    visitor.visit_new(this);
                    break;
                case 188:
                    visitor.visit_newarray(this);
                    break;
                case 190:
                    visitor.visit_arraylength(this);
                    break;
                case 191:
                    visitor.visit_athrow(this);
                    break;
                case 192:
                    visitor.visit_checkcast(this);
                    break;
                case 193:
                    visitor.visit_instanceof(this);
                    break;
                case 194:
                    visitor.visit_monitorenter(this);
                    break;
                case 195:
                    visitor.visit_monitorexit(this);
                    break;
                case 197:
                    visitor.visit_multianewarray(this);
                    break;
                case 198:
                    visitor.visit_ifnull(this);
                    break;
                case 199:
                    visitor.visit_ifnonnull(this);
                    break;
                case 204:
                    visitor.visit_putfield_nowb(this);
                    break;
                case 205:
                    visitor.visit_putstatic_nowb(this);
                    break;
                case 237:
                    visitor.visit_rc(this);
                    break;
                case 238:
                    visitor.visit_aupdate(this);
                    break;
                case 239:
                    visitor.visit_supdate(this);
                    break;
                case 240:
                    visitor.visit_aswizzle(this);
                    break;
                case 241:
                    visitor.visit_aswrange(this);
            }

        }
    }
    public class ConstantPool {
        private Map constantIndices = new HashMap();
        ResizeableArrayList constants;
        ResizeableArrayList resolved;

        public ConstantPool(ClassInfo.Constant[] c) {
            this.constants = new ResizeableArrayList(c.length);
            this.resolved = new ResizeableArrayList(c.length);

            for(int i = 0; i < c.length; ++i) {
                this.constants.add(c[i]);
                this.resolved.add((Object)null);
                if (c[i] != null) {
                    this.constantIndices.put(c[i], new Integer(i));
                }
            }

        }

        public ConstantPool() {
            this.constants = new ResizeableArrayList();
            this.resolved = new ResizeableArrayList();
        }

        public Object constantAt(int idx) {
            if (idx == 0) {
                return null;
            } else {
                Object value = this.resolved.get(idx);
                if (value != null) {
                    return value;
                } else {
                    ClassInfo.Constant c = (ClassInfo.Constant)this.constants.get(idx);
                    if (c == null) {
                        return null;
                    } else {
                        value = c.value();
                        if (value == null) {
                            return null;
                        } else {
                            int[] v;
                            String name;
                            int index;
                            switch(c.tag()) {
                                case 7:
                                    index = (Integer)value;
                                    name = (String)this.constantAt(index);
                                    value = Type.getType(Type.classDescriptor(name));
                                    break;
                                case 8:
                                    index = (Integer)value;
                                    value = this.constantAt(index);
                                    break;
                                case 9:
                                case 10:
                                case 11:
                                    v = (int[])value;
                                    Type clazz = (Type)this.constantAt(v[0]);
                                    NameAndType nameAndType = (NameAndType)this.constantAt(v[1]);
                                    value = new MemberRef(clazz, nameAndType);
                                    break;
                                case 12:
                                    v = (int[])value;
                                    name = (String)this.constantAt(v[0]);
                                    String type = (String)this.constantAt(v[1]);
                                    value = new NameAndType(name, Type.getType(type));
                            }

                            this.resolved.ensureSize(idx + 1);
                            this.resolved.set(idx, value);
                            return value;
                        }
                    }
                }
            }
        }

        public int numConstants() {
            return this.constants.size();
        }

        public int constantTag(int index) {
            if (index > 0 && index < this.constants.size()) {
                ClassInfo.Constant c = (ClassInfo.Constant)this.constants.get(index);
                if (c != null) {
                    return c.tag();
                }
            }

            return 1;
        }

        public int constantIndex(int tag, Object value) {
            return this.addConstant(tag, value);
        }

        public int getClassIndex(Class c) {
            return this.addConstant(7, Type.getType(c));
        }

        public int getIntegerIndex(Integer i) {
            return this.addConstant(3, i);
        }

        public int getFloatIndex(Float f) {
            return this.addConstant(4, f);
        }

        public int getLongIndex(Long l) {
            return this.addConstant(5, l);
        }

        public int getDoubleIndex(Double d) {
            return this.addConstant(6, d);
        }

        public int getClassIndex(Type type) {
            this.getTypeIndex(type);
            return this.addConstant(7, type);
        }
        public int getTypeIndex(Type type) {
            return this.addConstant(1, type.descriptor());
        }

        public int getStringIndex(String s) {
            return this.addConstant(8, s);
        }

        public int getMemberRefIndex(MemberRef ref) {
            return this.addConstant(9, ref);
        }

        public int getNameAndTypeIndex(NameAndType nat) {
            return this.addConstant(12, nat);
        }

        public int getUTF8Index(String s) {
            return this.addConstant(1, s);
        }

        public int addConstant(int tag, Object value) {
            if (value == null) {
                return 0;
            } else {
                ClassInfo.Constant c;
                int[] v;
                int index;
                switch(tag) {
                    case 1:
                        String s = (String)value;
                        c = new ClassInfo.Constant(tag, s.intern());
                        break;
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    default:
                        c = new ClassInfo.Constant(tag, value);
                        break;
                    case 7:
                        index = this.addConstant(1, ((Type)value).className());
                        c = new ClassInfo.Constant(7, new Integer(index));
                        break;
                    case 8:
                        index = this.addConstant(1, value);
                        c = new ClassInfo.Constant(8, new Integer(index));
                        break;
                    case 9:
                    case 10:
                    case 11:
                        v = new int[]{this.addConstant(7, ((MemberRef)value).declaringClass()), this.addConstant(12, ((MemberRef)value).nameAndType())};
                        c = new ClassInfo.Constant(tag, v);
                        break;
                    case 12:
                        v = new int[]{this.addConstant(1, ((NameAndType)value).name()), this.addConstant(1, ((NameAndType)value).type().descriptor())};
                        c = new ClassInfo.Constant(tag, v);
                }

                Integer index = (Integer)this.constantIndices.get(c);
                if (index == null) {
                    index = new Integer(this.constants.size());
                    this.constantIndices.put(c, index);
                    this.constants.add(c);
                    this.resolved.add(value);
                    if (tag == 5 || tag == 6) {
                        this.constants.add((Object)null);
                        this.resolved.add((Object)null);
                    }
                }

                return index;
            }
        }

        public ClassInfo.Constant[] constants() {
            Object[] a = this.constants.toArray();
            ClassInfo.Constant[] array = new ClassInfo.Constant[a.length];
            System.arraycopy(a, 0, array, 0, a.length);
            return array;
        }

        public List getConstantsList() {
            return Collections.unmodifiableList(this.constants);
        }
    }
    public class IncOperand {
        private LocalVariable var;
        private int incr;

        public IncOperand(LocalVariable var, int incr) {
            this.var = var;
            this.incr = incr;
        }

        public LocalVariable var() {
            return this.var;
        }

        public int incr() {
            return this.incr;
        }

        public String toString() {
            return this.var + " by " + this.incr;
        }
    }
    public class MultiArrayOperand {
        private Type type;
        private int dim;

        public MultiArrayOperand(Type type, int dim) {
            this.type = type;
            this.dim = dim;
        }

        public Type type() {
            return this.type;
        }

        public int dimensions() {
            return this.dim;
        }

        public String toString() {
            return this.type + " x " + this.dim;
        }
    }



}

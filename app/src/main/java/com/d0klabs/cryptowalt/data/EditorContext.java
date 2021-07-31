package com.d0klabs.cryptowalt.data;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public interface EditorContext {
    ClassInfo loadClass(String var1) throws ClassNotFoundException;

    ClassInfo newClassInfo(int var1, int var2, int var3, int[] var4, List var5);

    ClassHierarchy getHierarchy();

    ClassEditor newClass(int var1, String var2, Type var3, Type[] var4);

    ClassEditor editClass(String var1) throws ClassNotFoundException, ClassFormatException;

    ClassEditor editClass(Type var1) throws ClassNotFoundException, ClassFormatException;

    ClassEditor editClass(ClassInfo var1);

    FieldEditor editField(FieldInfo var1);

    FieldEditor editField(MemberRef var1) throws NoSuchFieldException;

    MethodEditor editMethod(MethodInfo var1);

    MethodEditor editMethod(MemberRef var1) throws NoSuchMethodException;

    void release(MethodInfo var1);

    void release(FieldInfo var1);

    void release(ClassInfo var1);

    void commit(ClassInfo var1);

    void commit(MethodInfo var1);

    void commit(FieldInfo var1);

    void commit();
    class ClassHierarchy {
        public static final Type POS_SHORT = Type.getType("L+short!;");
        public static final Type POS_BYTE = Type.getType("L+byte!;");
        static final int MAX_INT = 8;
        static final int MAX_SHORT = 7;
        static final int MAX_CHAR = 6;
        static final int MAX_BYTE = 5;
        static final int MAX_BOOL = 4;
        static final int MIN_CHAR = 3;
        static final int MIN_BOOL = 3;
        static final int ZERO = 3;
        static final int MIN_BYTE = 2;
        static final int MIN_SHORT = 1;
        static final int MIN_INT = 0;
        public static boolean DEBUG = false;
        public static boolean RELAX = false;
        Set classes;
        Graph extendsGraph;
        Graph implementsGraph;
        boolean closure;
        private Map resolvesToCache;
        EditorContext context;
        LinkedList worklist;
        Set inWorklist;

        private void db(String s) {
            if (DEBUG) {
                System.out.println(s);
            }

        }

        public ClassHierarchy(EditorContext context, Collection initial, boolean closure) {
            this.context = context;
            this.closure = closure;
            this.classes = new HashSet();
            this.extendsGraph = new Graph();
            this.implementsGraph = new Graph();
            this.worklist = new LinkedList();
            this.inWorklist = new HashSet();
            this.resolvesToCache = new HashMap();
            Iterator iter = (new ArrayList(initial)).iterator();

            while(iter.hasNext()) {
                String name = (String)iter.next();
                this.addClass(name);
            }

        }

        public void addClassNamed(String name) {
            this.addClass(name);
        }

        public Collection subclasses(Type type) {
            ClassHierarchy.TypeNode node = this.getExtendsNode(type);
            if (node == null) {
                return new ArrayList();
            } else {
                List list = new ArrayList(this.extendsGraph.preds(node));
                ListIterator iter = list.listIterator();

                while(iter.hasNext()) {
                    ClassHierarchy.TypeNode v = (ClassHierarchy.TypeNode)iter.next();
                    iter.set(v.type);
                }

                return list;
            }
        }

        public Type superclass(Type type) {
            ClassHierarchy.TypeNode node = this.getExtendsNode(type);
            if (node != null) {
                Collection succs = this.extendsGraph.succs(node);
                Iterator iter = succs.iterator();
                if (iter.hasNext()) {
                    ClassHierarchy.TypeNode v = (ClassHierarchy.TypeNode)iter.next();
                    return v.type;
                }
            }

            return null;
        }

        public Collection interfaces(Type type) {
            ClassHierarchy.TypeNode node = this.getImplementsNode(type);
            if (node == null) {
                return new ArrayList();
            } else {
                List list = new ArrayList(this.implementsGraph.succs(node));
                ListIterator iter = list.listIterator();

                while(iter.hasNext()) {
                    ClassHierarchy.TypeNode v = (ClassHierarchy.TypeNode)iter.next();
                    iter.set(v.type);
                }

                return list;
            }
        }

        public Collection implementors(Type type) {
            ClassHierarchy.TypeNode node = this.getImplementsNode(type);
            if (node == null) {
                return new ArrayList();
            } else {
                List list = new ArrayList(this.implementsGraph.preds(node));
                ListIterator iter = list.listIterator();

                while(iter.hasNext()) {
                    ClassHierarchy.TypeNode v = (ClassHierarchy.TypeNode)iter.next();
                    iter.set(v.type);
                }

                return list;
            }
        }

        public boolean subclassOf(Type a, Type b) {
            if (a.equals(b)) {
                return true;
            } else if (b.equals(Type.OBJECT)) {
                return true;
            } else if (b.isNull()) {
                return a.isNull();
            } else if (a.isArray()) {
                if (b.isArray()) {
                    return a.elementType().isReference() && b.elementType().isReference() ? this.subclassOf(a.elementType(), b.elementType()) : a.elementType().equals(b.elementType());
                } else {
                    return false;
                }
            } else if (b.isArray()) {
                return false;
            } else {
                for(Type t = a; t != null; t = this.superclass(t)) {
                    if (t.equals(b)) {
                        return true;
                    }
                }

                return false;
            }
        }

        public Collection classes() {
            return this.classes;
        }

        public boolean closure() {
            return this.closure;
        }

        private ClassHierarchy.TypeNode getExtendsNode(Type type) {
            GraphNode node = this.extendsGraph.getNode(type);
            if (node == null && type.isObject()) {
                this.addClassNamed(type.className());
            }

            return (ClassHierarchy.TypeNode)this.extendsGraph.getNode(type);
        }

        private ClassHierarchy.TypeNode getImplementsNode(Type type) {
            GraphNode node = this.implementsGraph.getNode(type);
            if (node == null && type.isObject()) {
                this.addClassNamed(type.className());
            }

            return (ClassHierarchy.TypeNode)this.implementsGraph.getNode(type);
        }

        private void addClass(String name) {
            Type type = Type.getType(Type.classDescriptor(name));
            if (!this.classes.contains(type)) {
                if (!this.inWorklist.contains(type)) {
                    this.db("ClassHierarchy: Adding " + name + " to hierarchy");
                    this.worklist.add(type);
                    this.inWorklist.add(type);

                    while(true) {
                        while(true) {
                            ClassHierarchy.TypeNode extendsNode;
                            ClassEditor c;
                            while(true) {
                                do {
                                    if (this.worklist.isEmpty()) {
                                        return;
                                    }

                                    type = (Type)this.worklist.removeFirst();
                                    this.inWorklist.remove(type);
                                } while(this.classes.contains(type));

                                this.classes.add(type);
                                extendsNode = this.getExtendsNode(type);
                                if (extendsNode == null) {
                                    extendsNode = new ClassHierarchy.TypeNode(type);
                                    this.extendsGraph.addNode(type, extendsNode);
                                }

                                try {
                                    c = this.context.editClass(type.className());
                                    break;
                                } catch (ClassNotFoundException var11) {
                                    if (!RELAX) {
                                        throw new RuntimeException("Class not found: " + var11.getMessage());
                                    }
                                }
                            }

                            Type[] interfaces = c.interfaces();
                            ClassHierarchy.TypeNode implementsNode;
                            if (c.superclass() != null) {
                                if (!c.isInterface() || interfaces.length == 0) {
                                    implementsNode = this.getExtendsNode(c.superclass());
                                    if (implementsNode == null) {
                                        implementsNode = new ClassHierarchy.TypeNode(c.superclass());
                                        this.extendsGraph.addNode(c.superclass(), implementsNode);
                                    }

                                    if (!extendsNode.type.equals(Type.OBJECT)) {
                                        this.extendsGraph.addEdge(extendsNode, implementsNode);
                                    }
                                }
                            } else if (!type.equals(Type.OBJECT) && !RELAX) {
                                throw new RuntimeException("Null superclass for " + type);
                            }

                            int tag;
                            Type t;
                            int i;
                            if (c.isInterface()) {
                                for(i = 0; i < interfaces.length; ++i) {
                                    Type iType = interfaces[i];
                                    ClassHierarchy.TypeNode iNode = this.getExtendsNode(iType);
                                    if (iNode == null) {
                                        iNode = new ClassHierarchy.TypeNode(iType);
                                        this.extendsGraph.addNode(iType, iNode);
                                    }

                                    this.extendsGraph.addEdge(extendsNode, iNode);
                                }
                            } else {
                                implementsNode = null;
                                if (interfaces.length > 0) {
                                    implementsNode = this.getImplementsNode(type);
                                    if (implementsNode == null) {
                                        implementsNode = new ClassHierarchy.TypeNode(type);
                                        this.implementsGraph.addNode(type, implementsNode);
                                    }
                                }

                                for(tag = 0; tag < interfaces.length; ++tag) {
                                    t = interfaces[tag];
                                    ClassHierarchy.TypeNode iNode = this.getImplementsNode(t);
                                    if (iNode == null) {
                                        iNode = new ClassHierarchy.TypeNode(t);
                                        this.implementsGraph.addNode(t, iNode);
                                    }

                                    this.implementsGraph.addEdge(implementsNode, iNode);
                                }
                            }

                            if (c.superclass() != null) {
                                this.addType(c.superclass());
                            }

                            for(i = 0; i < c.interfaces().length; ++i) {
                                this.addType(c.interfaces()[i]);
                            }

                            if (!this.closure) {
                                this.context.release(c.classInfo());
                            } else {
                                int typeIndex;
                                String desc;
                                for(i = 0; i < c.methods().length; ++i) {
                                    MethodInfo m = c.methods()[i];
                                    typeIndex = m.typeIndex();
                                    desc = (String)c.constants().constantAt(typeIndex);
                                    t = Type.getType(desc);
                                    this.addType(t);
                                }

                                for(i = 0; i < c.fields().length; ++i) {
                                    FieldInfo f = c.fields()[i];
                                    typeIndex = f.typeIndex();
                                    desc = (String)c.constants().constantAt(typeIndex);
                                    t = Type.getType(desc);
                                    this.addType(t);
                                }

                                for(i = 0; i < c.constants().numConstants(); ++i) {
                                    tag = c.constants().constantTag(i);
                                    if (tag != 5 && tag != 6) {
                                        if (tag == 7) {
                                            t = (Type)c.constants().constantAt(i);
                                            this.addType(t);
                                        } else if (tag == 12) {
                                            NameAndType p = (NameAndType)c.constants().constantAt(i);
                                            this.addType(p.type());
                                        }
                                    } else {
                                        ++i;
                                    }
                                }

                                this.context.release(c.classInfo());
                            }
                        }
                    }
                }
            }
        }

        private void addType(Type type) {
            if (!type.isMethod()) {
                if (type.isArray()) {
                    this.addType(type.elementType());
                } else if (type.isObject()) {
                    if (!this.classes.contains(type)) {
                        if (!this.inWorklist.contains(type)) {
                            this.worklist.add(type);
                            this.inWorklist.add(type);
                        }
                    }
                }
            } else {
                Type[] paramTypes = type.paramTypes();

                for(int i = 0; i < paramTypes.length; ++i) {
                    this.addType(paramTypes[i]);
                }

                Type returnType = type.returnType();
                this.addType(returnType);
            }
        }

        public Type intersectType(Type a, Type b) {
            if (a.equals(b)) {
                return a;
            } else if (!a.isNull() && !b.isNull()) {
                if (a.equals(Type.OBJECT)) {
                    return b;
                } else if (b.equals(Type.OBJECT)) {
                    return a;
                } else {
                    Type t;
                    if (a.isArray()) {
                        if (b.isArray()) {
                            if (a.elementType().isReference() && b.elementType().isReference()) {
                                t = this.intersectType(a.elementType(), b.elementType());
                                return t.isNull() ? Type.NULL : t.arrayType();
                            } else {
                                return !a.elementType().isReference() && !b.elementType().isReference() ? Type.NULL : Type.NULL;
                            }
                        } else {
                            return Type.NULL;
                        }
                    } else if (b.isArray()) {
                        return Type.NULL;
                    } else {
                        for(t = a; t != null; t = this.superclass(t)) {
                            if (t.equals(b)) {
                                return a;
                            }
                        }

                        for(t = b; t != null; t = this.superclass(t)) {
                            if (t.equals(a)) {
                                return b;
                            }
                        }

                        return Type.NULL;
                    }
                }
            } else {
                return Type.NULL;
            }
        }

        public Type unionTypes(Collection types) {
            if (types.size() <= 0) {
                return Type.OBJECT;
            } else {
                Iterator ts = types.iterator();

                Type type;
                for(type = (Type)ts.next(); ts.hasNext(); type = this.unionType(type, (Type)ts.next())) {
                }

                return type;
            }
        }

        public Type unionType(Type a, Type b) {
            if (a.equals(b)) {
                return a;
            } else if (!a.equals(Type.OBJECT) && !b.equals(Type.OBJECT)) {
                if (a.isNull()) {
                    return b;
                } else if (b.isNull()) {
                    return a;
                } else if (!a.isIntegral() && !a.equals(POS_BYTE) && !a.equals(POS_SHORT) || !b.isIntegral() && !b.equals(POS_BYTE) && !b.equals(POS_SHORT)) {
                    if (a.isArray()) {
                        if (b.isArray()) {
                            if (a.elementType().isReference() && b.elementType().isReference()) {
                                Type t = this.unionType(a.elementType(), b.elementType());
                                return t.arrayType();
                            } else {
                                return !a.elementType().isReference() && !b.elementType().isReference() ? Type.OBJECT : Type.OBJECT;
                            }
                        } else {
                            return Type.OBJECT;
                        }
                    } else if (b.isArray()) {
                        return Type.OBJECT;
                    } else {
                        Set superOfA = new HashSet();
                        Set superOfB = new HashSet();

                        Type t;
                        for(t = a; t != null; t = this.superclass(t)) {
                            superOfA.add(t);
                        }

                        for(t = b; t != null; t = this.superclass(t)) {
                            if (superOfA.contains(t)) {
                                return t;
                            }

                            superOfB.add(t);
                        }

                        for(t = a; t != null; t = this.superclass(t)) {
                            if (superOfB.contains(t)) {
                                return t;
                            }
                        }

                        throw new RuntimeException("No common super type for " + a + " (" + superOfA + ")" + " and " + b + " (" + superOfB + ")");
                    }
                } else {
                    BitSet v1 = typeToSet(a);
                    BitSet v2 = typeToSet(b);
                    v1.or(v2);
                    return setToType(v1);
                }
            } else {
                return Type.OBJECT;
            }
        }

        public void printClasses(PrintWriter out, int indent) {
            ClassHierarchy.TypeNode objectNode = this.getExtendsNode(Type.OBJECT);
            this.indent(out, indent);
            out.println(objectNode.type);
            this.printSubclasses(objectNode.type, out, true, indent + 2);
        }

        public void printImplements(PrintWriter out, int indent) {
            indent += 2;
            Iterator roots = this.implementsGraph.roots().iterator();

            while(roots.hasNext()) {
                ClassHierarchy.TypeNode iNode = (ClassHierarchy.TypeNode)roots.next();
                this.indent(out, indent);
                out.println(iNode.type);
                this.printImplementors(iNode.type, out, true, indent + 2);
            }

        }

        private void printImplementors(Type iType, PrintWriter out, boolean recurse, int indent) {
            Iterator implementors = this.implementors(iType).iterator();

            while(implementors.hasNext()) {
                Type implementor = (Type)implementors.next();
                this.indent(out, indent);
                out.println(implementor);
                if (recurse) {
                    this.printImplementors(implementor, out, recurse, indent + 2);
                }
            }

        }

        private void indent(PrintWriter out, int indent) {
            for(int i = 0; i < indent; ++i) {
                out.print(" ");
            }

        }

        private void printSubclasses(Type classType, PrintWriter out, boolean recurse, int indent) {
            Iterator iter = this.subclasses(classType).iterator();

            while(iter.hasNext()) {
                Type subclass = (Type)iter.next();
                this.indent(out, indent);
                out.println(subclass);
                if (recurse) {
                    this.printSubclasses(subclass, out, recurse, indent + 2);
                }
            }

        }

        public boolean methodIsOverridden(Type classType, NameAndType nat) {
            String methodName = nat.name();
            Type methodType = nat.type();
            this.db("ClassHierarchy: Is " + classType + "." + methodName + methodType + " overridden?");
            Collection subclasses = this.subclasses(classType);
            Iterator iter = subclasses.iterator();

            while(iter.hasNext()) {
                Type subclass = (Type)iter.next();
                this.db("Examining subclass " + subclass);
                ClassEditor ce = null;

                try {
                    ce = this.context.editClass(subclass.className());
                } catch (ClassNotFoundException var12) {
                    this.db(var12.getMessage());
                    return true;
                }

                MethodInfo[] methods = ce.methods();

                for(int i = 0; i < methods.length; ++i) {
                    MethodEditor me = this.context.editMethod(methods[i]);
                    if (me.name().equals(methodName) && me.type().equals(methodType)) {
                        this.db("  " + methodName + methodType + " is overridden by " + me.name() + me.type());
                        this.context.release(ce.classInfo());
                        return true;
                    }
                }

                if (this.methodIsOverridden(subclass, nat)) {
                    this.context.release(ce.classInfo());
                    return true;
                }

                this.context.release(ce.classInfo());
            }

            this.db("  NO!");
            return false;
        }

        public MemberRef methodInvoked(Type receiver, NameAndType method) {
            Type type = receiver;

            while(type != null) {
                MemberRef m = new MemberRef(type, method);

                try {
                    this.context.editMethod(m);
                    return m;
                } catch (NoSuchMethodException var6) {
                    type = this.superclass(type);
                }
            }

            throw new IllegalArgumentException("No implementation of " + receiver + "." + method);
        }

        public static Type setToType(BitSet v) {
            if (v.get(8)) {
                return Type.INTEGER;
            } else if (v.get(6)) {
                return !v.get(0) && !v.get(1) && !v.get(2) ? Type.CHARACTER : Type.INTEGER;
            } else if (v.get(7)) {
                if (v.get(0)) {
                    return Type.INTEGER;
                } else {
                    return !v.get(1) && !v.get(2) ? POS_SHORT : Type.SHORT;
                }
            } else if (v.get(5)) {
                if (v.get(0)) {
                    return Type.INTEGER;
                } else if (v.get(1)) {
                    return Type.SHORT;
                } else {
                    return v.get(2) ? Type.BYTE : POS_BYTE;
                }
            } else if (v.get(4)) {
                if (v.get(0)) {
                    return Type.INTEGER;
                } else if (v.get(1)) {
                    return Type.SHORT;
                } else {
                    return v.get(2) ? Type.BYTE : Type.BOOLEAN;
                }
            } else if (v.get(0)) {
                return Type.INTEGER;
            } else if (v.get(1)) {
                return Type.SHORT;
            } else {
                return v.get(2) ? Type.BYTE : Type.BOOLEAN;
            }
        }

        public static BitSet typeToSet(Type type) {
            BitSet v = new BitSet(8);
            byte lo;
            byte hi;
            if (type.equals(Type.INTEGER)) {
                lo = 0;
                hi = 8;
            } else if (type.equals(Type.CHARACTER)) {
                lo = 3;
                hi = 6;
            } else if (type.equals(Type.SHORT)) {
                lo = 1;
                hi = 7;
            } else if (type.equals(POS_SHORT)) {
                lo = 3;
                hi = 7;
            } else if (type.equals(Type.BYTE)) {
                lo = 2;
                hi = 5;
            } else if (type.equals(POS_BYTE)) {
                lo = 3;
                hi = 5;
            } else {
                if (!type.equals(Type.BOOLEAN)) {
                    throw new RuntimeException();
                }

                lo = 3;
                hi = 4;
            }

            for(int i = lo; i <= hi; ++i) {
                v.set(i);
            }

            return v;
        }

        public Set resolvesToWith(MemberRef method) {
            Set resolvesTo = (Set)this.resolvesToCache.get(method);
            if (resolvesTo == null) {
                this.db("Resolving " + method);
                resolvesTo = new HashSet();
                ClassHierarchy.ResolvesToWith rtw = new ClassHierarchy.ResolvesToWith();
                rtw.method = method;
                rtw.rTypes = new HashSet();
                MethodEditor me = null;

                try {
                    me = this.context.editMethod(method);
                } catch (NoSuchMethodException var15) {
                    this.db("  Hmm. Method is not implemented in declaring class");
                }

                if (me != null && (me.isStatic() || me.isConstructor())) {
                    rtw.rTypes.add(method.declaringClass());
                    ((Set)resolvesTo).add(rtw);
                    this.db("  Static method or constructor, resolves to itself");
                } else {
                    List types = new LinkedList();
                    types.add(method.declaringClass());

                    label81:
                    while(true) {
                        while(true) {
                            if (types.isEmpty()) {
                                break label81;
                            }

                            Type type = (Type)types.remove(0);
                            this.db("  Examining type " + type);
                            ClassEditor ce = null;

                            try {
                                ce = this.context.editClass(type);
                            } catch (ClassNotFoundException var14) {
                                System.err.println("** Class not found: " + var14.getMessage());
                                var14.printStackTrace(System.err);
                                System.exit(1);
                            }

                            if (ce.isInterface()) {
                                Iterator subinterfaces = this.subclasses(type).iterator();

                                while(subinterfaces.hasNext()) {
                                    Type subinterface = (Type)subinterfaces.next();
                                    types.add(subinterface);
                                    this.db("  Noting subinterface " + subinterface);
                                }

                                Iterator implementors = this.implementors(type).iterator();

                                while(implementors.hasNext()) {
                                    Type implementor = (Type)implementors.next();
                                    types.add(implementor);
                                    this.db("  Noting implementor " + implementor);
                                }
                            } else {
                                NameAndType nat = method.nameAndType();
                                MethodInfo[] methods = ce.methods();
                                boolean overridden = false;

                                for(int i = 0; i < methods.length; ++i) {
                                    MethodEditor over = this.context.editMethod(methods[i]);
                                    MemberRef ref = over.memberRef();
                                    if (ref.nameAndType().equals(nat) && !method.declaringClass().equals(type)) {
                                        this.db("  Class " + type + " overrides " + method);
                                        ((Set)resolvesTo).addAll(this.resolvesToWith(ref));
                                        overridden = true;
                                    }
                                }

                                if (!overridden) {
                                    this.db("  " + rtw.method + " called with " + type);
                                    rtw.rTypes.add(type);
                                    ((Set)resolvesTo).add(rtw);
                                    Iterator subclasses = this.subclasses(type).iterator();

                                    while(subclasses.hasNext()) {
                                        Type subclass = (Type)subclasses.next();
                                        types.add(subclass);
                                        this.db("  Noting subclass " + subclass);
                                    }
                                }
                            }
                        }
                    }
                }

                this.resolvesToCache.put(method, resolvesTo);
            }

            return (Set)resolvesTo;
        }

        public class ResolvesToWith {
            public MemberRef method;
            public HashSet rTypes;

            public ResolvesToWith() {
            }
        }

        class TypeNode extends GraphNode {
            Type type;

            public TypeNode(Type type) {
                this.type = type;
            }

            public String toString() {
                return "[" + this.type + "]";
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
    class FieldEditor {
        private ClassEditor editor;
        private FieldInfo fieldInfo;
        private String name;
        private Type type;
        private Object constantValue;
        private boolean isDirty;
        private boolean isDeleted;

        public FieldEditor(ClassEditor editor, int modifiers, Type type, String name) {
            this(editor, modifiers, (Type)type, name, (Object)null);
        }

        public FieldEditor(ClassEditor editor, int modifiers, Class type, String name, Object constantValue) {
            this(editor, modifiers, Type.getType(type), name, constantValue);
        }

        public FieldEditor(ClassEditor editor, int modifiers, Class type, String name) {
            this(editor, modifiers, (Type)Type.getType(type), name, (Object)null);
        }

        public FieldEditor(ClassEditor editor, int modifiers, Type type, String name, Object constantValue) {
            this.isDeleted = false;
            FieldInfo[] fields = editor.fields();

            for(int i = 0; i < fields.length; ++i) {
                FieldEditor fe = new FieldEditor(editor, fields[i]);
                if (fe.name().equals(name)) {
                    String s = "A field named " + name + " already exists in " + editor.name();
                    throw new IllegalArgumentException(s);
                }
            }

            this.editor = editor;
            ConstantPool cp = editor.constants();
            this.name = name;
            this.type = type;
            int typeIndex = cp.getUTF8Index(this.type.descriptor());
            int nameIndex = cp.getUTF8Index(name);
            ClassInfo classInfo = editor.classInfo();
            if (constantValue != null) {
                if ((modifiers & 8) == 0 || (modifiers & 16) == 0) {
                    String s = "Field " + name + " with a constant value must be static and final";
                    throw new IllegalArgumentException(s);
                }

                int valueIndex;
                String s;
                if (constantValue instanceof String) {
                    if (!type.equals(Type.STRING)) {
                        s = "Can't have field type of " + type.className() + " with a constant value of \"" + constantValue + "\"";
                        throw new IllegalArgumentException(s);
                    }

                    valueIndex = cp.getStringIndex((String)constantValue);
                } else if (constantValue instanceof Integer) {
                    if (!type.equals(Type.INTEGER)) {
                        s = "Can't have field type of " + type.className() + " with a constant value of \"" + constantValue + "\"";
                        throw new IllegalArgumentException(s);
                    }

                    valueIndex = cp.getIntegerIndex((Integer)constantValue);
                } else if (constantValue instanceof Long) {
                    if (!type.equals(Type.LONG)) {
                        s = "Can't have field type of " + type.className() + " with a constant value of \"" + constantValue + "\"";
                        throw new IllegalArgumentException(s);
                    }

                    valueIndex = cp.getLongIndex((Long)constantValue);
                } else if (constantValue instanceof Float) {
                    if (!type.equals(Type.FLOAT)) {
                        s = "Can't have field type of " + type.className() + " with a constant value of \"" + constantValue + "\"";
                        throw new IllegalArgumentException(s);
                    }

                    valueIndex = cp.getFloatIndex((Float)constantValue);
                } else {
                    if (!(constantValue instanceof Double)) {
                        s = "Cannot have a constant value of type " + constantValue.getClass().getName();
                        throw new IllegalArgumentException(s);
                    }

                    if (!type.equals(Type.DOUBLE)) {
                        s = "Can't have field type of " + type.className() + " with a constant value of \"" + constantValue + "\"";
                        throw new IllegalArgumentException(s);
                    }

                    valueIndex = cp.getDoubleIndex((Double)constantValue);
                }

                this.constantValue = constantValue;
                int cvNameIndex = cp.getUTF8Index("ConstantValue");
                this.fieldInfo = classInfo.addNewField(modifiers, typeIndex, nameIndex, cvNameIndex, valueIndex);
            } else {
                this.fieldInfo = classInfo.addNewField(modifiers, typeIndex, nameIndex);
            }

            this.isDirty = true;
        }

        public FieldEditor(ClassEditor editor, FieldInfo fieldInfo) {
            this.isDeleted = false;
            ConstantPool cp = editor.constants();
            this.fieldInfo = fieldInfo;
            this.editor = editor;
            int index = fieldInfo.nameIndex();
            this.name = (String)cp.constantAt(index);
            index = fieldInfo.typeIndex();
            String typeName = (String)cp.constantAt(index);
            this.type = Type.getType(typeName);
            index = fieldInfo.constantValue();
            this.constantValue = cp.constantAt(index);
            this.isDirty = false;
        }

        public ClassEditor declaringClass() {
            return this.editor;
        }

        public boolean isDirty() {
            return this.isDirty;
        }

        public void setDirty(boolean isDirty) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                this.isDirty = isDirty;
                if (isDirty) {
                    this.editor.setDirty(true);
                }

            }
        }

        public void delete() {
            this.setDirty(true);
            this.isDeleted = true;
        }

        public FieldInfo fieldInfo() {
            return this.fieldInfo;
        }

        public Object constantValue() {
            return this.constantValue;
        }

        public boolean isPublic() {
            return (this.fieldInfo.modifiers() & 1) != 0;
        }

        public boolean isPrivate() {
            return (this.fieldInfo.modifiers() & 2) != 0;
        }

        public boolean isProtected() {
            return (this.fieldInfo.modifiers() & 4) != 0;
        }

        public boolean isPackage() {
            return !this.isPublic() && !this.isPrivate() && !this.isProtected();
        }

        public boolean isStatic() {
            return (this.fieldInfo.modifiers() & 8) != 0;
        }

        public boolean isFinal() {
            return (this.fieldInfo.modifiers() & 16) != 0;
        }

        public boolean isVolatile() {
            return (this.fieldInfo.modifiers() & 64) != 0;
        }

        public boolean isTransient() {
            return (this.fieldInfo.modifiers() & 128) != 0;
        }

        public void setPublic(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 1;
                } else {
                    modifiers &= -2;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public void setPrivate(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 2;
                } else {
                    modifiers &= -3;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public void setProtected(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 4;
                } else {
                    modifiers &= -5;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public void setStatic(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 8;
                } else {
                    modifiers &= -9;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public void setFinal(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 16;
                } else {
                    modifiers &= -17;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public void setTransient(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 128;
                } else {
                    modifiers &= -129;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public void setVolatile(boolean flag) {
            if (this.isDeleted) {
                String s = "Cannot change a field once it has been marked for deletion";
                throw new IllegalStateException("Cannot change a field once it has been marked for deletion");
            } else {
                int modifiers = this.fieldInfo.modifiers();
                if (flag) {
                    modifiers |= 64;
                } else {
                    modifiers &= -65;
                }

                this.fieldInfo.setModifiers(modifiers);
                this.setDirty(true);
            }
        }

        public String name() {
            return this.name;
        }

        public Type type() {
            return this.type;
        }

        public NameAndType nameAndType() {
            return new NameAndType(this.name(), this.type());
        }

        public MemberRef memberRef() {
            return new MemberRef(this.declaringClass().type(), this.nameAndType());
        }

        public void commit() {
            ConstantPool cp;
            if (this.isDeleted) {
                cp = this.editor.constants();
                int nameIndex = cp.getUTF8Index(this.name);
                this.editor.classInfo().deleteField(nameIndex);
            } else {
                cp = this.editor.constants();
                this.fieldInfo.setNameIndex(cp.addConstant(1, this.name));
                this.fieldInfo.setTypeIndex(cp.addConstant(1, this.type.descriptor()));
                if (this.constantValue != null) {
                    if (this.constantValue instanceof Long) {
                        this.fieldInfo.setConstantValue(cp.addConstant(5, this.constantValue));
                    } else if (this.constantValue instanceof Float) {
                        this.fieldInfo.setConstantValue(cp.addConstant(4, this.constantValue));
                    } else if (this.constantValue instanceof Double) {
                        this.fieldInfo.setConstantValue(cp.addConstant(6, this.constantValue));
                    } else if (this.constantValue instanceof Integer) {
                        this.fieldInfo.setConstantValue(cp.addConstant(3, this.constantValue));
                    } else if (this.constantValue instanceof String) {
                        this.fieldInfo.setConstantValue(cp.addConstant(8, this.constantValue));
                    }
                }
            }

            this.isDirty = false;
        }

        public void print(PrintStream out) {
            out.println("field " + this.name + " " + this.type);
        }

        public String fullName() {
            return this.declaringClass().name() + "." + this.name();
        }

        public String toString() {
            return "[FieldEditor for " + this.name + this.type + "]";
        }
    }
}

class ClassFormatException extends RuntimeException {
    public ClassFormatException(String msg) {
        super(msg);
    }
}


package com.d0klabs.cryptowalt.data;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public interface InlineContext extends EditorContext {
    CallGraph getCallGraph();

    void setRootMethods(Set var1);

    InlineStats getInlineStats();

    void addIgnorePackage(String var1);

    void addIgnoreClass(Type var1);

    void addIgnoreMethod(MemberRef var1);

    void addIgnoreField(MemberRef var1);

    boolean ignoreClass(Type var1);

    boolean ignoreMethod(MemberRef var1);

    boolean ignoreField(MemberRef var1);

    void setIgnoreSystem(boolean var1);
    class InlineStats {
        private String configName = "Inlining stats";
        private Map morphicity = new TreeMap();
        private int nLiveClasses = 0;
        private int nLiveMethods = 0;
        private int nNoPreexist = 0;
        private int nInlined = 0;

        public InlineStats() {
        }

        public void setConfigName(String configName) {
            this.configName = configName;
        }

        public void noteMorphicity(int morphicity) {
            Integer r = new Integer(morphicity);
            Integer count = (Integer)this.morphicity.get(r);
            if (count == null) {
                this.morphicity.put(r, new Integer(1));
            } else {
                this.morphicity.put(r, new Integer(count + 1));
            }

        }

        public void noteNoPreexist() {
            ++this.nNoPreexist;
        }

        public void noteInlined() {
            ++this.nInlined;
        }

        public void noteLiveMethods(int nLiveMethods) {
            this.nLiveMethods = nLiveMethods;
        }

        public void noteLiveClasses(int nLiveClasses) {
            this.nLiveClasses = nLiveClasses;
        }

        public void printSummary(PrintWriter pw) {
            pw.println("Statistics for " + this.configName + " (" + new Date() + ")");
            pw.println("  Number of live classes: " + this.nLiveClasses);
            pw.println("  Number of live methods: " + this.nLiveMethods);
            pw.println("  Call site morphism:");
            Iterator morphs = this.morphicity.keySet().iterator();
            int total = 0;

            while(morphs.hasNext()) {
                Integer morph = (Integer)morphs.next();
                Integer count = (Integer)this.morphicity.get(morph);
                total += count;
                pw.println("    " + morph + "\t" + count);
            }

            pw.println("  Total number of call sites: " + total);
            pw.println("  Number of non-preexistent call sites: " + this.nNoPreexist);
            pw.println("  Number of inlined methods: " + this.nInlined);
        }
    }

    class CallGraph {
        public static boolean DEBUG = false;
        private static Set preLive;
        public static boolean USEPRELIVE = true;
        public static boolean USE1_2 = true;
        private Set roots;
        private Map calls;
        private Set liveClasses;
        private Map resolvesTo;
        private Map blocked;
        List worklist;
        Set liveMethods;
        InlineContext context;
        private ClassHierarchy hier;

        static void db(String s) {
            if (DEBUG) {
                System.out.println(s);
            }

        }

        private static void init() {
            preLive = new HashSet();
            preLive.add("java.lang.Boolean");
            preLive.add("java.lang.Class");
            preLive.add("java.lang.ClassLoader");
            preLive.add("java.lang.Compiler");
            preLive.add("java.lang.Integer");
            preLive.add("java.lang.SecurityManager");
            preLive.add("java.lang.String");
            preLive.add("java.lang.StringBuffer");
            preLive.add("java.lang.System");
            preLive.add("java.lang.StackOverflowError");
            preLive.add("java.lang.Thread");
            preLive.add("java.lang.ThreadGroup");
            preLive.add("java.io.BufferedInputStream");
            preLive.add("java.io.BufferedReader");
            preLive.add("java.io.BufferedOutputStream");
            preLive.add("java.io.BufferedWriter");
            preLive.add("java.io.File");
            preLive.add("java.io.FileDescriptor");
            preLive.add("java.io.InputStreamReader");
            preLive.add("java.io.ObjectStreamClass");
            preLive.add("java.io.OutputStreamWriter");
            preLive.add("java.io.PrintStream");
            preLive.add("java.io.PrintWriter");
            preLive.add("java.net.URL");
            preLive.add("java.security.Provider");
            preLive.add("java.security.Security");
            preLive.add("java.util.Hashtable");
            preLive.add("java.util.ListResourceBundle");
            preLive.add("java.util.Locale");
            preLive.add("java.util.Properties");
            preLive.add("java.util.Stack");
            preLive.add("java.util.Vector");
            preLive.add("java.util.zip.ZipFile");
            if (USE1_2) {
                preLive.add("java.lang.Package");
                preLive.add("java.lang.ref.Finalizer");
                preLive.add("java.lang.ref.ReferenceQueue");
                preLive.add("java.io.FilePermission");
                preLive.add("java.io.UnixFileSystem");
                preLive.add("java.net.URLClassLoader");
                preLive.add("java.security.SecureClassLoader");
                preLive.add("java.security.AccessController");
                preLive.add("java.text.resources.LocaleElements");
                preLive.add("java.text.resources.LocaleElements_en");
                preLive.add("java.util.HashMap");
                preLive.add("java.util.jar.JarFile");
            }

        }

        public static void addPreLive(String name) {
            if (preLive == null) {
                init();
            }

            preLive.add(name);
        }

        public static boolean removePreLive(String name) {
            if (preLive == null) {
                init();
            }

            return preLive.remove(name);
        }

        public CallGraph(InlineContext context, Set roots) {
            if (preLive == null) {
                init();
            }

            this.context = context;
            this.hier = context.getHierarchy();
            this.roots = roots;
            this.liveClasses = new HashSet();
            this.resolvesTo = new HashMap();
            this.calls = new HashMap();
            this.blocked = new HashMap();
            this.worklist = new LinkedList(this.roots);
            this.liveMethods = new HashSet();
            CallVisitor visitor = new CallVisitor(this);
            db("Adding pre-live classes");
            this.doPreLive();
            db("Constructing call graph");

            while(true) {
                MemberRef caller;
                MethodEditor callerMethod;
                do {
                    do {
                        do {
                            if (this.worklist.isEmpty()) {
                                this.blocked = null;
                                return;
                            }

                            caller = (MemberRef)this.worklist.remove(0);
                        } while(this.liveMethods.contains(caller));

                        callerMethod = null;

                        try {
                            callerMethod = context.editMethod(caller);
                        } catch (NoSuchMethodException var10) {
                            System.err.println("** Could not find method: " + caller);
                            var10.printStackTrace(System.err);
                            System.exit(1);
                        }
                    } while(callerMethod.isAbstract());

                    this.liveMethods.add(caller);
                } while(callerMethod.isNative());

                db("\n  Examining method " + caller);
                Set callees = new HashSet();
                this.calls.put(caller, callees);
                if (callerMethod.isStatic() || callerMethod.isConstructor()) {
                    this.addClinit(callerMethod.declaringClass().type());
                }

                Iterator code = callerMethod.code().iterator();
                visitor.setCaller(callerMethod);

                while(code.hasNext()) {
                    Object o = code.next();
                    if (o instanceof InstructionVisitor.Instruction) {
                        InstructionVisitor.Instruction inst = (InstructionVisitor.Instruction)o;
                        inst.visit(visitor);
                    }
                }
            }
        }

        private void doPreLive() {
            if (USEPRELIVE) {
                db("Making constructors of pre-live classes live");
                Iterator iter = preLive.iterator();

                while(iter.hasNext()) {
                    String name = (String)iter.next();
                    db("  " + name + " is pre-live");
                    name = name.replace('.', '/');
                    ClassEditor ce = null;

                    try {
                        ce = this.context.editClass(name);
                    } catch (ClassNotFoundException var7) {
                        System.err.println("** Cannot find pre-live class: " + name);
                        var7.printStackTrace(System.err);
                        System.exit(1);
                    }

                    this.liveClasses.add(ce.type());
                    this.addClinit(ce.type());
                    MethodInfo[] methods = ce.methods();

                    for(int i = 0; i < methods.length; ++i) {
                        MethodEditor method = this.context.editMethod(methods[i]);
                        if (method.name().equals("<init>")) {
                            db("  " + method);
                            this.worklist.add(method.memberRef());
                        }
                    }
                }

            }
        }

        void addClinit(Type type) {
            try {
                ClassEditor ce = this.context.editClass(type);
                MethodInfo[] methods = ce.methods();

                for(int i = 0; i < methods.length; ++i) {
                    MethodEditor clinit = this.context.editMethod(methods[i]);
                    if (clinit.name().equals("<clinit>")) {
                        this.worklist.add(clinit.memberRef());
                        this.context.release(clinit.methodInfo());
                        break;
                    }

                    this.context.release(clinit.methodInfo());
                }

                this.context.release(ce.classInfo());
            } catch (ClassNotFoundException var6) {
                System.err.println("** Could not find class for " + type);
                var6.printStackTrace(System.err);
                System.exit(1);
            }

        }

        void doVirtual(MethodEditor caller, MemberRef callee) {
            Iterator resolvesToWith = this.hier.resolvesToWith(callee).iterator();

            while(true) {
                ClassHierarchy.ResolvesToWith rtw;
                Iterator rTypes;
                boolean isLive;
                do {
                    if (!resolvesToWith.hasNext()) {
                        return;
                    }

                    rtw = (ClassHierarchy.ResolvesToWith)resolvesToWith.next();
                    db("      resolves to " + rtw.method);
                    this.addCall(caller, rtw.method);
                    rTypes = rtw.rTypes.iterator();
                    isLive = false;

                    while(rTypes.hasNext()) {
                        Type rType = (Type)rTypes.next();
                        if (this.liveClasses.contains(rType)) {
                            isLive = true;
                            db("      Method " + rtw.method + " is live");
                            this.worklist.add(rtw.method);
                            break;
                        }
                    }
                } while(isLive);

                rTypes = rtw.rTypes.iterator();
                StringBuffer sb = new StringBuffer();

                while(rTypes.hasNext()) {
                    Type rType = (Type)rTypes.next();
                    Set blockedMethods = (Set)this.blocked.get(rType);
                    if (blockedMethods == null) {
                        blockedMethods = new HashSet();
                        this.blocked.put(rType, blockedMethods);
                    }

                    ((Set)blockedMethods).add(rtw.method);
                    sb.append(rType.toString());
                    if (rTypes.hasNext()) {
                        sb.append(',');
                    }
                }

                db("      Blocked " + rtw.method + " on " + sb);
            }
        }

        void addCall(MethodEditor callerMethod, MemberRef callee) {
            MemberRef caller = callerMethod.memberRef();
            Set callees = (Set)this.calls.get(caller);
            if (callees == null) {
                callees = new HashSet();
                this.calls.put(caller, callees);
            }

            ((Set)callees).add(callee);
        }

        void makeLive(Type type) {
            if (!this.liveClasses.contains(type)) {
                db("    Making " + type + " live");
                this.liveClasses.add(type);
                Set blockedMethods = (Set)this.blocked.remove(type);
                if (blockedMethods != null) {
                    Iterator iter = blockedMethods.iterator();

                    while(iter.hasNext()) {
                        MemberRef method = (MemberRef)iter.next();
                        db("      Unblocking " + method);
                        this.worklist.add(method);
                    }
                }

            }
        }

        public Set resolvesTo(MemberRef method) {
            TreeSet resolvesTo = (TreeSet)this.resolvesTo.get(method);
            if (resolvesTo == null) {
                resolvesTo = new TreeSet(new MemberRefComparator(this.context));
                this.resolvesTo.put(method, resolvesTo);
                Set liveMethods = this.liveMethods();
                Iterator rtws = this.hier.resolvesToWith(method).iterator();

                while(rtws.hasNext()) {
                    ClassHierarchy.ResolvesToWith rtw = (ClassHierarchy.ResolvesToWith)rtws.next();
                    if (liveMethods.contains(rtw.method)) {
                        resolvesTo.add(rtw.method);
                    }
                }
            }

            return (Set)resolvesTo.clone();
        }

        public Set resolvesTo(MemberRef method, Set rTypes) {
            if (rTypes.isEmpty()) {
                return this.resolvesTo(method);
            } else {
                TreeSet resolvesTo = new TreeSet(new MemberRefComparator(this.context));
                Set liveMethods = this.liveMethods();
                Iterator rtws = this.hier.resolvesToWith(method).iterator();

                while(rtws.hasNext()) {
                    ClassHierarchy.ResolvesToWith rtw = (ClassHierarchy.ResolvesToWith)rtws.next();
                    if (liveMethods.contains(rtw.method)) {
                        HashSet clone = (HashSet)rtw.rTypes.clone();
                        clone.retainAll(rTypes);
                        if (!clone.isEmpty()) {
                            resolvesTo.add(rtw.method);
                        }
                    }
                }

                return (Set)resolvesTo.clone();
            }
        }

        public Set liveMethods() {
            return this.liveMethods;
        }

        public Set roots() {
            return this.roots;
        }

        public Set liveClasses() {
            return this.liveClasses;
        }

        public void print(PrintWriter out, boolean printLeaves) {
            Iterator callers = this.calls.keySet().iterator();

            while(true) {
                MemberRef caller;
                Iterator callees;
                do {
                    if (!callers.hasNext()) {
                        return;
                    }

                    caller = (MemberRef)callers.next();
                    callees = ((Set)this.calls.get(caller)).iterator();
                } while(!printLeaves && !callees.hasNext());

                out.print(caller.declaringClass() + "." + caller.name() + caller.type());
                if (this.roots.contains(caller)) {
                    out.print(" (root)");
                }

                out.println("");

                while(callees.hasNext()) {
                    MemberRef callee = (MemberRef)callees.next();
                    if (this.calls.containsKey(callee)) {
                        out.println("  " + callee.declaringClass() + "." + callee.name() + callee.type());
                    }
                }

                out.println("");
            }
        }

        public void printSummary(PrintWriter out) {
            out.println("Instantiated classes:");
            Iterator instantiated = this.liveClasses.iterator();

            while(instantiated.hasNext()) {
                Type type = (Type)instantiated.next();
                out.println("  " + type.toString());
            }

            out.println("\nBlocked methods:");
            if (this.blocked != null) {
                Iterator types = this.blocked.keySet().iterator();

                label27:
                while(true) {
                    Set set;
                    do {
                        if (!types.hasNext()) {
                            break label27;
                        }

                        Type type = (Type)types.next();
                        out.println("  " + type);
                        set = (Set)this.blocked.get(type);
                    } while(set == null);

                    Iterator methods = set.iterator();

                    while(methods.hasNext()) {
                        MemberRef method = (MemberRef)methods.next();
                        out.println("    " + method);
                    }
                }
            }

            out.println("\nCall graph:");
            this.print(out, false);
        }
    }
    class CallVisitor extends InstructionVisitor.InstructionAdapter {
        MethodEditor caller;
        CallGraph cg;
        boolean firstSpecial;

        private static void db(String s) {
            CallGraph.db(s);
        }

        public CallVisitor(CallGraph cg) {
            this.cg = cg;
        }

        public void setCaller(MethodEditor caller) {
            this.caller = caller;
            if (caller.isConstructor()) {
                this.firstSpecial = true;
            } else {
                this.firstSpecial = false;
            }

        }

        public void visit_invokevirtual(InstructionVisitor.Instruction inst) {
            db("\n    Visiting Call: " + inst);
            this.firstSpecial = false;
            MemberRef callee = (MemberRef)inst.operand();
            this.cg.doVirtual(this.caller, callee);
        }

        public void visit_invokeinterface(InstructionVisitor.Instruction inst) {
            db("\n    Visiting Call: " + inst);
            this.firstSpecial = false;
            MemberRef callee = (MemberRef)inst.operand();
            this.cg.doVirtual(this.caller, callee);
        }

        public void visit_invokestatic(InstructionVisitor.Instruction inst) {
            db("\n    Visiting call: " + inst);
            this.firstSpecial = false;
            MemberRef callee = (MemberRef)inst.operand();
            this.cg.addCall(this.caller, callee);
            this.cg.worklist.add(callee);
        }

        public void visit_invokespecial(InstructionVisitor.Instruction inst) {
            db("\n    Visiting call: " + inst);
            MemberRef callee = (MemberRef)inst.operand();
            MethodEditor calleeMethod = null;

            try {
                calleeMethod = this.cg.context.editMethod(callee);
            } catch (NoSuchMethodException var5) {
                System.err.println("** Couldn't find method: " + callee);
                System.exit(1);
            }

            if (!calleeMethod.isSynchronized() && !calleeMethod.isNative()) {
                this.cg.addCall(this.caller, callee);
                this.cg.worklist.add(callee);
            } else {
                this.cg.doVirtual(this.caller, callee);
            }

            this.cg.context.release(calleeMethod.methodInfo());
        }

        public void visit_getstatic(InstructionVisitor.Instruction inst) {
            db("\n    Referencing static field " + inst);
            MemberRef field = (MemberRef)inst.operand();
            this.cg.addClinit(field.declaringClass());
        }

        public void visit_putstatic(InstructionVisitor.Instruction inst) {
            db("\n    Referencing static field " + inst);
            MemberRef field = (MemberRef)inst.operand();
            this.cg.addClinit(field.declaringClass());
        }

        public void visit_new(InstructionVisitor.Instruction inst) {
            Type type = (Type)inst.operand();
            this.cg.makeLive(type);
        }
    }
    class MemberRefComparator implements Comparator {
        TypeComparator c;

        public MemberRefComparator(InlineContext context) {
            this.c = new TypeComparator(context);
        }

        public int compare(Object o1, Object o2) {
            MemberRef ref1 = (MemberRef)o1;
            MemberRef ref2 = (MemberRef)o2;
            Type type1 = ref1.declaringClass();
            Type type2 = ref2.declaringClass();
            return this.c.compare(type1, type2);
        }

        public boolean compareTo(Object other) {
            return other instanceof MemberRefComparator;
        }
    }




}

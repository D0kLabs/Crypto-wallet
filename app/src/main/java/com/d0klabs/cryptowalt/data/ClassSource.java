package com.d0klabs.cryptowalt.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public interface ClassSource {
    Class loadClass(String var1) throws ClassNotFoundException;
    class ClassFile implements ClassInfo {
        private final ClassInfoLoader loader;
        private List constants;
        private int modifiers;
        private int thisClass;
        private int superClass;
        private int[] interfaces;
        private Field[] fields;
        private Method[] methods;
        private Attribute[] attrs;
        private File file;
        private int major = 45;
        private int minor = 3;

        public ClassFile(File file, ClassInfoLoader loader, DataInputStream in) {
            this.loader = loader;
            this.file = file;

            try {
                this.readHeader(in);
                this.readConstantPool(in);
                this.readAccessFlags(in);
                this.readClassInfo(in);
                this.readFields(in);
                this.readMethods(in);
                this.readAttributes(in);
                in.close();
            } catch (IOException var5) {
                throw new ClassFormatException(var5.getMessage() + " (in file " + file + ")");
            }
        }

        public ClassFile(int modifiers, int classIndex, int superClassIndex, int[] interfaceIndexes, List constants, ClassInfoLoader loader) {
            this.modifiers = modifiers;
            this.thisClass = classIndex;
            this.superClass = superClassIndex;
            this.interfaces = interfaceIndexes;
            this.constants = constants;
            this.loader = loader;
            this.fields = new Field[0];
            this.methods = new Method[0];
            this.attrs = new Attribute[0];
        }

        public ClassInfoLoader loader() {
            return this.loader;
        }

        public String name() {
            Constant c = (Constant)this.constants.get(this.thisClass);
            if (c.tag() == 7) {
                Integer nameIndex = (Integer)c.value();
                if (nameIndex != null) {
                    c = (Constant)this.constants.get(nameIndex);
                    if (c.tag() == 1) {
                        return (String)c.value();
                    }
                }
            }

            throw new ClassFormatException("Couldn't find class name in file");
        }

        public void setClassIndex(int index) {
            this.thisClass = index;
        }

        public void setSuperclassIndex(int index) {
            this.superClass = index;
        }

        public void setInterfaceIndices(int[] indices) {
            this.interfaces = indices;
        }

        public int classIndex() {
            return this.thisClass;
        }

        public int superclassIndex() {
            return this.superClass;
        }

        public int[] interfaceIndices() {
            return this.interfaces;
        }

        public void setModifiers(int modifiers) {
            this.modifiers = modifiers;
        }

        public int modifiers() {
            return this.modifiers;
        }

        public FieldInfo[] fields() {
            return this.fields;
        }

        public MethodInfo[] methods() {
            return this.methods;
        }

        public void setMethods(MethodInfo[] methods) {
            this.methods = new Method[methods.length];

            for(int i = 0; i < methods.length; ++i) {
                this.methods[i] = (Method)methods[i];
            }

        }

        public Constant[] constants() {
            return (Constant[])this.constants.toArray(new Constant[0]);
        }

        public void setConstants(Constant[] constants) {
            this.constants = new ArrayList(constants.length);

            for(int i = 0; i < constants.length; ++i) {
                this.constants.add(i, constants[i]);
            }

        }

        public File file() {
            return this.file;
        }

        public File outputFile() {
            File outputDir = ((ClassFileLoader)this.loader).outputDir();
            String fileName = this.name().replace('/', File.separatorChar);
            return new File(outputDir, fileName + ".class");
        }

        public void commit() {
            try {
                this.commitTo(this.loader.outputStreamFor(this));
            } catch (IOException var2) {
                var2.printStackTrace();
                System.exit(1);
            }

        }

        void commitTo(OutputStream outStream) {
            try {
                DataOutputStream out = new DataOutputStream(outStream);
                this.writeHeader(out);
                this.writeConstantPool(out);
                this.writeAccessFlags(out);
                this.writeClassInfo(out);
                this.writeFields(out, (Set)null);
                this.writeMethods(out, (Set)null);
                this.writeAttributes(out);
                out.close();
            } catch (IOException var3) {
                var3.printStackTrace();
                System.exit(1);
            }

        }

        public void commitOnly(Set methods, Set fields) {
            try {
                OutputStream outStream = this.loader.outputStreamFor(this);
                DataOutputStream out = new DataOutputStream(outStream);
                this.writeHeader(out);
                this.writeConstantPool(out);
                this.writeAccessFlags(out);
                this.writeClassInfo(out);
                this.writeFields(out, fields);
                this.writeMethods(out, methods);
                this.writeAttributes(out);
                out.close();
            } catch (IOException var5) {
                var5.printStackTrace();
                System.exit(1);
            }

        }

        private void writeHeader(DataOutputStream out) throws IOException {
            out.writeInt(-889275714);
            out.writeShort(this.major);
            out.writeShort(this.minor);
        }

        private void writeConstantPool(DataOutputStream out) throws IOException {
            out.writeShort(this.constants.size());
            int i = 1;

            while(i < this.constants.size()) {
                this.writeConstant(out, (Constant)this.constants.get(i));
                switch(((Constant)this.constants.get(i)).tag()) {
                    case 5:
                    case 6:
                        ++i;
                    default:
                        ++i;
                }
            }

        }

        private Constant readConstant(DataInputStream in) throws IOException {
            int tag = in.readUnsignedByte();
            Object value;
            switch(tag) {
                case 1:
                    value = in.readUTF();
                    break;
                case 2:
                default:
                    throw new ClassFormatException(this.file.getPath() + ": Invalid constant tag: " + tag);
                case 3:
                    value = new Integer(in.readInt());
                    break;
                case 4:
                    value = new Float(in.readFloat());
                    break;
                case 5:
                    value = new Long(in.readLong());
                    break;
                case 6:
                    value = new Double(in.readDouble());
                    break;
                case 7:
                case 8:
                    value = new Integer(in.readUnsignedShort());
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                    value = new int[2];
                    ((int[])value)[0] = in.readUnsignedShort();
                    ((int[])value)[1] = in.readUnsignedShort();
            }

            return new Constant(tag, value);
        }

        private void writeConstant(DataOutputStream out, Constant constant) throws IOException {
            int tag = constant.tag();
            Object value = constant.value();
            out.writeByte(tag);
            switch(tag) {
                case 1:
                    out.writeUTF((String)value);
                case 2:
                default:
                    break;
                case 3:
                    out.writeInt((Integer)value);
                    break;
                case 4:
                    out.writeFloat((Float)value);
                    break;
                case 5:
                    out.writeLong((Long)value);
                    break;
                case 6:
                    out.writeDouble((Double)value);
                    break;
                case 7:
                case 8:
                    out.writeShort((Integer)value);
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                    out.writeShort(((int[])value)[0]);
                    out.writeShort(((int[])value)[1]);
            }

        }

        private void writeAccessFlags(DataOutputStream out) throws IOException {
            out.writeShort(this.modifiers);
        }

        private void writeClassInfo(DataOutputStream out) throws IOException {
            out.writeShort(this.thisClass);
            out.writeShort(this.superClass);
            out.writeShort(this.interfaces.length);

            for(int i = 0; i < this.interfaces.length; ++i) {
                out.writeShort(this.interfaces[i]);
            }

        }

        private void writeFields(DataOutputStream out, Set onlyFields) throws IOException {
            out.writeShort(this.fields.length);

            for(int i = 0; i < this.fields.length; ++i) {
                if (onlyFields == null || !onlyFields.contains(this.fields[i])) {
                    this.fields[i].write(out);
                }
            }

        }

        private void writeMethods(DataOutputStream out, Set onlyMethods) throws IOException {
            if (onlyMethods != null) {
                out.writeShort(onlyMethods.size());
            } else {
                out.writeShort(this.methods.length);
            }

            for(int i = 0; i < this.methods.length; ++i) {
                if (onlyMethods == null || !onlyMethods.contains(this.methods[i])) {
                    this.methods[i].write(out);
                }
            }

        }

        private void writeAttributes(DataOutputStream out) throws IOException {
            out.writeShort(this.attrs.length);

            for(int i = 0; i < this.attrs.length; ++i) {
                out.writeShort(this.attrs[i].nameIndex());
                out.writeInt(this.attrs[i].length());
                this.attrs[i].writeData(out);
            }

        }

        private void readHeader(DataInputStream in) throws IOException {
            int magic = in.readInt();
            if (magic != -889275714) {
                throw new ClassFormatError("Bad magic number.");
            } else {
                this.major = in.readUnsignedShort();
                this.minor = in.readUnsignedShort();
            }
        }

        private void readConstantPool(DataInputStream in) throws IOException {
            int count = in.readUnsignedShort();
            this.constants = new ArrayList(count);
            this.constants.add(0, (Object)null);
            int i = 1;

            while(i < count) {
                this.constants.add(i, this.readConstant(in));
                switch(((Constant)this.constants.get(i)).tag()) {
                    case 5:
                    case 6:
                        ++i;
                        this.constants.add(i, (Object)null);
                    default:
                        ++i;
                }
            }

        }

        private void readAccessFlags(DataInputStream in) throws IOException {
            this.modifiers = in.readUnsignedShort();
        }

        private void readClassInfo(DataInputStream in) throws IOException {
            this.thisClass = in.readUnsignedShort();
            this.superClass = in.readUnsignedShort();
            int numInterfaces = in.readUnsignedShort();
            this.interfaces = new int[numInterfaces];

            for(int i = 0; i < numInterfaces; ++i) {
                this.interfaces[i] = in.readUnsignedShort();
            }

        }

        private void readFields(DataInputStream in) throws IOException {
            int numFields = in.readUnsignedShort();
            this.fields = new Field[numFields];

            for(int i = 0; i < numFields; ++i) {
                this.fields[i] = new Field(in, this);
            }

        }

        private void readMethods(DataInputStream in) throws IOException {
            int numMethods = in.readUnsignedShort();
            this.methods = new Method[numMethods];

            for(int i = 0; i < numMethods; ++i) {
                this.methods[i] = new Method(in, this);
            }

        }

        private void readAttributes(DataInputStream in) throws IOException {
            int numAttributes = in.readUnsignedShort();
            this.attrs = new Attribute[numAttributes];

            for(int i = 0; i < numAttributes; ++i) {
                int nameIndex = in.readUnsignedShort();
                int length = in.readInt();
                this.attrs[i] = new GenericAttribute(in, nameIndex, length);
            }

        }

        public FieldInfo addNewField(int modifiers, int typeIndex, int nameIndex) {
            Field field = new Field(this, modifiers, typeIndex, nameIndex);
            Field[] fields = new Field[this.fields.length + 1];

            for(int i = 0; i < this.fields.length; ++i) {
                fields[i] = this.fields[i];
            }

            fields[this.fields.length] = field;
            this.fields = fields;
            return field;
        }

        public FieldInfo addNewField(int modifiers, int typeIndex, int nameIndex, int cvNameIndex, int constantValueIndex) {
            Field field = new Field(this, modifiers, typeIndex, nameIndex, cvNameIndex, constantValueIndex);
            Field[] fields = new Field[this.fields.length + 1];

            for(int i = 0; i < this.fields.length; ++i) {
                fields[i] = this.fields[i];
            }

            fields[this.fields.length] = field;
            this.fields = fields;
            return field;
        }

        public void deleteField(int nameIndex) {
            List newFields = new ArrayList();
            boolean foundIt = false;

            for(int i = 0; i < this.fields.length; ++i) {
                Field field = this.fields[i];
                if (field.nameIndex() == nameIndex) {
                    foundIt = true;
                } else {
                    newFields.add(field);
                }
            }

            if (!foundIt) {
                String s = "No field with name index " + nameIndex + " in " + this.name();
                throw new IllegalArgumentException(s);
            } else {
                this.fields = (Field[])newFields.toArray(new Field[0]);
            }
        }

        public void deleteMethod(int nameIndex, int typeIndex) {
            List newMethods = new ArrayList();
            boolean foundIt = false;

            for(int i = 0; i < this.methods.length; ++i) {
                Method method = this.methods[i];
                if (method.nameIndex() == nameIndex && method.typeIndex() == typeIndex) {
                    foundIt = true;
                } else {
                    newMethods.add(method);
                }
            }

            if (!foundIt) {
                String s = "No method with name index " + nameIndex + " and type index " + typeIndex + " in " + this.name();
                throw new IllegalArgumentException(s);
            } else {
                this.methods = (Method[])newMethods.toArray(new Method[0]);
            }
        }

        public MethodInfo addNewMethod(int modifiers, int typeIndex, int nameIndex, int exceptionIndex, int[] exceptionTypeIndices, int codeIndex) {
            Exceptions exceptions = new Exceptions(this, exceptionIndex, exceptionTypeIndices);
            Code code = new Code(this, codeIndex);
            Attribute[] attributes = new Attribute[]{exceptions, code};
            Method method = new Method(this, modifiers, nameIndex, typeIndex, attributes, code, exceptions);
            Method[] methods = new Method[this.methods.length + 1];

            for(int i = 0; i < this.methods.length; ++i) {
                methods[i] = this.methods[i];
            }

            methods[this.methods.length] = method;
            this.methods = methods;
            return method;
        }

        public void print(PrintStream out) {
            this.print(new PrintWriter(out, true));
        }

        public void print(PrintWriter out) {
            out.print("(constants");

            int i;
            for(i = 0; i < this.constants.size(); ++i) {
                out.print("\n    " + i + ": " + this.constants.get(i));
            }

            out.println(")");
            out.println("(class " + this.classIndex() + ")");
            out.println("(super " + this.superclassIndex() + ")");
            out.print("(interfaces");

            for(i = 0; i < this.interfaces.length; ++i) {
                out.print("\n    " + i + ": " + this.interfaces[i]);
            }

            out.println(")");
            out.print("(modifiers");
            if ((this.modifiers & 1) != 0) {
                out.print(" PUBLIC");
            }

            if ((this.modifiers & 16) != 0) {
                out.print(" FINAL");
            }

            if ((this.modifiers & 32) != 0) {
                out.print(" SUPER");
            }

            if ((this.modifiers & 512) != 0) {
                out.print(" INTERFACE");
            }

            if ((this.modifiers & 1024) != 0) {
                out.print(" ABSTRACT");
            }

            out.println(")");
            out.print("(fields");

            for(i = 0; i < this.fields.length; ++i) {
                out.print("\n    " + i + ": " + this.fields[i]);
            }

            out.println(")");
            out.print("(methods");

            for(i = 0; i < this.methods.length; ++i) {
                out.print("\n    " + i + ": " + this.methods[i]);
            }

            out.println(")");
        }

        public String toString() {
            return "(ClassFile " + this.name() + ")";
        }
    }
    class ClassFileLoader implements ClassInfoLoader {
        public static boolean DEBUG = false;
        public static boolean USE_SYSTEM_CLASSES = true;
        private File outputDir = FileIO.FILE_NAME;
        private String classpath;
        private Map openZipFiles;
        private LinkedList cache;
        private boolean verbose;
        private static final int CACHE_LIMIT = 10;
        private ClassSource _classSource;

        public ClassFileLoader(ClassSource classSource) {
            this.outputDir = new File(".");
            this.classpath = System.getProperty("java.class.path");
            this.classpath = this.classpath + File.pathSeparator + System.getProperty("sun.boot.class.path");
            if (USE_SYSTEM_CLASSES) {
                this.classpath = this.classpath + File.pathSeparator + System.getProperty("java.sys.class.path");
            }

            this.openZipFiles = new HashMap();
            this.cache = new LinkedList();
            this.verbose = false;
            this._classSource = classSource;
        }

        public ClassFileLoader() {
            this(new DefaultClassSource());
        }

        public void setVerbose(boolean verbose) {
            this.verbose = verbose;
        }

        public void setClassPath(String classpath) {
            this.classpath = classpath;
        }

        public void appendClassPath(String morePath) {
            this.classpath = this.classpath + File.pathSeparator + morePath;
        }

        public void prependClassPath(String morePath) {
            this.classpath = morePath + File.pathSeparator + this.classpath;
        }

        public String getClassPath() {
            return this.classpath;
        }

        private ClassInfo loadClassFromStream(File inputFile, InputStream stream) throws ClassNotFoundException {
            DataInputStream in = new DataInputStream(stream);
            ClassFile file = new ClassFile(inputFile, this, in);
            return file;
        }

        private ClassInfo loadClassFromFile(File file) throws ClassNotFoundException {
            try {
                InputStream in = new FileInputStream(file);
                ClassInfo info = this.loadClassFromStream(file, in);
                if (this.verbose) {
                    System.out.println("[Loaded " + info.name() + " from " + file.getPath() + "]");
                }

                try {
                    in.close();
                } catch (IOException var5) {
                }

                return info;
            } catch (FileNotFoundException var6) {
                throw new ClassNotFoundException(file.getPath());
            }
        }

        public ClassInfo newClass(int modifiers, int classIndex, int superClassIndex, int[] interfaceIndexes, List constants) {
            return new ClassFile(modifiers, classIndex, superClassIndex, interfaceIndexes, constants, this);
        }

        private ClassInfo loadClassFromRessource(String name) {
            name = name.replace('/', '.');

            try {
                Class clazz = this._classSource.loadClass(name);
                int i = name.lastIndexOf(46);
                if (i >= 0 && i < name.length()) {
                    name = name.substring(i + 1);
                }

                URL url = clazz.getResource(name + ".class");
                if (url != null) {
                    return this.loadClassFromStream(new File(url.getFile()), url.openStream());
                }
            } catch (Exception var5) {
            }

            return null;
        }

        public ClassInfo loadClass(String name) throws ClassNotFoundException {
            ClassInfo file = null;
            if (name.endsWith(".class")) {
                File nameFile = new File(name);
                if (!nameFile.exists()) {
                    throw new ClassNotFoundException(name);
                } else {
                    return this.loadClassFromFile(nameFile);
                }
            } else if ((file = this.loadClassFromRessource(name)) != null) {
                this.addToCache(file);
                return file;
            } else {
                name = name.replace('.', '/');
                if (DEBUG) {
                    System.out.println("  Looking for " + name + " in cache = " + this.cache);
                }

                Iterator iter = this.cache.iterator();

                while(iter.hasNext()) {
                    file = (ClassFile)iter.next();
                    if (name.equals(file.name())) {
                        if (DEBUG) {
                            System.out.println("  Found " + file.name() + " in cache");
                        }

                        iter.remove();
                        this.cache.addFirst(file);
                        return file;
                    }
                }

                file = null;
                String classFile = name.replace('/', File.separatorChar) + ".class";
                String path = this.classpath + File.pathSeparator;
                if (DEBUG) {
                    System.out.println("CLASSPATH = " + path);
                }

                int index = 0;

                for(int end = path.indexOf(File.pathSeparator, index); end >= 0; end = path.indexOf(File.pathSeparator, index)) {
                    String dir = path.substring(index, end);
                    File f = new File(dir);
                    if (f.isDirectory()) {
                        f = new File(dir, classFile);
                        if (f.exists()) {
                            try {
                                InputStream in = new FileInputStream(f);
                                if (this.verbose) {
                                    System.out.println("  [Loaded " + name + " from " + f.getPath() + "]");
                                }

                                file = this.loadClassFromStream(f, in);

                                try {
                                    in.close();
                                } catch (IOException var17) {
                                }
                                break;
                            } catch (FileNotFoundException var21) {
                            }
                        }
                    } else if (dir.endsWith(".zip") || dir.endsWith(".jar")) {
                        try {
                            ZipFile zip = (ZipFile)this.openZipFiles.get(dir);
                            if (zip == null) {
                                zip = new ZipFile(f);
                                this.openZipFiles.put(dir, zip);
                            }

                            String zipEntry = classFile.replace(File.separatorChar, '/');
                            ZipEntry entry = zip.getEntry(zipEntry);
                            if (entry != null) {
                                if (this.verbose) {
                                    System.out.println("  [Loaded " + name + " from " + f.getPath() + "]");
                                }

                                InputStream in = zip.getInputStream(entry);
                                file = this.loadClassFromStream(f, in);

                                try {
                                    in.close();
                                } catch (IOException var18) {
                                }
                                break;
                            }
                        } catch (ZipException var19) {
                        } catch (IOException var20) {
                        }
                    }

                    index = end + 1;
                }

                if (file == null) {
                    File f = new File(classFile);
                    if (!f.exists()) {
                        throw new ClassNotFoundException(name);
                    }

                    if (this.verbose) {
                        System.out.println("  [Loaded " + name + " from " + f.getPath() + "]");
                    }

                    try {
                        InputStream in = new FileInputStream(f);
                        file = this.loadClassFromStream(f, in);

                        try {
                            in.close();
                        } catch (IOException var15) {
                        }
                    } catch (FileNotFoundException var16) {
                        throw new ClassNotFoundException(name);
                    }
                }

                if (file == null) {
                    throw new ClassNotFoundException(name);
                } else {
                    this.addToCache(file);
                    return file;
                }
            }
        }

        private void addToCache(ClassInfo file) {
            if (this.cache.size() == 10) {
                this.cache.removeLast();
            }

            this.cache.addFirst(file);
        }

        public void setOutputDir(File dir) {
            this.outputDir = dir;
        }

        public File outputDir() {
            return this.outputDir;
        }

        public void writeEntry(byte[] bytes, String name) throws IOException {
            OutputStream os = this.outputStreamFor(name);
            os.write(bytes);
            os.flush();
            os.close();
        }

        public OutputStream outputStreamFor(ClassInfo info) throws IOException {
            String name = info.name().replace('/', File.separatorChar) + ".class";
            return this.outputStreamFor(name);
        }

        protected OutputStream outputStreamFor(String name) throws IOException {
            name = name.replace('/', File.separatorChar);
            File f = new File(this.outputDir, name);
            if (f.exists()) {
                f.delete();
            }

            File dir = new File(f.getParent());
            dir.mkdirs();
            if (!dir.exists()) {
                throw new RuntimeException("Couldn't create directory: " + dir);
            } else {
                return new FileOutputStream(f);
            }
        }

        public void done() throws IOException {
        }
    }
    public class Code extends Attribute {
        private ClassInfo classInfo;
        private int maxStack;
        private int maxLocals;
        private byte[] code;
        private MethodInfo.Catch[] handlers;
        private LineNumberTable lineNumbers;
        private LocalVariableTable locals;
        private Attribute[] attrs;

        Code(ClassInfo classInfo, int codeIndex) {
            super(codeIndex, -1);
            this.classInfo = classInfo;
            this.maxStack = -1;
            this.maxLocals = -1;
            this.code = new byte[0];
            this.handlers = new MethodInfo.Catch[0];
            this.lineNumbers = null;
            this.locals = null;
            this.attrs = new Attribute[0];
        }

        public Code(ClassInfo classInfo, DataInputStream in, int index, int len) throws IOException {
            super(index, len);
            this.classInfo = classInfo;
            this.maxStack = in.readUnsignedShort();
            this.maxLocals = in.readUnsignedShort();
            int codeLength = in.readInt();
            this.code = new byte[codeLength];

            int numHandlers;
            for(numHandlers = 0; numHandlers < codeLength; numHandlers += in.read(this.code, numHandlers, codeLength - numHandlers)) {
            }

            numHandlers = in.readUnsignedShort();
            this.handlers = new MethodInfo.Catch[numHandlers];

            int numAttributes;
            for(numAttributes = 0; numAttributes < numHandlers; ++numAttributes) {
                this.handlers[numAttributes] = this.readCatch(in);
            }

            numAttributes = in.readUnsignedShort();
            List attrList = new ArrayList(numAttributes);

            for(int i = 0; i < numAttributes; ++i) {
                int nameIndex = in.readUnsignedShort();
                int length = in.readInt();
                ClassInfo.Constant name = classInfo.constants()[nameIndex];
                if (name != null) {
                    if ("LineNumberTable".equals(name.value())) {
                        this.lineNumbers = new LineNumberTable(in, nameIndex, length);
                        attrList.add(this.lineNumbers);
                    } else if ("LocalVariableTable".equals(name.value())) {
                        this.locals = new LocalVariableTable(in, nameIndex, length);
                        attrList.add(this.locals);
                    } else if ("LocalVariableTypeTable".equals(name.value())) {
                        new GenericAttribute(in, nameIndex, length);
                    } else {
                        attrList.add(new GenericAttribute(in, nameIndex, length));
                    }
                } else {
                    attrList.add(new GenericAttribute(in, nameIndex, length));
                }
            }

            this.attrs = (Attribute[])attrList.toArray(new Attribute[attrList.size()]);
        }

        public void writeData(DataOutputStream out) throws IOException {
            out.writeShort(this.maxStack);
            out.writeShort(this.maxLocals);
            out.writeInt(this.code.length);
            out.write(this.code, 0, this.code.length);
            out.writeShort(this.handlers.length);

            int i;
            for(i = 0; i < this.handlers.length; ++i) {
                this.writeCatch(out, this.handlers[i]);
            }

            out.writeShort(this.attrs.length);

            for(i = 0; i < this.attrs.length; ++i) {
                out.writeShort(this.attrs[i].nameIndex());
                out.writeInt(this.attrs[i].length());
                this.attrs[i].writeData(out);
            }

        }

        private MethodInfo.Catch readCatch(DataInputStream in) throws IOException {
            int startPC = in.readUnsignedShort();
            int endPC = in.readUnsignedShort();
            int handlerPC = in.readUnsignedShort();
            int catchType = in.readUnsignedShort();
            return new MethodInfo.Catch(startPC, endPC, handlerPC, catchType);
        }

        private void writeCatch(DataOutputStream out, MethodInfo.Catch c) throws IOException {
            int startPC = c.startPC();
            int endPC = c.endPC();
            int handlerPC = c.handlerPC();
            int catchType = c.catchTypeIndex();
            out.writeShort(startPC);
            out.writeShort(endPC);
            out.writeShort(handlerPC);
            out.writeShort(catchType);
        }

        public void setMaxStack(int maxStack) {
            this.maxStack = maxStack;
        }

        public void setMaxLocals(int maxLocals) {
            this.maxLocals = maxLocals;
        }

        public int maxStack() {
            return this.maxStack;
        }

        public int maxLocals() {
            return this.maxLocals;
        }

        public void setExceptionHandlers(MethodInfo.Catch[] handlers) {
            this.handlers = handlers;
        }

        public int length() {
            int length = 8 + this.code.length + 2 + this.handlers.length * 8 + 2;

            for(int i = 0; i < this.attrs.length; ++i) {
                length += 6 + this.attrs[i].length();
            }

            return length;
        }

        public MethodInfo.LineNumberDebugInfo[] lineNumbers() {
            return this.lineNumbers != null ? this.lineNumbers.lineNumbers() : new MethodInfo.LineNumberDebugInfo[0];
        }

        public MethodInfo.LocalDebugInfo[] locals() {
            return this.locals != null ? this.locals.locals() : new MethodInfo.LocalDebugInfo[0];
        }

        public void setLineNumbers(MethodInfo.LineNumberDebugInfo[] lineNumbers) {
            if (lineNumbers == null) {
                for(int i = 0; i < this.attrs.length; ++i) {
                    if (this.lineNumbers == this.attrs[i]) {
                        Attribute[] a = this.attrs;
                        this.attrs = new Attribute[a.length - 1];
                        System.arraycopy(a, 0, this.attrs, 0, i);
                        System.arraycopy(a, i + 1, this.attrs, i, this.attrs.length - i);
                        break;
                    }
                }

                this.lineNumbers = null;
            } else if (this.lineNumbers != null) {
                this.lineNumbers.setLineNumbers(lineNumbers);
            }

        }

        public void setLocals(MethodInfo.LocalDebugInfo[] locals) {
            if (locals == null) {
                for(int i = 0; i < this.attrs.length; ++i) {
                    if (this.locals == this.attrs[i]) {
                        Attribute[] a = this.attrs;
                        this.attrs = new Attribute[a.length - 1];
                        System.arraycopy(a, 0, this.attrs, 0, i);
                        System.arraycopy(a, i + 1, this.attrs, i, this.attrs.length - i);
                        break;
                    }
                }

                this.locals = null;
            } else if (this.locals != null) {
                this.locals.setLocals(locals);
            }

        }

        public MethodInfo.Catch[] exceptionHandlers() {
            return this.handlers;
        }

        public byte[] code() {
            return this.code;
        }

        public int codeLength() {
            return this.code.length;
        }

        public void setCode(byte[] code) {
            this.code = code;
        }

        private Code(Code other) {
            super(other.nameIndex, other.length);
            this.classInfo = other.classInfo;
            this.maxStack = other.maxStack;
            this.maxLocals = other.maxLocals;
            this.code = new byte[other.code.length];
            System.arraycopy(other.code, 0, this.code, 0, other.code.length);
            this.handlers = new MethodInfo.Catch[other.handlers.length];

            int i;
            for(i = 0; i < other.handlers.length; ++i) {
                this.handlers[i] = (MethodInfo.Catch)other.handlers[i].clone();
            }

            if (other.lineNumbers != null) {
                this.lineNumbers = (LineNumberTable)other.lineNumbers.clone();
            }

            if (other.locals != null) {
                this.locals = (LocalVariableTable)other.locals.clone();
            }

            this.attrs = new Attribute[other.attrs.length];

            for(i = 0; i < other.attrs.length; ++i) {
                this.attrs[i] = other.attrs[i];
            }

        }

        public Object clone() {
            return new Code(this);
        }

        public String toString() {
            String x = "";
            if (this.handlers != null) {
                for(int i = 0; i < this.handlers.length; ++i) {
                    x = x + "\n        " + this.handlers[i];
                }
            }

            return "(code " + this.maxStack + " " + this.maxLocals + " " + this.code.length + x + ")";
        }
    }
    public class ConstantValue extends Attribute {
        private int constantValueIndex;

        ConstantValue(int nameIndex, int length, int constantValueIndex) {
            super(nameIndex, length);
            this.constantValueIndex = constantValueIndex;
        }

        public ConstantValue(DataInputStream in, int nameIndex, int length) throws IOException {
            super(nameIndex, length);
            this.constantValueIndex = in.readUnsignedShort();
        }

        public void writeData(DataOutputStream out) throws IOException {
            out.writeShort(this.constantValueIndex);
        }

        public int constantValueIndex() {
            return this.constantValueIndex;
        }

        public void setConstantValueIndex(int index) {
            this.constantValueIndex = index;
        }

        private ConstantValue(ConstantValue other) {
            super(other.nameIndex, other.length);
            this.constantValueIndex = other.constantValueIndex;
        }

        public Object clone() {
            return new ConstantValue(this);
        }

        public String toString() {
            return "(constant-value " + this.constantValueIndex + ")";
        }
    }
    public class DefaultClassSource implements ClassSource {
        public DefaultClassSource() {
        }

        public Class loadClass(String name) throws ClassNotFoundException {
            return Class.forName(name);
        }
    }
    public class Exceptions extends Attribute {
        private int[] exceptions;
        private ClassInfo classInfo;

        Exceptions(ClassInfo info, int nameIndex, int[] exceptions) {
            super(nameIndex, 2 * exceptions.length + 2);
            this.classInfo = info;
            this.exceptions = exceptions;
        }

        public Exceptions(ClassInfo classInfo, DataInputStream in, int nameIndex, int length) throws IOException {
            super(nameIndex, length);
            this.classInfo = classInfo;
            int count = in.readUnsignedShort();
            this.exceptions = new int[count];

            for(int i = 0; i < count; ++i) {
                this.exceptions[i] = in.readUnsignedShort();
            }

        }

        public void writeData(DataOutputStream out) throws IOException {
            out.writeShort(this.exceptions.length);

            for(int i = 0; i < this.exceptions.length; ++i) {
                out.writeShort(this.exceptions[i]);
            }

        }

        public int[] exceptionTypes() {
            return this.exceptions;
        }

        public int length() {
            return 2 + this.exceptions.length * 2;
        }

        private Exceptions(Exceptions other) {
            super(other.nameIndex, other.length);
            this.exceptions = new int[other.exceptions.length];
            System.arraycopy(other.exceptions, 0, this.exceptions, 0, other.exceptions.length);
            this.classInfo = other.classInfo;
        }

        public Object clone() {
            return new Exceptions(this);
        }

        public String toString() {
            return "(exceptions)";
        }
    }
    public abstract class Attribute {
        protected int nameIndex;
        protected int length;

        public Attribute(int nameIndex, int length) {
            this.nameIndex = nameIndex;
            this.length = length;
        }

        public abstract void writeData(DataOutputStream var1) throws IOException;

        public String toString() {
            return "(attribute " + this.nameIndex + " " + this.length + ")";
        }

        public int nameIndex() {
            return this.nameIndex;
        }

        public int length() {
            return this.length;
        }

        public Object clone() {
            throw new UnsupportedOperationException("Cannot clone Attribute!  (subclass: " + this.getClass() + ")");
        }
    }
    public class Field implements FieldInfo {
        private ClassInfo classInfo;
        private int modifiers;
        private int name;
        private int type;
        private Attribute[] attrs;
        private ConstantValue constantValue;

        Field(ClassInfo classInfo, int modifiers, int typeIndex, int nameIndex) {
            this.classInfo = classInfo;
            this.modifiers = modifiers;
            this.name = nameIndex;
            this.type = typeIndex;
            this.attrs = new Attribute[0];
            this.constantValue = null;
        }

        Field(ClassInfo classInfo, int modifiers, int typeIndex, int nameIndex, int cvNameIndex, int constantValueIndex) {
            this.classInfo = classInfo;
            this.modifiers = modifiers;
            this.name = nameIndex;
            this.type = typeIndex;
            this.constantValue = new ConstantValue(cvNameIndex, 2, constantValueIndex);
            this.attrs = new Attribute[1];
            this.attrs[0] = this.constantValue;
        }

        public Field(DataInputStream in, ClassInfo classInfo) throws IOException {
            this.classInfo = classInfo;
            this.modifiers = in.readUnsignedShort();
            this.name = in.readUnsignedShort();
            this.type = in.readUnsignedShort();
            int numAttributes = in.readUnsignedShort();
            this.attrs = new Attribute[numAttributes];

            for(int i = 0; i < numAttributes; ++i) {
                int nameIndex = in.readUnsignedShort();
                int length = in.readInt();
                ClassInfo.Constant name = classInfo.constants()[nameIndex];
                if (name != null && "ConstantValue".equals(name.value())) {
                    this.constantValue = new ConstantValue(in, nameIndex, length);
                    this.attrs[i] = this.constantValue;
                }

                if (this.attrs[i] == null) {
                    this.attrs[i] = new GenericAttribute(in, nameIndex, length);
                }
            }

        }

        public ClassInfo declaringClass() {
            return this.classInfo;
        }

        public void setNameIndex(int name) {
            this.name = name;
        }

        public void setTypeIndex(int type) {
            this.type = type;
        }

        public int nameIndex() {
            return this.name;
        }

        public int typeIndex() {
            return this.type;
        }

        public void setModifiers(int modifiers) {
            this.modifiers = modifiers;
        }

        public int modifiers() {
            return this.modifiers;
        }

        public int constantValue() {
            return this.constantValue != null ? this.constantValue.constantValueIndex() : 0;
        }

        public void setConstantValue(int index) {
            if (this.constantValue != null) {
                this.constantValue.setConstantValueIndex(index);
            }

        }

        public void write(DataOutputStream out) throws IOException {
            out.writeShort(this.modifiers);
            out.writeShort(this.name);
            out.writeShort(this.type);
            out.writeShort(this.attrs.length);

            for(int i = 0; i < this.attrs.length; ++i) {
                out.writeShort(this.attrs[i].nameIndex());
                out.writeInt(this.attrs[i].length());
                this.attrs[i].writeData(out);
            }

        }

        public String toString() {
            String x = "";
            x = x + " (modifiers";
            if ((this.modifiers & 1) != 0) {
                x = x + " PUBLIC";
            }

            if ((this.modifiers & 2) != 0) {
                x = x + " PRIVATE";
            }

            if ((this.modifiers & 4) != 0) {
                x = x + " PROTECTED";
            }

            if ((this.modifiers & 8) != 0) {
                x = x + " STATIC";
            }

            if ((this.modifiers & 16) != 0) {
                x = x + " FINAL";
            }

            if ((this.modifiers & 32) != 0) {
                x = x + " SYNCHRONIZED";
            }

            if ((this.modifiers & 64) != 0) {
                x = x + " VOLATILE";
            }

            if ((this.modifiers & 128) != 0) {
                x = x + " TRANSIENT";
            }

            if ((this.modifiers & 256) != 0) {
                x = x + " NATIVE";
            }

            if ((this.modifiers & 512) != 0) {
                x = x + " INTERFACE";
            }

            if ((this.modifiers & 1024) != 0) {
                x = x + " ABSTRACT";
            }

            x = x + ")";
            if (this.constantValue != null) {
                x = x + " " + this.constantValue;
            }

            return "(field " + this.name + " " + this.type + x + ")";
        }
    }
    public class GenericAttribute extends Attribute {
        private byte[] data;

        public GenericAttribute(DataInputStream in, int nameIndex, int length) throws IOException {
            super(nameIndex, length);
            this.data = new byte[length];

            for(int read = 0; read < length; read += in.read(this.data, read, length - read)) {
            }

        }

        public void writeData(DataOutputStream out) throws IOException {
            out.write(this.data, 0, this.data.length);
        }

        private GenericAttribute(GenericAttribute other) {
            super(other.nameIndex, other.length);
            this.data = new byte[other.data.length];
            System.arraycopy(other.data, 0, this.data, 0, other.data.length);
        }

        public Object clone() {
            return new GenericAttribute(this);
        }
    }
    public class Method implements MethodInfo {
        private ClassInfo classInfo;
        private int modifiers;
        private int name;
        private int type;
        private Attribute[] attrs;
        private Code code;
        private Exceptions exceptions;
        public static boolean DEBUG = Boolean.getBoolean("Method.DEBUG");

        Method(ClassInfo classInfo, int modifiers, int name, int type, Attribute[] attrs, Code code, Exceptions exceptions) {
            this.classInfo = classInfo;
            this.modifiers = modifiers;
            this.name = name;
            this.type = type;
            this.attrs = attrs;
            this.code = code;
            this.exceptions = exceptions;
        }

        public Method(DataInputStream in, ClassInfo classInfo) throws IOException {
            this.classInfo = classInfo;
            this.modifiers = in.readUnsignedShort();
            this.name = in.readUnsignedShort();
            this.type = in.readUnsignedShort();
            int numAttributes = in.readUnsignedShort();
            this.attrs = new Attribute[numAttributes];

            for(int i = 0; i < numAttributes; ++i) {
                int nameIndex = in.readUnsignedShort();
                int length = in.readInt();
                ClassInfo.Constant name = classInfo.constants()[nameIndex];
                if (name != null) {
                    if ("Code".equals(name.value())) {
                        this.code = new Code(classInfo, in, nameIndex, length);
                        this.attrs[i] = this.code;
                    } else if ("Exceptions".equals(name.value())) {
                        this.exceptions = new Exceptions(classInfo, in, nameIndex, length);
                        this.attrs[i] = this.exceptions;
                    }
                }

                if (this.attrs[i] == null) {
                    this.attrs[i] = new GenericAttribute(in, nameIndex, length);
                }
            }

        }

        public ClassInfo declaringClass() {
            return this.classInfo;
        }

        public void setNameIndex(int name) {
            this.name = name;
        }

        public void setTypeIndex(int type) {
            this.type = type;
        }

        public int nameIndex() {
            return this.name;
        }

        public int typeIndex() {
            return this.type;
        }

        public void setModifiers(int modifiers) {
            this.modifiers = modifiers;
        }

        public int modifiers() {
            return this.modifiers;
        }

        public int maxStack() {
            return this.code != null ? this.code.maxStack() : 0;
        }

        public void setMaxStack(int maxStack) {
            if (this.code != null) {
                this.code.setMaxStack(maxStack);
            }

        }

        public int maxLocals() {
            return this.code != null ? this.code.maxLocals() : 0;
        }

        public void setMaxLocals(int maxLocals) {
            if (this.code != null) {
                this.code.setMaxLocals(maxLocals);
            }

        }

        public byte[] code() {
            return this.code != null ? this.code.code() : new byte[0];
        }

        public int codeLength() {
            return this.code != null ? this.code.codeLength() : 0;
        }

        public void setCode(byte[] bytes) {
            if (this.code != null) {
                this.code.setCode(bytes);
                if (DEBUG) {
                    System.out.println("Set code with " + bytes.length + " bytes");
                }
            }

        }

        public int[] exceptionTypes() {
            return this.exceptions != null ? this.exceptions.exceptionTypes() : new int[0];
        }

        public void setLineNumbers(LineNumberDebugInfo[] lineNumbers) {
            if (this.code != null) {
                this.code.setLineNumbers(lineNumbers);
            }

        }

        public void setLocals(LocalDebugInfo[] locals) {
            if (this.code != null) {
                this.code.setLocals(locals);
            }

        }

        public LineNumberDebugInfo[] lineNumbers() {
            return this.code != null ? this.code.lineNumbers() : new LineNumberDebugInfo[0];
        }

        public LocalDebugInfo[] locals() {
            return this.code != null ? this.code.locals() : new LocalDebugInfo[0];
        }

        public Catch[] exceptionHandlers() {
            return this.code != null ? this.code.exceptionHandlers() : new Catch[0];
        }

        public void setExceptionHandlers(Catch[] handlers) {
            if (this.code != null) {
                this.code.setExceptionHandlers(handlers);
            }

        }

        public void write(DataOutputStream out) throws IOException {
            if (DEBUG) {
                System.out.println("Writing method " + this);
                System.out.println("  Masked Modifiers: " + (this.modifiers & '\uf000'));
            }

            out.writeShort(this.modifiers);
            out.writeShort(this.name);
            out.writeShort(this.type);
            out.writeShort(this.attrs.length);

            for(int i = 0; i < this.attrs.length; ++i) {
                if (DEBUG) {
                    System.out.println("  " + this.attrs[i]);
                }

                out.writeShort(this.attrs[i].nameIndex());
                out.writeInt(this.attrs[i].length());
                this.attrs[i].writeData(out);
            }

        }

        public String toString() {
            String x = "";
            x = x + " (modifiers";
            if ((this.modifiers & 1) != 0) {
                x = x + " PUBLIC";
            }

            if ((this.modifiers & 2) != 0) {
                x = x + " PRIVATE";
            }

            if ((this.modifiers & 4) != 0) {
                x = x + " PROTECTED";
            }

            if ((this.modifiers & 8) != 0) {
                x = x + " STATIC";
            }

            if ((this.modifiers & 16) != 0) {
                x = x + " FINAL";
            }

            if ((this.modifiers & 32) != 0) {
                x = x + " SYNCHRONIZED";
            }

            if ((this.modifiers & 64) != 0) {
                x = x + " VOLATILE";
            }

            if ((this.modifiers & 128) != 0) {
                x = x + " TRANSIENT";
            }

            if ((this.modifiers & 256) != 0) {
                x = x + " NATIVE";
            }

            if ((this.modifiers & 512) != 0) {
                x = x + " INTERFACE";
            }

            if ((this.modifiers & 1024) != 0) {
                x = x + " ABSTRACT";
            }

            x = x + ")";
            return "(method " + this.name + " " + this.type + x + (this.code != null ? "\n      " + this.code : "") + (this.exceptions != null ? "\n      " + this.exceptions : "") + ")";
        }

        private Method(ClassInfo classInfo, int modifiers, int name, int type, Attribute[] attrs) {
            this.classInfo = classInfo;
            this.modifiers = modifiers;
            this.name = name;
            this.type = type;
            if (attrs != null) {
                this.attrs = new Attribute[attrs.length];

                for(int i = 0; i < attrs.length; ++i) {
                    Attribute attr = (Attribute)attrs[i].clone();
                    if (attr instanceof Code) {
                        this.code = (Code)attr;
                    } else if (attr instanceof Exceptions) {
                        this.exceptions = (Exceptions)attr;
                    }

                    this.attrs[i] = attr;
                }
            }
        }

        public Object clone() {
            return new Method(this.classInfo, this.modifiers, this.name, this.type, this.attrs);
        }
    }
    public class LineNumberTable extends Attribute {
        private MethodInfo.LineNumberDebugInfo[] lineNumbers;

        public LineNumberTable(DataInputStream in, int nameIndex, int length) throws IOException {
            super(nameIndex, length);
            int numLines = in.readUnsignedShort();
            this.lineNumbers = new MethodInfo.LineNumberDebugInfo[numLines];

            for(int i = 0; i < this.lineNumbers.length; ++i) {
                int startPC = in.readUnsignedShort();
                int lineNumber = in.readUnsignedShort();
                this.lineNumbers[i] = new MethodInfo.LineNumberDebugInfo(startPC, lineNumber);
            }

        }

        public MethodInfo.LineNumberDebugInfo[] lineNumbers() {
            return this.lineNumbers;
        }

        public void setLineNumbers(MethodInfo.LineNumberDebugInfo[] lineNumbers) {
            this.lineNumbers = lineNumbers;
        }

        public int length() {
            return 2 + this.lineNumbers.length * 4;
        }

        public String toString() {
            String x = "(lines";

            for(int i = 0; i < this.lineNumbers.length; ++i) {
                x = x + "\n          (line #" + this.lineNumbers[i].lineNumber() + " pc=" + this.lineNumbers[i].startPC() + ")";
            }

            return x + ")";
        }

        public void writeData(DataOutputStream out) throws IOException {
            out.writeShort(this.lineNumbers.length);

            for(int i = 0; i < this.lineNumbers.length; ++i) {
                out.writeShort(this.lineNumbers[i].startPC());
                out.writeShort(this.lineNumbers[i].lineNumber());
            }

        }

        private LineNumberTable(LineNumberTable other) {
            super(other.nameIndex, other.length);
            this.lineNumbers = new MethodInfo.LineNumberDebugInfo[other.lineNumbers.length];

            for(int i = 0; i < other.lineNumbers.length; ++i) {
                this.lineNumbers[i] = (MethodInfo.LineNumberDebugInfo)other.lineNumbers[i].clone();
            }

        }

        public Object clone() {
            return new LineNumberTable(this);
        }
    }
    class LocalVariableTable extends Attribute {
        private MethodInfo.LocalDebugInfo[] locals;

        public LocalVariableTable(DataInputStream in, int index, int len) throws IOException {
            super(index, len);
            int numLocals = in.readUnsignedShort();
            this.locals = new MethodInfo.LocalDebugInfo[numLocals];

            for(int i = 0; i < this.locals.length; ++i) {
                int startPC = in.readUnsignedShort();
                int length = in.readUnsignedShort();
                int nameIndex = in.readUnsignedShort();
                int typeIndex = in.readUnsignedShort();
                int varIndex = in.readUnsignedShort();
                this.locals[i] = new MethodInfo.LocalDebugInfo(startPC, length, nameIndex, typeIndex, varIndex);
            }

        }

        public MethodInfo.LocalDebugInfo[] locals() {
            return this.locals;
        }

        public void setLocals(MethodInfo.LocalDebugInfo[] locals) {
            this.locals = locals;
        }

        public int length() {
            return 2 + this.locals.length * 10;
        }

        public String toString() {
            String x = "(locals";

            for(int i = 0; i < this.locals.length; ++i) {
                x = x + "\n          (local @" + this.locals[i].index() + " name=" + this.locals[i].nameIndex() + " type=" + this.locals[i].typeIndex() + " pc=" + this.locals[i].startPC() + ".." + (this.locals[i].startPC() + this.locals[i].length()) + ")";
            }

            return x + ")";
        }

        public void writeData(DataOutputStream out) throws IOException {
            out.writeShort(this.locals.length);

            for(int i = 0; i < this.locals.length; ++i) {
                out.writeShort(this.locals[i].startPC());
                out.writeShort(this.locals[i].length());
                out.writeShort(this.locals[i].nameIndex());
                out.writeShort(this.locals[i].typeIndex());
                out.writeShort(this.locals[i].index());
            }

        }

        private LocalVariableTable(LocalVariableTable other) {
            super(other.nameIndex, other.length);
            this.locals = new MethodInfo.LocalDebugInfo[other.locals.length];

            for(int i = 0; i < other.locals.length; ++i) {
                this.locals[i] = (MethodInfo.LocalDebugInfo)other.locals[i].clone();
            }

        }

        public Object clone() {
            return new LocalVariableTable(this);
        }
    }







}
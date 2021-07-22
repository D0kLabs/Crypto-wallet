/* This file is part of the db4o object database http://www.db4o.com

Copyright (C) 2004 - 2011  Versant Corporation http://www.versant.com

db4o is free software; you can redistribute it and/or modify it under
the terms of version 3 of the GNU General Public License as published
by the Free Software Foundation.

db4o is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along
with this program.  If not, see http://www.gnu.org/licenses/. */
package com.db4o.instrumentation.core;

import com.d0klabs.cryptowalt.data.ClassEditor;
import com.d0klabs.cryptowalt.data.ClassFormatException;
import com.d0klabs.cryptowalt.data.ClassInfo;
import com.d0klabs.cryptowalt.data.ClassInfoLoader;
import com.d0klabs.cryptowalt.data.ClassSource;
import com.d0klabs.cryptowalt.data.EditorContext;
import com.d0klabs.cryptowalt.data.FieldInfo;
import com.d0klabs.cryptowalt.data.FlowGraph;
import com.d0klabs.cryptowalt.data.InlineContext;
import com.d0klabs.cryptowalt.data.MemberRef;
import com.d0klabs.cryptowalt.data.MethodEditor;
import com.d0klabs.cryptowalt.data.MethodInfo;
import com.d0klabs.cryptowalt.data.NameAndType;
import com.d0klabs.cryptowalt.data.Type;
import com.db4o.instrumentation.bloat.BloatReferenceProvider;
import com.db4o.instrumentation.util.BloatUtil;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * @exclude
 */
public class BloatLoaderContext {
	private final EditorContext context;
	
	private final BloatReferenceProvider references;
	
	public BloatLoaderContext(ClassInfoLoader loader) {
		this(new CachingBloatContext(loader,new LinkedList(),false));
	}

	public BloatLoaderContext(EditorContext context) {
		this.context=context;
		this.references = new BloatReferenceProvider();
	}
	
	public BloatReferenceProvider references() {
		return references;
	}

	public FlowGraph flowGraph(String className, String methodName) throws ClassNotFoundException {
		return flowGraph(className, methodName, null);
	}
	
	public FlowGraph flowGraph(String className, String methodName,Type[] argTypes) throws ClassNotFoundException {
		ClassEditor classEdit = classEditor(className);
		return flowGraph(classEdit, methodName, argTypes);
	}

	public FlowGraph flowGraph(ClassEditor classEdit, String methodName,Type[] argTypes) throws ClassNotFoundException {
		MethodEditor method = method(classEdit, methodName, argTypes);
		return method == null ? null : new FlowGraph(method);
	}

	public MethodEditor method(ClassEditor classEdit, String methodName,Type[] argTypes) throws ClassNotFoundException {
		ClassEditor clazz = classEdit;
		while(clazz != null) {
			MethodInfo[] methods = clazz.methods();
			for (int methodIdx = 0; methodIdx < methods.length; methodIdx++) {
				MethodEditor methodEdit = context.editMethod(methods[methodIdx]);
				if (methodEdit.name().equals(methodName)&&signatureMatchesTypes(argTypes, methodEdit)) {
					return methodEdit;
				}
			}
			clazz = classEditor(clazz.superclass());
		}
		return null;
	}

	public EditorContext.FieldEditor field(ClassEditor classEdit, String fieldName, Type fieldType) throws ClassNotFoundException {
		ClassEditor clazz = classEdit;
		while(clazz != null) {
			FieldInfo[] fields = clazz.fields();
			for (int fieldIdx = 0; fieldIdx < fields.length; fieldIdx++) {
				FieldInfo fieldInfo=fields[fieldIdx];
				EditorContext.FieldEditor fieldEdit = context.editField(fieldInfo);
				if (fieldEdit.name().equals(fieldName)&&fieldType.equals(fieldEdit.type())) {
					return fieldEdit;
				}
			}
			clazz = classEditor(clazz.superclass());
		}
		return null;
	}

	public ClassEditor classEditor(Type type) throws ClassNotFoundException {
		return type == null ? null : classEditor(BloatUtil.normalizeClassName(type));
	}

	private boolean signatureMatchesTypes(Type[] argTypes,
			MethodEditor methodEdit) {
		if(argTypes==null) {
			return true;
		}
		Type[] sigTypes=methodEdit.paramTypes();
		int sigOffset=(methodEdit.isStatic()||methodEdit.isConstructor() ? 0 : 1);
		if(argTypes.length!=(sigTypes.length-sigOffset)) {
			return false;
		}
		for (int idx = 0; idx < argTypes.length; idx++) {
			if(!argTypes[idx].className().equals(sigTypes[idx+sigOffset].className())) {
				return false;
			}
		}
		return true;
	}

	public ClassEditor classEditor(String className) throws ClassNotFoundException {
		return context.editClass(className);
	}

	public ClassEditor classEditor(int modifiers, String className, Type superClass, Type[] interfaces) {
		return context.newClass(modifiers, className, superClass, interfaces);
	}
	
	public Type superType(Type type) throws ClassNotFoundException {
		return context.editClass(type).superclass();
	}
	
	public void commit() {
		try {
			context.commit();
		}
		catch(ConcurrentModificationException exc) {
			exc.printStackTrace();
			throw exc;
		}
	}
}
class CachingBloatContext extends PersistentBloatContext {
	protected Map classRC = new HashMap();
	protected Map methodRC = new HashMap();
	protected Map fieldRC = new HashMap();

	public CachingBloatContext(ClassInfoLoader loader, Collection classes, boolean closure) {
		super(loader, closure);
		this.addClasses(classes);
	}

	public ClassEditor newClass(int modifiers, String className, Type superType, Type[] interfaces) {
		ClassEditor ce = super.newClass(modifiers, className, superType, interfaces);
		ClassInfo info = ce.classInfo();
		this.classRC.put(info, new Integer(1));
		return ce;
	}

	public ClassEditor editClass(ClassInfo info) {
		ClassEditor ce = (ClassEditor)this.classEditors.get(info);
		if (ce == null) {
			ce = new ClassEditor(this, info);
			this.classEditors.put(info, ce);
			this.classRC.put(info, new Integer(1));
			if (!this.classInfos.containsValue(info)) {
				String className = ce.name().intern();
				BloatContext.db("editClass(ClassInfo): " + className + " -> " + info);
				this.classInfos.put(className, info);
			}
		} else {
			Integer rc = (Integer)this.classRC.get(info);
			this.classRC.put(info, new Integer(rc + 1));
		}

		return ce;
	}

	public MethodEditor editMethod(MemberRef method) throws NoSuchMethodException {
		MethodInfo info = (MethodInfo)this.methodInfos.get(method);
		if (info == null) {
			BloatContext.db("Creating a new MethodEditor for " + method);
			NameAndType nat = method.nameAndType();
			String name = nat.name();
			Type type = nat.type();

			try {
				ClassEditor ce = this.editClass(method.declaringClass());
				MethodInfo[] methods = ce.methods();

				for(int i = 0; i < methods.length; ++i) {
					MethodEditor me = this.editMethod(methods[i]);
					if (me.name().equals(name) && me.type().equals(type)) {
						this.methodInfos.put(method, methods[i]);
						this.release(ce.classInfo());
						return me;
					}
				}

				this.release(ce.classInfo());
				throw new NoSuchMethodException(method.toString());
			} catch (ClassNotFoundException var10) {
				throw new NoSuchMethodException(method.toString() + "(" + var10.getMessage() + ")");
			} catch (ClassFormatException var11) {
				throw new NoSuchMethodException(method.toString() + "(" + var11.getMessage() + ")");
			}
		} else {
			return this.editMethod(info);
		}
	}

	public MethodEditor editMethod(MethodInfo info) {
		MethodEditor me = (MethodEditor)this.methodEditors.get(info);
		if (me == null) {
			ClassInfo classInfo = info.declaringClass();
			me = new MethodEditor(this.editClass(classInfo), info);
			this.release(classInfo);
			this.methodEditors.put(info, me);
			this.methodRC.put(info, new Integer(1));
			BloatContext.db("Creating a new MethodEditor for " + me.memberRef());
		} else {
			Integer rc = (Integer)this.methodRC.get(info);
			this.methodRC.put(info, new Integer(rc + 1));
		}

		return me;
	}

	public EditorContext.FieldEditor editField(MemberRef field) throws NoSuchFieldException {
		FieldInfo info = (FieldInfo)this.fieldInfos.get(field);
		if (info == null) {
			NameAndType nat = field.nameAndType();
			String name = nat.name();
			Type type = nat.type();

			try {
				ClassEditor ce = this.editClass(field.declaringClass());
				FieldInfo[] fields = ce.fields();

				for(int i = 0; i < fields.length; ++i) {
					EditorContext.FieldEditor fe = this.editField(fields[i]);
					if (fe.name().equals(name) && fe.type().equals(type)) {
						this.fieldInfos.put(field, fields[i]);
						this.release(ce.classInfo());
						return fe;
					}

					this.release(fields[i]);
				}

				this.release(ce.classInfo());
			} catch (ClassNotFoundException var10) {
			} catch (ClassFormatException var11) {
			}

			throw new NoSuchFieldException(field.toString());
		} else {
			return this.editField(info);
		}
	}

	public EditorContext.FieldEditor editField(FieldInfo info) {
		EditorContext.FieldEditor fe = (EditorContext.FieldEditor)this.fieldEditors.get(info);
		BloatContext.db("Editing " + info);
		if (fe == null) {
			ClassInfo classInfo = info.declaringClass();
			fe = new EditorContext.FieldEditor(this.editClass(classInfo), info);
			this.release(classInfo);
			this.fieldEditors.put(info, fe);
			this.fieldRC.put(info, new Integer(0));
			BloatContext.db("Creating a new FieldEditor for " + fe.nameAndType());
		} else {
			Integer rc = (Integer)this.fieldRC.get(info);
			this.fieldRC.put(info, new Integer(rc + 1));
		}

		return fe;
	}

	public void release(ClassInfo info) {
		Integer rc = (Integer)this.classRC.get(info);
		if (rc != null && rc > 1) {
			this.classRC.put(info, new Integer(rc - 1));
		} else {
			ClassEditor ce = (ClassEditor)this.classEditors.get(info);
			if (ce == null || !ce.isDirty()) {
				ce = (ClassEditor)this.classEditors.remove(info);
				this.classRC.remove(info);
				this.classEditors.remove(info);
				Iterator iter = this.classInfos.keySet().iterator();

				while(iter.hasNext()) {
					String name = (String)iter.next();
					ClassInfo info2 = (ClassInfo)this.classInfos.get(name);
					if (info2 == info) {
						BloatContext.db("Removing ClassInfo: " + name + " -> " + info2);
						this.classInfos.remove(name);
						break;
					}
				}

				if (ce != null) {
					MethodInfo[] methods = ce.methods();

					for(int i = 0; i < methods.length; ++i) {
						this.release(methods[i]);
					}

					FieldInfo[] fields = ce.fields();

					for(int i = 0; i < fields.length; ++i) {
						this.release(fields[i]);
					}
				}

			}
		}
	}

	public void release(MethodInfo info) {
		Integer rc = (Integer)this.classRC.get(info);
		if (rc != null && rc > 1) {
			this.methodRC.put(info, new Integer(rc - 1));
		} else {
			MethodEditor me = (MethodEditor)this.methodEditors.get(info);
			if (me == null || !me.isDirty()) {
				this.methodRC.remove(info);
				this.methodEditors.remove(info);
				Iterator iter = this.methodInfos.keySet().iterator();

				while(iter.hasNext()) {
					MemberRef ref = (MemberRef)iter.next();
					MethodInfo info2 = (MethodInfo)this.methodInfos.get(ref);
					if (info2 == info) {
						this.methodInfos.remove(ref);
						break;
					}
				}

			}
		}
	}

	public void release(FieldInfo info) {
		Integer rc = (Integer)this.fieldRC.get(info);
		BloatContext.db("Releasing " + info);
		if (rc != null && rc > 1) {
			this.fieldRC.put(info, new Integer(rc - 1));
		} else {
			EditorContext.FieldEditor fe = (EditorContext.FieldEditor)this.fieldEditors.get(info);
			if (fe == null || !fe.isDirty()) {
				this.fieldRC.remove(info);
				this.fieldEditors.remove(info);
				Iterator iter = this.fieldInfos.keySet().iterator();

				while(iter.hasNext()) {
					MemberRef ref = (MemberRef)iter.next();
					FieldInfo info2 = (FieldInfo)this.fieldInfos.get(ref);
					if (info2 == info) {
						this.fieldInfos.remove(ref);
						break;
					}
				}

			}
		}
	}

	public void commit(ClassInfo info) {
		super.commit(info);
		this.classEditors.remove(info);
		this.classRC.remove(info);
	}

	public void commit(MethodInfo info) {
		super.commit(info);
		this.methodEditors.remove(info);
		this.methodRC.remove(info);
	}

	public void commit(FieldInfo info) {
		super.commit(info);
		this.fieldEditors.remove(info);
		this.fieldRC.remove(info);
	}

	public void commit() {
		Collection fieldValues = this.fieldEditors.values();
		EditorContext.FieldEditor[] fieldArray = (EditorContext.FieldEditor[])fieldValues.toArray(new EditorContext.FieldEditor[fieldValues.size()]);

		for(int i = 0; i < fieldArray.length; ++i) {
			EditorContext.FieldEditor fe = fieldArray[i];
			this.commit(fe.fieldInfo());
		}

		Collection methodValues = this.methodEditors.values();
		MethodEditor[] methodArray = (MethodEditor[])methodValues.toArray(new MethodEditor[methodValues.size()]);

		for(int i = 0; i < methodArray.length; ++i) {
			MethodEditor me = methodArray[i];
			this.commit(me.methodInfo());
		}

		Collection classValues = this.classEditors.values();
		ClassEditor[] classArray = (ClassEditor[])classValues.toArray(new ClassEditor[classValues.size()]);

		for(int i = 0; i < classArray.length; ++i) {
			ClassEditor ce = classArray[i];
			this.commit(ce.classInfo());
		}

	}

	public String toString() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		pw.println("Context of caches in CachingBloatContext...");
		pw.println("  Class Infos");
		Iterator iter = this.classInfos.keySet().iterator();

		Object key;
		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.classInfos.get(key));
		}

		pw.println("  Class Editors");
		iter = this.classEditors.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.classEditors.get(key));
		}

		pw.println("  Class RC");
		iter = this.classRC.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.classRC.get(key));
		}

		pw.println("  Method Infos");
		iter = this.methodInfos.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.methodInfos.get(key));
		}

		pw.println("  Method Editors");
		iter = this.methodEditors.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.methodEditors.get(key));
		}

		pw.println("  Method RC");
		iter = this.methodRC.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.methodRC.get(key));
		}

		pw.println("  Field Infos");
		iter = this.fieldInfos.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.fieldInfos.get(key));
		}

		pw.println("  Field Editors");
		iter = this.fieldEditors.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.fieldEditors.get(key));
		}

		pw.println("  Field RC");
		iter = this.fieldRC.keySet().iterator();

		while(iter.hasNext()) {
			key = iter.next();
			pw.println("    " + key + " -> " + this.fieldRC.get(key));
		}

		return sw.toString();
	}
}
class PersistentBloatContext extends BloatContext {
	protected final EditorContext.ClassHierarchy hierarchy;
	protected Map classInfos;
	protected Map methodInfos;
	protected Map fieldInfos;
	protected Map classEditors;
	protected Map methodEditors;
	protected Map fieldEditors;
	public static boolean DB_COMMIT = false;

	protected static void comm(String s) {
		if (DB_COMMIT || BloatContext.DEBUG) {
			System.out.println(s);
		}

	}

	public PersistentBloatContext(ClassInfoLoader loader) {
		this(loader, true);
	}

	protected PersistentBloatContext(ClassInfoLoader loader, boolean closure) {
		super(loader);
		BloatContext.db("Creating a new BloatContext");
		this.classInfos = new HashMap();
		this.methodInfos = new HashMap();
		this.fieldInfos = new HashMap();
		this.classEditors = new HashMap();
		this.methodEditors = new HashMap();
		this.fieldEditors = new HashMap();
		this.hierarchy = new EditorContext.ClassHierarchy(this, new ArrayList(), closure);
	}

	protected void addClasses(Collection classes) {
		Iterator iter = classes.iterator();

		while(iter.hasNext()) {
			String className = (String)iter.next();
			this.hierarchy.addClassNamed(className);
		}

	}

	public ClassInfo loadClass(String className) throws ClassNotFoundException {
		className = className.replace('.', '/').intern();
		ClassInfo info = (ClassInfo)this.classInfos.get(className);
		if (info == null) {
			BloatContext.db("BloatContext: Loading class " + className);
			info = this.loader.loadClass(className);
			this.hierarchy.addClassNamed(className);
			BloatContext.db("loadClass: " + className + " -> " + info);
			this.classInfos.put(className, info);
		}

		return info;
	}

	public ClassInfo newClassInfo(int modifiers, int classIndex, int superClassIndex, int[] interfaceIndexes, List constants) {
		return this.loader.newClass(modifiers, classIndex, superClassIndex, interfaceIndexes, constants);
	}

	public EditorContext.ClassHierarchy getHierarchy() {
		return this.hierarchy;
	}

	public ClassEditor newClass(int modifiers, String className, Type superType, Type[] interfaces) {
		ClassEditor ce = new ClassEditor(this, modifiers, className, superType, interfaces);
		ClassInfo info = ce.classInfo();
		className = ce.name().intern();
		BloatContext.db("editClass(ClassInfo): " + className + " -> " + info);
		this.classInfos.put(className, info);
		this.classEditors.put(info, ce);
		return ce;
	}

	public ClassEditor editClass(String className) throws ClassNotFoundException, ClassFormatException {
		className = className.intern();
		ClassInfo info = (ClassInfo)this.classInfos.get(className);
		if (info == null) {
			info = this.loadClass(className);
		}

		return this.editClass(info);
	}

	public ClassEditor editClass(Type classType) throws ClassNotFoundException, ClassFormatException {
		return this.editClass(classType.className());
	}

	public ClassEditor editClass(ClassInfo info) {
		ClassEditor ce = (ClassEditor)this.classEditors.get(info);
		if (ce == null) {
			ce = new ClassEditor(this, info);
			this.classEditors.put(info, ce);
			if (!this.classInfos.containsValue(info)) {
				String className = ce.name().intern();
				BloatContext.db("editClass(ClassInfo): " + className + " -> " + info);
				this.classInfos.put(className, info);
			}
		}

		return ce;
	}

	public MethodEditor editMethod(MemberRef method) throws NoSuchMethodException {
		MethodInfo info = (MethodInfo)this.methodInfos.get(method);
		if (info == null) {
			BloatContext.db("Creating a new MethodEditor for " + method);
			NameAndType nat = method.nameAndType();
			String name = nat.name();
			Type type = nat.type();

			try {
				ClassEditor ce = this.editClass(method.declaringClass());
				MethodInfo[] methods = ce.methods();

				for(int i = 0; i < methods.length; ++i) {
					MethodEditor me = this.editMethod(methods[i]);
					if (me.name().equals(name) && me.type().equals(type)) {
						this.methodInfos.put(method, methods[i]);
						this.release(ce.classInfo());
						return me;
					}

					this.release(methods[i]);
				}
			} catch (ClassNotFoundException var10) {
			} catch (ClassFormatException var11) {
			}

			throw new NoSuchMethodException(method.toString());
		} else {
			return this.editMethod(info);
		}
	}

	public MethodEditor editMethod(MethodInfo info) {
		MethodEditor me = (MethodEditor)this.methodEditors.get(info);
		if (me == null) {
			me = new MethodEditor(this.editClass(info.declaringClass()), info);
			this.methodEditors.put(info, me);
			BloatContext.db("Creating a new MethodEditor for " + me.memberRef());
		}

		return me;
	}

	public EditorContext.FieldEditor editField(MemberRef field) throws NoSuchFieldException {
		FieldInfo info = (FieldInfo)this.fieldInfos.get(field);
		if (info == null) {
			NameAndType nat = field.nameAndType();
			String name = nat.name();
			Type type = nat.type();

			try {
				ClassEditor ce = this.editClass(field.declaringClass());
				FieldInfo[] fields = ce.fields();

				for(int i = 0; i < fields.length; ++i) {
					FieldEditor fe = this.editField(fields[i]);
					if (fe.name().equals(name) && fe.type().equals(type)) {
						this.fieldInfos.put(field, fields[i]);
						this.release(ce.classInfo());
						return fe;
					}

					this.release(fields[i]);
				}
			} catch (ClassNotFoundException var10) {
			} catch (ClassFormatException var11) {
			}

			throw new NoSuchFieldException(field.toString());
		} else {
			return this.editField(info);
		}
	}

	public FieldEditor editField(FieldInfo info) {
		FieldEditor fe = (FieldEditor)this.fieldEditors.get(info);
		if (fe == null) {
			fe = new FieldEditor(this.editClass(info.declaringClass()), info);
			this.fieldEditors.put(info, fe);
			BloatContext.db("Creating a new FieldEditor for " + fe.nameAndType());
		}

		return fe;
	}

	public void release(ClassInfo info) {
	}

	public void release(ClassEditor ce) {
	}

	public void release(MethodInfo info) {
	}

	public void release(FieldInfo info) {
	}

	public void commit(ClassInfo info) {
		Type type = Type.getType("L" + info.name() + ";");
		if (!this.ignoreClass(type)) {
			ClassEditor ce = this.editClass(info);
			MethodInfo[] methods = ce.methods();

			for(int i = 0; i < methods.length; ++i) {
				this.commit(methods[i]);
			}

			FieldInfo[] fields = ce.fields();

			for(int i = 0; i < fields.length; ++i) {
				this.commit(fields[i]);
			}

			ce.commit();
			ce.setDirty(false);
			this.release(info);
		}
	}

	public void commit(MethodInfo info) {
		MethodEditor me = this.editMethod(info);
		me.commit();
		me.declaringClass().setDirty(true);
		me.setDirty(false);
		this.release(info);
	}

	public void commit(FieldInfo info) {
		EditorContext.FieldEditor fe = this.editField(info);
		fe.commit();
		fe.declaringClass().setDirty(true);
		fe.setDirty(false);
		this.release(info);
	}

	public void commit() {
		Object[] array = this.fieldEditors.values().toArray();

		int i;
		for(i = 0; i < array.length; ++i) {
			EditorContext.FieldEditor fe = (EditorContext.FieldEditor)array[i];
			if (!this.ignoreField(fe.memberRef())) {
				this.commit(fe.fieldInfo());
			}
		}

		array = this.methodEditors.values().toArray();

		for(i = 0; i < array.length; ++i) {
			MethodEditor me = (MethodEditor)array[i];
			if (!this.ignoreMethod(me.memberRef())) {
				this.commit(me.methodInfo());
			}
		}

		array = this.classEditors.values().toArray();

		for(i = 0; i < array.length; ++i) {
			ClassEditor ce = (ClassEditor)array[i];
			if (!this.ignoreClass(ce.type())) {
				this.commit(ce.classInfo());
			}
		}

	}

	public void commitDirty() {
		comm("Committing dirty data");
		Object[] array = this.fieldEditors.values().toArray();

		int i;
		for(i = 0; i < array.length; ++i) {
			EditorContext.FieldEditor fe = (EditorContext.FieldEditor)array[i];
			if (fe.isDirty() && !this.ignoreField(fe.memberRef())) {
				comm("  Committing field: " + fe.declaringClass().name() + "." + fe.name());
				this.commit(fe.fieldInfo());
			}
		}

		array = this.methodEditors.values().toArray();

		for(i = 0; i < array.length; ++i) {
			MethodEditor me = (MethodEditor)array[i];
			if (me.isDirty() && !this.ignoreMethod(me.memberRef())) {
				comm("  Committing method: " + me.declaringClass().name() + "." + me.name() + me.type());
				this.commit(me.methodInfo());
			}
		}

		array = this.classEditors.values().toArray();

		for(i = 0; i < array.length; ++i) {
			ClassEditor ce = (ClassEditor)array[i];
			if (ce.isDirty() && !this.ignoreClass(ce.type())) {
				comm("  Committing class: " + ce.name());
				this.commit(ce.classInfo());
			}
		}

	}
}
abstract class BloatContext implements InlineContext {
	public static boolean DEBUG = Boolean.getBoolean("BloatContext.DEBUG");
	protected InlineStats inlineStats;
	protected Set ignorePackages = new HashSet();
	protected Set ignoreClasses = new HashSet();
	protected Set ignoreMethods = new HashSet();
	protected Set ignoreFields = new HashSet();
	protected boolean ignoreSystem = false;
	protected CallGraph callGraph;
	protected Set roots;
	protected ClassInfoLoader loader;
	private static ClassLoader systemCL;

	static {
		String s = "";
		systemCL = "".getClass().getClassLoader();
	}

	protected static void db(String s) {
		if (DEBUG) {
			System.out.println(s);
		}

	}

	public BloatContext(ClassInfoLoader loader) {
		this.loader = loader;
	}

	public static boolean isSystem(Type type) {
		Class c = null;

		try {
			c = Class.forName(type.className().replace('/', '.'));
		} catch (ClassNotFoundException var3) {
			System.err.println("** Could not find class " + type.className());
			var3.printStackTrace(System.err);
			System.exit(1);
		}

		return c.getClassLoader() == systemCL;
	}

	public void setRootMethods(Set roots) {
		if (this.callGraph != null) {
			throw new IllegalStateException("Cannot set roots after call graph has been created");
		} else {
			this.roots = roots;
		}
	}

	public CallGraph getCallGraph() {
		if (this.callGraph == null) {
			this.callGraph = new CallGraph(this, this.roots);
		}

		return this.callGraph;
	}

	public InlineStats getInlineStats() {
		if (this.inlineStats == null) {
			this.inlineStats = new InlineStats();
		}

		return this.inlineStats;
	}

	public void addIgnorePackage(String name) {
		name = name.replace('.', '/');
		this.ignorePackages.add(name);
	}

	public void addIgnoreClass(Type type) {
		this.ignoreClasses.add(type);
	}

	public void addIgnoreMethod(MemberRef method) {
		this.ignoreMethods.add(method);
	}

	public void addIgnoreField(MemberRef field) {
		this.ignoreFields.add(field);
	}

	public void setIgnoreSystem(boolean ignore) {
		this.ignoreSystem = ignore;
	}

	public boolean ignoreClass(Type type) {
		if (this.ignoreClasses.contains(type)) {
			return true;
		} else if (type.isPrimitive()) {
			this.addIgnoreClass(type);
			return true;
		} else if (this.ignoreSystem && isSystem(type)) {
			this.addIgnoreClass(type);
			return true;
		} else {
			String packageName = type.className();
			int lastSlash = packageName.lastIndexOf(47);
			if (lastSlash == -1) {
				return false;
			} else {
				packageName.substring(0, lastSlash);
				Iterator packages = this.ignorePackages.iterator();

				while(packages.hasNext()) {
					String s = (String)packages.next();
					if (type.className().startsWith(s)) {
						this.addIgnoreClass(type);
						return true;
					}
				}

				return false;
			}
		}
	}

	public boolean ignoreMethod(MemberRef method) {
		if (this.ignoreMethods.contains(method)) {
			return true;
		} else if (this.ignoreClass(method.declaringClass())) {
			this.addIgnoreMethod(method);
			return true;
		} else {
			return false;
		}
	}

	public boolean ignoreField(MemberRef field) {
		if (this.ignoreMethods.contains(field)) {
			return true;
		} else if (this.ignoreClass(field.declaringClass())) {
			this.addIgnoreField(field);
			return true;
		} else {
			return false;
		}
	}

	public abstract void commitDirty();

	public static void main(String[] args) {
		PrintWriter out = new PrintWriter(System.out, true);
		PrintWriter err = new PrintWriter(System.err, true);
		BloatContext context = new CachingBloatContext(new ClassFileLoader(), new ArrayList(), false);
		List types = new ArrayList();

		for(int i = 0; i < args.length; ++i) {
			if (args[i].equals("-ip")) {
				++i;
				if (i >= args.length) {
					err.println("** Missing package name");
					System.exit(1);
				}

				out.println("Ignoring package " + args[i]);
				context.addIgnorePackage(args[i]);
			} else {
				String type;
				if (args[i].equals("-ic")) {
					++i;
					if (i >= args.length) {
						err.println("** Missing class name");
						System.exit(1);
					}

					out.println("Ignoring class " + args[i]);
					type = args[i].replace('.', '/');
					context.addIgnoreClass(Type.getType("L" + type + ";"));
				} else {
					type = args[i].replace('.', '/');
					types.add(Type.getType("L" + type + ";"));
				}
			}
		}

		out.println("");
		Iterator iter = types.iterator();

		while(iter.hasNext()) {
			Type type = (Type)iter.next();
			out.println("Ignore " + type + "? " + context.ignoreClass(type));
		}

	}
}
class ClassFileLoader implements ClassInfoLoader {
	public static boolean DEBUG = false;
	public static boolean USE_SYSTEM_CLASSES = true;
	private File outputDir;
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
		this(new ClassSource.DefaultClassSource());
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
		ClassSource.ClassFile file = new ClassSource.ClassFile(inputFile, this, in);
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

	public ClassInfo[] loadClassesFromZipFile(ZipFile zipFile) throws ClassNotFoundException {
		ClassInfo[] infos = new ClassInfo[zipFile.size()];
		Enumeration entries = zipFile.entries();

		for(int i = 0; entries.hasMoreElements(); ++i) {
			ZipEntry entry = (ZipEntry)entries.nextElement();
			if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
				try {
					InputStream stream = zipFile.getInputStream(entry);
					File file = new File(entry.getName());
					infos[i] = this.loadClassFromStream(file, stream);
				} catch (IOException var8) {
					System.err.println("IOException: " + var8);
				}
			}
		}

		return infos;
	}

	public ClassInfo newClass(int modifiers, int classIndex, int superClassIndex, int[] interfaceIndexes, List constants) {
		return new ClassSource.ClassFile(modifiers, classIndex, superClassIndex, interfaceIndexes, constants, this);
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
				file = (ClassSource.ClassFile)iter.next();
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



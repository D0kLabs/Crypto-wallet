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
package com.db4o.internal;

import com.db4o.ObjectSet;
import com.db4o.config.Configuration;
import com.db4o.ext.DatabaseClosedException;
import com.db4o.ext.DatabaseReadOnlyException;
import com.db4o.ext.Db4oDatabase;
import com.db4o.ext.Db4oUUID;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.InvalidIDException;
import com.db4o.ext.ObjectInfo;
import com.db4o.ext.StoredClass;
import com.db4o.foundation.NotSupportedException;
import com.db4o.internal.activation.ActivationMode;
import com.db4o.internal.activation.NullModifiedObjectQuery;
import com.db4o.internal.activation.UpdateDepth;
import com.db4o.io.Storage;
import com.db4o.query.Predicate;
import com.db4o.query.Query;
import com.db4o.query.QueryComparator;

/**
 * @exclude
 */
public abstract class ExternalObjectContainer extends ObjectContainerBase {
    
    public ExternalObjectContainer(Configuration config) {
        super(config);
    }
    
    public final void activate(Object obj){
        activate(null, obj);
    }
    
    public final void activate(Object obj, int depth) throws DatabaseClosedException {
        activate(null, obj, activationDepthProvider().activationDepth(depth, ActivationMode.ACTIVATE));
    }
    
    public final void deactivate(Object obj) {
    	deactivate(null, obj);
    }
    
    public final void bind(Object obj, long id) throws IllegalArgumentException {
        bind(null, obj, id);
    }
    
    public final void commit() throws DatabaseReadOnlyException, DatabaseClosedException {
        commit(null);
    }
    
    public final void deactivate(Object obj, int depth) throws DatabaseClosedException {
        deactivate(null, obj, depth);
    }

    public final void delete(Object a_object) {
        delete(null, a_object);
    }
    
    public Object descend(Object obj, String[] path){
        return descend(null, obj, path);
    }
    
    public ExtObjectContainer ext() {
        return this;
    }
    
	public final ObjectSet queryByExample(Object template) throws DatabaseClosedException {
        return queryByExample(null, template);
    }

    public final Object getByID(long id) throws DatabaseClosedException, InvalidIDException  {
        return getByID(null, id);
    }

    public final Object getByUUID(Db4oUUID uuid){
        return getByUUID(null, uuid);
    }
    
    public final long getID(Object obj) {
        return getID(null, obj);
    }

    public final ObjectInfo getObjectInfo (Object obj){
        return getObjectInfo(null, obj);
    }
    
    public boolean isActive(Object obj) {
        return isActive(null, obj);
    }

    public boolean isCached(long id) {
        return isCached(null, id); 
    }

    public boolean isStored(Object obj) {
        return isStored(null, obj);
    }

    public final Object peekPersisted(Object obj, int depth, boolean committed) throws DatabaseClosedException {
        return peekPersisted(null, obj, activationDepthProvider().activationDepth(depth, ActivationMode.PEEK), committed);
    }

    public final void purge(Object obj) {
        purge(null, obj);
    }

    public Query query() {
        return query((Transaction)null);
    }
    
    public final ObjectSet query(Class clazz) {
        return queryByExample(clazz);
    }
    
    public final ObjectSet query(Predicate predicate){
        return query(predicate,(QueryComparator)null);
    }
    
    public final ObjectSet query(Predicate predicate,QueryComparator comparator){
        return query(null, predicate, comparator);
    }

    public final void refresh(Object obj, int depth) {
        refresh(null, obj, depth);
    }
    
    public final void rollback() {
        rollback(null);
    }
    
	public final void store(Object obj) 
        throws DatabaseClosedException, DatabaseReadOnlyException {
        store(obj, Const4.UNSPECIFIED);
    }

	public final void store(Object obj, int depth) 
        throws DatabaseClosedException, DatabaseReadOnlyException {
		store(null, obj, depth == Const4.UNSPECIFIED ? (UpdateDepth)updateDepthProvider().unspecified(NullModifiedObjectQuery.INSTANCE) : (UpdateDepth)updateDepthProvider().forDepth(depth));
    }
    
    public final StoredClass storedClass(Object clazz) {
        return storedClass(null, clazz);
    }
    
    public StoredClass[] storedClasses() {
        return storedClasses(null);
    }
    
    public abstract void backup(Storage targetStorage, String path) throws Db4oIOException, DatabaseClosedException,
    	NotSupportedException;

    public abstract Db4oDatabase identity();
    
}

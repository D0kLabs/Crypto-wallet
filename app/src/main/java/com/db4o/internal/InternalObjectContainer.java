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

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.callbacks.*;
import com.db4o.internal.events.*;
import com.db4o.internal.query.*;
import com.db4o.reflect.*;

/**
 * @exclude
 * @sharpen.partial
 */
public interface InternalObjectContainer extends ExtObjectContainer {
    
    void callbacks(Callbacks cb);
    
    Callbacks callbacks();
    
    /**
     * @sharpen.property
     */
    ObjectContainerBase container();
    
    /**
     * @sharpen.property
     */
    Transaction transaction();
    
    NativeQueryHandler getNativeQueryHandler();

    ClassMetadata classMetadataForReflectClass(ReflectClass reflectClass);

    ClassMetadata classMetadataForName(String name);
    
    ClassMetadata classMetadataForID(int id);

    /**
     * @sharpen.property
     */
    HandlerRegistry handlers();
    
    /**
     * @sharpen.property
     */
    Config4Impl configImpl();
    
    <R> R syncExec(Closure4<R> block);
    
    int instanceCount(ClassMetadata clazz, Transaction trans);
    
    /**
     * @sharpen.property
     */
    boolean isClient();

	void storeAll(Transaction trans, Iterator4 objects);

	UpdateDepthProvider updateDepthProvider();

	EventRegistryImpl newEventRegistry();
}

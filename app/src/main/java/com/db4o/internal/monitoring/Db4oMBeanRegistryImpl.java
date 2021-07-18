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
package com.db4o.internal.monitoring;

import com.db4o.ObjectContainer;
import com.db4o.events.Event4;
import com.db4o.events.EventListener4;
import com.db4o.events.EventRegistry;
import com.db4o.events.EventRegistryFactory;
import com.db4o.events.ObjectContainerEventArgs;
import com.db4o.monitoring.Db4oMBean;
import com.db4o.monitoring.Db4oMBeanRegistry;

import java.util.HashSet;
import java.util.Set;

import static com.db4o.foundation.Environments.my;

/**
 * @exclude
 */
//@decaf.Ignore
public class Db4oMBeanRegistryImpl implements Db4oMBeanRegistry {

	private final Set<Db4oMBean> _beans = new HashSet<Db4oMBean>();
	
	public Db4oMBeanRegistryImpl() {
		ObjectContainer db = my(ObjectContainer.class);
		EventRegistry eventRegistry = EventRegistryFactory.forObjectContainer(db);
		eventRegistry.opened().addListener(new EventListener4<ObjectContainerEventArgs>() {
			public void onEvent(Event4<ObjectContainerEventArgs> event, ObjectContainerEventArgs args) {
				register();
			}
		});
		eventRegistry.closing().addListener(new EventListener4<ObjectContainerEventArgs>() {
			public void onEvent(Event4<ObjectContainerEventArgs> event, ObjectContainerEventArgs args) {
				unregister();
			}
		});
	}
	
	public void add(Db4oMBean bean) {
		_beans.add(bean);
	}

	public void register() {
		for (Db4oMBean bean : _beans) {
				bean.register();
		}
	}

	public void unregister() {
		for (Db4oMBean bean : _beans) {
				bean.unregister();
		}
	}

}

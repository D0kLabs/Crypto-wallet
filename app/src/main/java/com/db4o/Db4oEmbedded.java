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
package com.db4o;

import com.db4o.config.EmbeddedConfiguration;
import com.db4o.foundation.ArgumentNullException;
import com.db4o.internal.ObjectContainerFactory;
import com.db4o.internal.config.EmbeddedConfigurationImpl;

/**
 * Factory class to open db4o instances in embedded mode.
 * 
 * <br><br>
 * @see com.db4o.cs.Db4oClientServer class in
 * db4o-[version]-cs-java[java-version].jar
 * for methods to open db4o servers and db4o clients.
 * @since 7.5
 * 
 */
public class Db4oEmbedded {

	public static EmbeddedConfiguration newConfiguration() {
		return new EmbeddedConfigurationImpl(Db4o.newConfiguration());
	}


	public static final EmbeddedObjectContainer openFile(EmbeddedConfiguration config) {
		if (null == config) {
			throw new ArgumentNullException();
		}
		return ObjectContainerFactory.openObjectContainer(config);
	}

	public static final EmbeddedObjectContainer openFile() {
		return openFile(newConfiguration());
	}

}

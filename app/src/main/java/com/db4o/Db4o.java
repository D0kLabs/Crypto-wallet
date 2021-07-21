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
package  com.db4o;

import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.config.*;

public class Db4o {
	
	static final Config4Impl i_config = new Config4Impl();
	
	static {
		Platform4.getDefaultConfiguration(i_config);
	}


	public static Configuration configure(){
		return i_config;
	}

	public static Configuration newConfiguration() {
		Config4Impl config = new Config4Impl();
		Platform4.getDefaultConfiguration(config);
		return config;
	}

	public static Configuration cloneConfiguration() {
		return (Config4Impl) ((DeepClone) Db4o.configure()).deepClone(null);
	}

	public static final ObjectContainer openFile(String databaseFileName)
			throws DatabaseFileLockedException,
			IncompatibleFileFormatException, OldFormatException, DatabaseReadOnlyException {
		return Db4o.openFile(cloneConfiguration(),databaseFileName);
	}


	public static final ObjectContainer openFile(Configuration config,
			String databaseFileName) throws DatabaseFileLockedException, IncompatibleFileFormatException,
			OldFormatException, DatabaseReadOnlyException {

		return ObjectContainerFactory.openObjectContainer(Db4oLegacyConfigurationBridge.asEmbeddedConfiguration(config));
	}


}

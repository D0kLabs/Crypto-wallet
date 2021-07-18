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
package com.db4o.cs.monitoring;

import com.db4o.cs.foundation.*;

/**
 * @exclude
 */
//@decaf.Ignore
public class MonitoredServerSideClientSocket4 extends MonitoredSocket4Base {

	public MonitoredServerSideClientSocket4(Socket4 socket, Networking bean) {
		super(socket);
		
		_bean = bean;
	}

	@Override
	protected Networking bean() {
		return _bean;
	}

	private final Networking _bean;
}

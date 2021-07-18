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
package com.db4o.cs.ssl;

import com.db4o.cs.foundation.NetworkSocketBase;
import com.db4o.cs.foundation.Socket4;

import java.io.IOException;

import javax.net.ssl.SSLSocketFactory;

//@decaf.Ignore
public class SSLSocket4 extends NetworkSocketBase {

	private final SSLSocketFactory _factory;

	public SSLSocket4(String hostName, int port, SSLSocketFactory factory) throws IOException {
		super(factory.createSocket(hostName, port), hostName);
		_factory = factory;
	}

	@Override
	protected Socket4 createParallelSocket(String hostName, int port) throws IOException {
		return new SSLSocket4(hostName, port, _factory);
	}

}

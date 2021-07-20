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

import com.db4o.ObjectServer;
import com.db4o.cs.internal.ClientConnectionEventArgs;
import com.db4o.cs.internal.ObjectServerEvents;
import com.db4o.cs.internal.ServerClosedEventArgs;
import com.db4o.events.Event4;
import com.db4o.events.EventListener4;
import com.db4o.events.StringEventArgs;

/**
 * publishes the number of client connections to JMX.
 */
//@decaf.Ignore
public class ClientConnectionsMonitoringSupport implements ServerConfigurationItem {

	public void apply(ObjectServer server) {
			final ClientConnections bean = Db4oClientServerMBeans.newClientConnectionsMBean(server);
			bean.register();
			((ObjectServerEvents)server).closed().addListener(new EventListener4<ServerClosedEventArgs>() {
				public void onEvent(Event4<ServerClosedEventArgs> e, ServerClosedEventArgs args) {
					bean.unregister();
				}
			});
			
			((ObjectServerEvents)server).clientConnected().addListener(new EventListener4<ClientConnectionEventArgs>() { public void onEvent(Event4<ClientConnectionEventArgs> e, ClientConnectionEventArgs args) {
				bean.notifyClientConnected();
			}});
			
			((ObjectServerEvents)server).clientDisconnected().addListener(new EventListener4<StringEventArgs>() { public void onEvent(Event4<StringEventArgs> e, StringEventArgs args) {
				bean.notifyClientDisconnected();
			}});

	}

	public void prepare(ServerConfiguration configuration) {
	}
	
}

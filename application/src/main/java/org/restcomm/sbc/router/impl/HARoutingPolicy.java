/*******************************************************************************
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc, Eolos IT Corp and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 *******************************************************************************/
package org.restcomm.sbc.router.impl;

import java.net.InetAddress;

import org.restcomm.sbc.router.RoutingPolicy;

/**
 * @author  ocarriles@eolos.la (Oscar Andres Carriles)
 * @date    28/4/2016 10:58:13
 * @class   HARoutingPolicy.java
 * @project Servlet2.5SBC
 *
 */
public class HARoutingPolicy implements RoutingPolicy {

	public String getName() {
		return "Hight Availability Routing Policy";
	}

	public InetAddress getSelectedIPAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getSelectdTransport() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getSelectedPort() {
		// TODO Auto-generated method stub
		return 0;
	}

}
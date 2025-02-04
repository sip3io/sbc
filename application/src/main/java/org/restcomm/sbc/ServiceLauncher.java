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
package org.restcomm.sbc;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;

import org.apache.commons.configuration.Configuration;

import org.apache.log4j.Logger;
import org.restcomm.sbc.managers.Monitor;





/**
 * @author  ocarriles@eolos.la (Oscar Andres Carriles)
 * @date    1 abr. 2017 7:58:37
 * @class   ServiceLauncher.java
 *
 */
public final class ServiceLauncher extends SipServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ServiceLauncher.class);

   
    
    public ServiceLauncher() {
        super();
    }

    @Override
    public void destroy() {
        
    }

    @Override
    public void init(final ServletConfig servletConfig) throws ServletException {
    	super.init(servletConfig);
    	if(LOG.isInfoEnabled()){
	          LOG.info(">> ServiceLauncher Servlet init()");
	    }
        final ServletContext context = servletConfig.getServletContext();
       
		SipFactory sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
		
		Configuration configuration = (Configuration) context.getAttribute(Configuration.class.getName());
		
        Monitor monitor=Monitor.getMonitor();
        monitor.start(sipFactory, configuration);
        
        
    }
    
    
    
   
   
    
}

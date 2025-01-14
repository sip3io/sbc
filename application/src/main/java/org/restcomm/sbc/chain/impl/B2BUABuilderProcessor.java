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
 */

package org.restcomm.sbc.chain.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.sip.Address;
import javax.servlet.sip.B2buaHelper;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TooManyHopsException;

import org.apache.log4j.Logger;
import org.restcomm.chain.ProcessorChain;
import org.restcomm.chain.processor.Message;
import org.restcomm.chain.processor.ProcessorCallBack;
import org.restcomm.chain.processor.impl.DefaultProcessor;
import org.restcomm.chain.processor.impl.ProcessorParsingException;
import org.restcomm.chain.processor.impl.SIPMutableMessage;
import org.restcomm.sbc.ConfigurationCache;
import org.restcomm.sbc.bo.Connector;
import org.restcomm.sbc.bo.Location;
import org.restcomm.sbc.bo.LocationNotFoundException;
import org.restcomm.sbc.managers.LocationManager;
import org.restcomm.sbc.managers.MessageUtil;
import org.restcomm.sbc.managers.RouteManager;


/**
 * @author ocarriles@eolos.la (Oscar Andres Carriles)
 * @date 3/5/2016 22:48:44
 * @class B2BUABuilderProcessor.java
 *
 */
public class B2BUABuilderProcessor extends DefaultProcessor implements ProcessorCallBack {

	private static transient Logger LOG = Logger.getLogger(B2BUABuilderProcessor.class);

	private LocationManager locationManager;
	private SipApplicationSession aSession;
	
	public B2BUABuilderProcessor() {
		// just to notify spi instantiation
		super();
		this.type = Type.SINGLE_PROCESSOR;
	}
	public B2BUABuilderProcessor(ProcessorChain chain) {
		super(chain);
		this.chain = chain;
		locationManager = LocationManager.getLocationManager();		
	}

	public B2BUABuilderProcessor(String name, ProcessorChain chain) {
		this(chain);
		setName(name);
	}
	/*
	 * <------------UPSTREAM -----------------------
    				|        			|
				+---------+         +---------+
	 <--------- |  UA     |         | UA      | <---------
	            |<--------|         |		  |
	 ---------> + @MZ     |-------->|  @DMZ   + --------->      
	  Message   |         |         |         |  Message
				|         |         |         |
				+---------+         +---------+
			MZ Leg  |      			   | DMZ Leg
			
	   -------------DOWNSTREAM---------------------->
     */

	private void processRequest(SIPMutableMessage message) {
		
		SipServletRequest request=(SipServletRequest) message.getContent();
		
		
		
		B2buaHelper helper = request.getB2buaHelper();
		if (LOG.isTraceEnabled()) {
			LOG.trace(">> processRequest() Initial/Linked="+request.isInitial()+"/"+helper.getLinkedSession(request.getSession()));
			
		}
		SipServletRequest newRequest = null;
		Map<String, List<String>> headers = new HashMap<String, List<String>>();
		
		SipURI fromURI 	= (SipURI) request.getFrom().getURI();
		
		SipURI toURI 	= (SipURI) request.getTo().  getURI();
		SipURI newSipToUri = toURI;
		Address contactURI = null;
		
		
		RouteManager routeManager = RouteManager.getRouteManager();
		Connector connector = null;
		InetSocketAddress outBoundInterface = null;
		SipURI route;
		
		
		if (message.getDirection()==Message.SOURCE_DMZ) {
			message.setTarget(Message.TARGET_MZ);
			// Must create Leg to MZ based on router info
			try {
				
				connector = routeManager.getRouteToMZ(request.getLocalAddr(), request.getLocalPort(),
						request.getInitialTransport());
				outBoundInterface = connector.getOutboundInterface();
				contactURI = routeManager.getFromAddress(fromURI, outBoundInterface);
				//contactURI.setParameter("gr", "");

			} catch (Exception e) {
				LOG.error("ERROR", e);
			}

			if (LOG.isTraceEnabled()) {
				headers.put(MessageUtil.B2BUA_FINGERPRINT_HEADER, Arrays.asList("Made in RequestBuilder to MZ"));
				LOG.trace("Connector to build OB "+connector.toPrint());
			}
			message.setTargetLocalAddress(connector.getHost());
			message.setTargetRemoteAddress(ConfigurationCache.getRoutingPolicy().getCandidate().getHost());
			message.setTargetTransport(connector.getTransport().toString());
			
			// newSipUri = sipFactory.createSipURI(""/*toURI.getUser()*/,
			// ConfigurationCache.getTargetHost());
			// newSipUri = sipFactory.createSipURI(toURI.getUser(), ConfigurationCache.getTargetHost());
			SipURI candidate=ConfigurationCache.getRoutingPolicy().getCandidate();
			
			route=candidate;
			//route="sip:" + candidate.getHost()+":"+candidate.getPort()+";transport="+candidate.getTransportParam();
			
			

		} else {
			message.setTarget(Message.TARGET_DMZ);
			// Comes from MZ Must create LEG to DMZ based on Location info
			Location location = null;
			
			try {
				location = locationManager.getLocation(toURI.getUser() + "@" + ConfigurationCache.getDomain());
				//outBoundInterface = new InetSocketAddress(ConfigurationCache.getDomain(), request.getLocalPort());
				outBoundInterface = routeManager.getOutboundProxy(location.getSourceConnectorSid().toString());
				connector=routeManager.getDMZConnector(location.getSourceConnectorSid().toString());
				contactURI = routeManager.getFromAddress(fromURI, outBoundInterface);
				//contactURI.setParameter("gr", "");
				message.setTargetLocalAddress(ConfigurationCache.getIpOfDomain());
				message.setTargetRemoteAddress(location.getHost());
				message.setTargetTransport(location.getTransport().toUpperCase());

			} catch (LocationNotFoundException e) {
				if (LOG.isTraceEnabled()) {			
					LOG.error(e);
				}
				
				SipServletResponse response =
				request.createResponse(SipServletResponse.SC_FORBIDDEN);
				
				message.setContent(response);
				message.unlink();
				return;
				
			} catch (NoRouteToHostException e) {
				LOG.error("ERROR", e);
				SipServletResponse response =
				request.createResponse(SipServletResponse.SC_FORBIDDEN);
						
				message.setContent(response);
				message.unlink();
				return;
			}
			
			newSipToUri = ConfigurationCache.getSipFactory().createSipURI(toURI.getUser(), location.getHost());
			newSipToUri.setPort(location.getPort());
			newSipToUri.setTransportParam(location.getTransport());
			connector=routeManager.getDMZConnector(location.getSourceConnectorSid().toString());
			fromURI.setHost(connector.getHost());
			fromURI.setTransportParam(connector.getTransport().name());
			fromURI.setPort(connector.getPort());
			route=null;
			//headers.put("To",	Arrays.asList(newSipToUri.toString()));
			

		}
		
		try {
			
			
			if (request.isInitial()) {
				headers.put("Contact",	Arrays.asList(contactURI.toString()));
				
				if (LOG.isTraceEnabled()) {
					
					if(aSession!=null&&aSession.isValid()){
					LOG.trace("LNK "+aSession.getAttribute(request.getSession().getCallId()));
					}
					
				}
				
				SipServletRequest usedRequest=null;
				
				if(aSession!=null&&aSession.isValid()) {
					usedRequest=(SipServletRequest) aSession.getAttribute(request.getSession().getCallId());
					
					
				}
				
				if(usedRequest!=null&&usedRequest.getSession().isValid()) {
					
					LOG.trace("REUSING SESSION REQ "+usedRequest.getCallId()+":"+usedRequest.getHeader("CSeq"));
					LOG.trace("ContactURI on creation "+contactURI.toString());
					usedRequest.setMaxForwards(70);
					
					newRequest = helper.createRequest(usedRequest.getSession(), request, headers);
					try {
						newRequest.setAddressHeader("Contact", contactURI);
					} catch (Exception e) {
						LOG.trace(e.getMessage());
					}
					String auth = request.getHeader("Authorization");
					newRequest.removeHeader("Authorization");
					if(auth!=null)
						newRequest.addHeader("Authorization", auth);
					// Controls expiration time of this leg
					// newRequest.getApplicationSession().setExpires(0);
					
					
				}
				else {
					LOG.trace("ContactURI on creation "+contactURI.toString());
					newRequest = helper.createRequest(request, true, headers);
					try {
						newRequest.setAddressHeader("Contact", contactURI);
					} catch (Exception e) {
						LOG.trace(e.getMessage());
					}
					// Controls expiration time of this leg
					newRequest.getApplicationSession().setExpires(0);
					aSession = ConfigurationCache.getSipFactory().createApplicationSession();
					aSession.setAttribute(request.getSession().getCallId(), newRequest);
					LOG.trace("NEW SESSION REQ/OLD "+newRequest.getSession()+"/"+request.getSession());
				
				}
				
				
				
				
				//newRequest.pushRoute(newSipToUri);
				
				
				//newRequest.getFrom().setURI(sipFactory.createSipURI(fromURI.getUser(),  ConfigurationCache.getTargetHost()));
				//newRequest.getTo().  setURI(newSipToUri);
				
				if(route!=null) {
					
					 newRequest.getSession().setOutboundInterface(outBoundInterface);
					 newRequest.pushRoute(route);
					 route=null;
				}
				/*else {
					try {
					newRequest.pushRoute(connector.buildAddress());
				} catch (ServletParseException e) {
					LOG.error("Cannot build addess",e);
				}
					
				}*/
				else {
					
					newRequest.setRequestURI(newSipToUri);
					newRequest.getTo().  setURI(newSipToUri);
					newRequest.getFrom().  setURI(fromURI);
					
				}
				
				
			} else {
				
				
				SipSession session = request.getSession();
				SipSession linkedSession = helper.getLinkedSession(session);
				
				if (LOG.isTraceEnabled()) {
					LOG.trace("NOT Initial Request " + request.getMethod());
					LOG.trace("SES " + session.toString());
					LOG.trace("LNK " + linkedSession);
				}
				if(linkedSession==null) {
					// what else can I do?
					LOG.warn("No linked session for request "+request.getMethod());
					linkedSession=session;
				}
				
				if (request.getMethod().equals("BYE")) {		
					newRequest = linkedSession.createRequest("BYE");
					
				} 
				
				else if (request.getMethod().equals("CANCEL")) {
					SipServletRequest originalRequest = (SipServletRequest) linkedSession
							.getAttribute(MessageUtil.B2BUA_ORIG_REQUEST_ATTR);
					newRequest = helper.getLinkedSipServletRequest(originalRequest).createCancel();
				} 
				/*
				 * Reinvites
				 */
				else if (request.getMethod().equals("INVITE")) {
					if (LOG.isTraceEnabled()) {	
						LOG.trace("Reinviting?");
					}
					newRequest = linkedSession.createRequest("INVITE");		
				} 
				else if (request.getMethod().equals("ACK")) {
					newRequest = linkedSession.createRequest("ACK");		
				} 
				else if (request.getMethod().equals("INFO")) {
					newRequest = linkedSession.createRequest("INFO");
					newRequest.setContent(request.getContent(), request.getContentType());		
				} 
				/*
				else if (request.getMethod().equals("CANCEL")) {
					SipServletRequest originalRequest = (SipServletRequest) linkedSession
							.getAttribute(MessageUtil.B2BUA_ORIG_REQUEST_ATTR);
					newRequest = helper.getLinkedSipServletRequest(originalRequest).createCancel();	
				} 
				*/
				else {
					LOG.error(request.getMethod() + " not implemented!");
				}
				

			}

			
			//newRequest.setAddressHeader("Contact", sipFactory.createAddress(contactURI));
			newRequest.getSession().setAttribute(MessageUtil.B2BUA_ORIG_REQUEST_ATTR, request);
	
			
			if (LOG.isTraceEnabled()) {
				LOG.trace("Initial Request " + request.getMethod()+" on session "+newRequest.getSession().getId());
				LOG.trace("Routing thru outboundInterface " + outBoundInterface.toString());
				LOG.trace("Routing To " + route);
				LOG.trace("Contact back " + contactURI.toString());
				LOG.trace("Sending Message: \n " + newRequest.toString());
			}
			
			
		} catch (IllegalArgumentException e) {
			LOG.error("", e);
		} catch (TooManyHopsException e) {
			LOG.error("", e);
		} catch (UnsupportedEncodingException e) {
			LOG.error("", e);
		} catch (IOException e) {
			LOG.error("", e);
		} 
		
		message.setContent(newRequest);
		
	}

	private void processResponse(SIPMutableMessage message) {
		if (LOG.isTraceEnabled()) {
			LOG.trace(">> processResponse()");
		}
		if(message.getDirection()==Message.SOURCE_DMZ) {
			message.setTarget(Message.TARGET_MZ);
		}
		else {
			message.setTarget(Message.TARGET_DMZ);
		}
		SipServletResponse dmzResponse=(SipServletResponse) message.getContent();
		
		//SipURI toURI = (SipURI) dmzResponse.getTo().getURI();

		B2buaHelper helper = dmzResponse.getRequest().getB2buaHelper();
		SipServletResponse mzResponse;

		int statusResponse = dmzResponse.getStatus();
		String reasonResponse = dmzResponse.getReasonPhrase();
		
		
		/*
		
		if (dmzResponse.getStatus() == SipServletResponse.SC_OK) {
		//	if(dmzResponse.getMethod().equals("REGISTER")) {	
				if (LOG.isTraceEnabled()) {
					LOG.trace("Final Response Discarding dialog");
				}
				if(aSession!=null)
					aSession.invalidate();
		//	}
		}
		*/
		
		SipServletRequest linked = helper.getLinkedSipServletRequest(dmzResponse.getRequest()); 
		SipSession originalSession = helper.getLinkedSession(dmzResponse.getSession());
		
		if(linked!=null) {
			message.setTargetLocalAddress(linked.getLocalAddr());
			message.setTargetRemoteAddress(linked.getRemoteAddr());
			message.setTargetTransport(linked.getTransport().toUpperCase());
			
			mzResponse = linked.createResponse(statusResponse, reasonResponse); 
			
			if (LOG.isTraceEnabled()) {
				LOG.trace("Reusing linked session");
			}
		}
		else {
			message.setTargetLocalAddress(dmzResponse.getLocalAddr());
			message.setTargetRemoteAddress(dmzResponse.getRemoteAddr());
			message.setTargetTransport(dmzResponse.getTransport().toUpperCase());
			
			if(originalSession == null) {
				message.abort();
				message.setContent(dmzResponse);
				return;
			}
			
			mzResponse = helper.createResponseToOriginalRequest(originalSession, statusResponse, reasonResponse);
			
			
			LOG.warn(">>>>>>>>>>>>Must Abort Message flow?, No linked Session available.");
			
		}
		
		
		if (LOG.isTraceEnabled()) {
			LOG.trace("mz Response created for session "+originalSession.getId());
		}

		try {
			if (dmzResponse.getContent() != null) {
				mzResponse.setContent(dmzResponse.getContent(), dmzResponse.getContentType());
			}
			mzResponse.setHeaderForm(dmzResponse.getHeaderForm());
			
		} catch (UnsupportedEncodingException e) {
			LOG.error("ERROR", e);
		} catch (IOException e) {
			LOG.error("ERROR", e);
		} catch (IllegalStateException e) {
			LOG.error("ERROR", e);
		}
		
		try {
		
			if (dmzResponse.getStatus() == SipServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED) {	
				mzResponse.setHeader("Proxy-Authenticate", dmzResponse.getHeader("Proxy-Authenticate"));	
			}
	
			else if (dmzResponse.getStatus() == SipServletResponse.SC_UNAUTHORIZED) {	
				mzResponse.setHeader("WWW-Authenticate", dmzResponse.getHeader("WWW-Authenticate"));	
			}
		} catch (Exception e) {
			LOG.error("ERROR", e);
		}
		
		
		mzResponse.getSession().setAttribute(MessageUtil.B2BUA_ORIG_REQUEST_ATTR, dmzResponse.getRequest());
		//mzResponse.setHeader("Contact", "sip:"+dmzResponse.getRequest().getLocalAddr()+":"+dmzResponse.getRequest().getLocalPort());
		message.setContent(mzResponse);
		

	}
	
	

	public String getName() {
		return "B2BUA leg builder Processor";
	}

	public int getId() {
		return this.hashCode();
	}

	
	@Override
	public void setName(String name) {
		this.name = name;

	}

	@Override
	public ProcessorCallBack getCallback() {
		return this;
	}

	@Override
	public void doProcess(Message message) throws ProcessorParsingException {
		SIPMutableMessage m = (SIPMutableMessage) message;
		SipServletMessage sm=(SipServletMessage) message.getContent();
		
		/*
		if (LOG.isTraceEnabled()) {
			LOG.trace("-------" + sm.getLocalAddr() + "->" + sm.getRemoteAddr());
			LOG.trace("-------Receiving message: \n" + sm);
		}
		*/
		if(sm instanceof SipServletRequest) {
			processRequest(m);
		}
		if(sm instanceof SipServletResponse) {
			processResponse(m);
		}
		
	}

	@Override
	public double getVersion() {
		return 1.0;
	}

}

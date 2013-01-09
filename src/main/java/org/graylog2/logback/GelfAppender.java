/**
 * Copyright (c) 2013 Gunnar Wagenknecht and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 */
package org.graylog2.logback;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graylog2.GelfMessage;
import org.graylog2.GelfMessageProvider;
import org.graylog2.GelfSender;
import org.graylog2.GelfTCPSender;
import org.graylog2.GelfUDPSender;
import org.json.simple.JSONValue;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.status.ErrorStatus;

/**
 * A Logback appender that outputs GELF messages.
 * <p>
 * Note, the appender is synchronous, i.e. only one thread will send a message
 * to a GELF server at time. In order to make it asynchronous please consider
 * wrapping it into an {@link AsyncAppender}.
 * </p>
 */
public class GelfAppender extends AppenderBase<ILoggingEvent> implements GelfMessageProvider {

	private String graylogHost;
	private static String originHost;
	private int graylogPort = 12201;
	private String facility;
	private GelfSender gelfSender;
	private boolean extractStacktrace;
	private boolean addExtendedInformation;
	private boolean includeLocation = true;
	private Map<String, String> fields;

	@Override
	protected void append(final ILoggingEvent event) {
		if (!isStarted() || (gelfSender == null)) {
			return;
		}

		final GelfMessage gelfMessage = LogbackGelfMessageFactory.makeMessage(event, this);
		try {
			gelfSender.sendMessage(gelfMessage);
		} catch (final IOException e) {
			addStatus(new ErrorStatus("IO failure in appender", this, e));
		}
	}

	public String getFacility() {
		return facility;
	}

	public Map<String, String> getFields() {
		if (fields == null) {
			fields = new HashMap<String, String>();
		}
		return Collections.unmodifiableMap(fields);
	}

	public String getGraylogHost() {
		return graylogHost;
	}

	public int getGraylogPort() {
		return graylogPort;
	}

	private String getLocalHostName() {
		String hostName = null;
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (final UnknownHostException e) {
			addError("Unknown local hostname", e);
		}

		return hostName;
	}

	public String getOriginHost() {
		if (originHost == null) {
			originHost = getLocalHostName();
		}
		return originHost;
	}

	public boolean isAddExtendedInformation() {
		return addExtendedInformation;
	}

	public boolean isExtractStacktrace() {
		return extractStacktrace;
	}

	public boolean isIncludeLocation() {
		return includeLocation;
	}

	public void setAddExtendedInformation(final boolean addExtendedInformation) {
		this.addExtendedInformation = addExtendedInformation;
	}

	@SuppressWarnings("unchecked")
	public void setAdditionalFields(final String additionalFields) {
		fields = (Map<String, String>) JSONValue.parse(additionalFields.replaceAll("'", "\""));
	}

	public void setExtractStacktrace(final boolean extractStacktrace) {
		this.extractStacktrace = extractStacktrace;
	}

	public void setFacility(final String facility) {
		this.facility = facility;
	}

	public void setGraylogHost(final String graylogHost) {
		this.graylogHost = graylogHost;
	}

	public void setGraylogPort(final int graylogPort) {
		this.graylogPort = graylogPort;
	}

	public void setIncludeLocation(final boolean includeLocation) {
		this.includeLocation = includeLocation;
	}

	public void setOriginHost(final String originHost) {
		GelfAppender.originHost = originHost;
	}

	@Override
	public void start() {
		if (graylogHost == null) {
			addError("Graylog2 hostname is empty!", null);
			return;
		}

		try {
			if (graylogHost.startsWith("tcp:")) {
				final String tcpGraylogHost = graylogHost.substring(0, 4);
				gelfSender = new GelfTCPSender(tcpGraylogHost, graylogPort);
			} else if (graylogHost.startsWith("udp:")) {
				final String udpGraylogHost = graylogHost.substring(0, 4);
				gelfSender = new GelfUDPSender(udpGraylogHost, graylogPort);
			} else {
				gelfSender = new GelfUDPSender(graylogHost, graylogPort);
			}
		} catch (final UnknownHostException e) {
			addError("Unknown Graylog2 hostname:" + getGraylogHost(), e);
			return;
		} catch (final SocketException e) {
			addError("Socket exception", e);
			return;
		} catch (final IOException e) {
			addError("IO exception", e);
			return;
		}

		// continue start
		super.start();
	}

	@Override
	public void stop() {
		try {
			if (gelfSender != null) {
				gelfSender.close();
				gelfSender = null;
			}
		} finally {
			// make sure stop runs
			super.stop();
		}
	}
}

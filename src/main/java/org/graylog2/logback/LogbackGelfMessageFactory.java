package org.graylog2.logback;

import java.util.Map;
import java.util.Map.Entry;

import org.graylog2.GelfMessage;
import org.graylog2.GelfMessageProvider;

import ch.qos.logback.classic.pattern.FileOfCallerConverter;
import ch.qos.logback.classic.pattern.LineOfCallerConverter;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;
import ch.qos.logback.core.CoreConstants;

public class LogbackGelfMessageFactory {
	private static final int MAX_SHORT_MESSAGE_LENGTH = 250;
	private static final String ORIGIN_HOST_KEY = "originHost";
	private static final String LOGGER_NAME = "logger";
	private static final String THREAD_NAME = "thread";
	private static final String JAVA_TIMESTAMP = "timestampMs";

	private static final LineOfCallerConverter LINE_OF_CALLER = new LineOfCallerConverter();
	private static final FileOfCallerConverter FILE_OF_CALLER = new FileOfCallerConverter();
	private static final ThrowableProxyConverter THROWABLE_PROXY_CONVERTER;
	static {
		THROWABLE_PROXY_CONVERTER = new ThrowableProxyConverter();
		THROWABLE_PROXY_CONVERTER.start();
	}

	public static final GelfMessage makeMessage(final ILoggingEvent event, final GelfMessageProvider provider) {
		final long timeStamp = event.getTimeStamp();

		String file = null;
		String lineNumber = null;
		if (provider.isIncludeLocation()) {
			file = FILE_OF_CALLER.convert(event);
			lineNumber = LINE_OF_CALLER.convert(event);
		}

		String renderedMessage = event.getFormattedMessage();
		String shortMessage;

		if (renderedMessage == null) {
			renderedMessage = "";
		}

		if (renderedMessage.length() > MAX_SHORT_MESSAGE_LENGTH) {
			shortMessage = renderedMessage.substring(0, MAX_SHORT_MESSAGE_LENGTH - 1);
		} else {
			shortMessage = renderedMessage;
		}

		if (provider.isExtractStacktrace()) {
			final IThrowableProxy throwableInformation = event.getThrowableProxy();
			if (throwableInformation != null) {
				renderedMessage += CoreConstants.LINE_SEPARATOR + THROWABLE_PROXY_CONVERTER.convert(event);
			}
		}

		final GelfMessage gelfMessage = new GelfMessage(shortMessage, renderedMessage, timeStamp, String.valueOf(LevelToSyslogSeverity.convert(event)), lineNumber, file);

		if (provider.getOriginHost() != null) {
			gelfMessage.setHost(provider.getOriginHost());
		}

		if (provider.getFacility() != null) {
			gelfMessage.setFacility(provider.getFacility());
		}

		final Map<String, String> fields = provider.getFields();
		for (final Map.Entry<String, String> entry : fields.entrySet()) {
			if (entry.getKey().equals(ORIGIN_HOST_KEY) && (gelfMessage.getHost() == null)) {
				gelfMessage.setHost(fields.get(ORIGIN_HOST_KEY));
			} else {
				gelfMessage.addField(entry.getKey(), entry.getValue());
			}
		}

		if (provider.isAddExtendedInformation()) {

			gelfMessage.addField(THREAD_NAME, event.getThreadName());
			gelfMessage.addField(LOGGER_NAME, event.getLoggerName());
			gelfMessage.addField(JAVA_TIMESTAMP, Long.toString(gelfMessage.getJavaTimestamp()));

			// Get MDC and add a GELF field for each key/value pair
			final Map<String, String> mdc = event.getMDCPropertyMap();
			if (mdc != null) {
				for (final Entry<String, String> e : mdc.entrySet()) {
					gelfMessage.addField(e.getKey(), e.getValue());
				}
			}
		}

		return gelfMessage;
	}
}

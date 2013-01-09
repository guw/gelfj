package org.graylog2;

import java.io.IOException;

public interface GelfSender {
	public static final int DEFAULT_PORT = 12201;

	public boolean sendMessage(GelfMessage message) throws IOException;
	public void close();
}

package net.gvcc.goffice.messaging.amqp.exception;

public class ExchangeConfigNotFoundException extends QueueConfigException {

	private static final long serialVersionUID = -1822031197762055882L;

	public ExchangeConfigNotFoundException(String name, String message) {
		super(name, message);
	}

	public ExchangeConfigNotFoundException(String name, String message, Throwable cause) {
		super(name, message, cause);
	}
}

package net.gvcc.goffice.messaging.amqp.exception;

public class ExchangeConfigInvalidNameException extends QueueConfigException {

	private static final long serialVersionUID = 8278748961269580642L;

	public ExchangeConfigInvalidNameException(String name, String message) {
		super(name, message);
	}

	public ExchangeConfigInvalidNameException(String name, String message, Throwable cause) {
		super(name, message, cause);
	}
}

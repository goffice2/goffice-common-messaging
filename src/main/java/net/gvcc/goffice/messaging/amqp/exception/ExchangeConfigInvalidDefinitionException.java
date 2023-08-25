package net.gvcc.goffice.messaging.amqp.exception;

public class ExchangeConfigInvalidDefinitionException extends QueueConfigException {

	private static final long serialVersionUID = 1L;

	public ExchangeConfigInvalidDefinitionException(String name, String message) {
		super(name, message);
	}
}

package net.gvcc.goffice.messaging.amqp.exception;

public class QueueConfigNotFoundException extends QueueConfigException {

	private static final long serialVersionUID = -1822031197762055882L;

	public QueueConfigNotFoundException(String name, String message) {
		super(name, message);
	}

	public QueueConfigNotFoundException(String name, String message, Throwable cause) {
		super(name, message, cause);
	}
}

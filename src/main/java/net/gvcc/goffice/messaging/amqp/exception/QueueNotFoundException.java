package net.gvcc.goffice.messaging.amqp.exception;

public class QueueNotFoundException extends QueueConfigException {

	private static final long serialVersionUID = -1822031197762055882L;

	public QueueNotFoundException(String name, String message) {
		super(name, message);
	}
}

package net.gvcc.goffice.messaging.amqp.exception;

import lombok.Getter;

public class QueueConfigException extends Exception {
	private static final long serialVersionUID = 1273128866276043291L;

	@Getter
	private String name;

	public QueueConfigException(String name, String message) {
		super(message);
		this.name = name;
	}

	public QueueConfigException(String name, String message, Throwable cause) {
		super(message, cause);
		this.name = name;
	}
}

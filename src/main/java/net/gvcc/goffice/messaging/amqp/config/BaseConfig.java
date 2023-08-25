package net.gvcc.goffice.messaging.amqp.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 *
 * <p>
 * The <code>BaseConfig</code> class
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */

@Getter
@Setter
public class BaseConfig {
	@Getter(value = AccessLevel.PROTECTED)
	private QueueGroupConfig config;

	private String name;
}

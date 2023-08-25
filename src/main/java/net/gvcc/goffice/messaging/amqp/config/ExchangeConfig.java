package net.gvcc.goffice.messaging.amqp.config;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.gvcc.goffice.messaging.amqp.Constants;

/**
 * 
 *
 * <p>
 * The <code>ExchangeConfig</code> class
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@NoArgsConstructor
@Getter
@Setter
public class ExchangeConfig {
	private MessagingSystemConfigProperties owner;
	private String name;
	private String type = Constants.DIRECT; // default exchange type

	// used the there is a single exchange config at component level
	public ExchangeConfig(MessagingSystemConfigProperties owner, String name, String type) {
		this.owner = owner;
		this.name = name;
		this.type = type;
	}

	// used the there are multiple exchange config at component level
	public void init(MessagingSystemConfigProperties owner, String configName) {
		this.owner = owner;
		this.name = StringUtils.defaultIfEmpty(this.name, configName);
	}
}

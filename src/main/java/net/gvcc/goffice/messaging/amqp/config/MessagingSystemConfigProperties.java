/*
 * goffice... 
 * https://www.goffice.org
 * 
 * Copyright (c) 2005-2022 Consorzio dei Comuni della Provincia di Bolzano Soc. Coop. <https://www.gvcc.net>.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.gvcc.goffice.messaging.amqp.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.gvcc.goffice.messaging.amqp.exception.ExchangeConfigInvalidNameException;
import net.gvcc.goffice.messaging.amqp.exception.ExchangeConfigNotFoundException;
import net.gvcc.goffice.messaging.amqp.exception.QueueConfigException;
import net.gvcc.goffice.messaging.amqp.exception.QueueConfigInvalidNameException;
import net.gvcc.goffice.messaging.amqp.exception.QueueConfigNotFoundException;

/**
 *
 * 
 * this class contains the mapping with the properties file and in particular with the properties related to the multiple queue configurations for a specific microservices module
 * <p>
 * The <code>MultiConfigProperties</code> class
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
@NoArgsConstructor
@ConfigurationProperties(prefix = "goffice.messaging.amqp")
@Component
public class MessagingSystemConfigProperties {
	private static Logger LOGGER = LoggerFactory.getLogger(MessagingSystemConfigProperties.class);

	// @Setter(value = AccessLevel.NONE)
	// @Getter(value = AccessLevel.NONE)
	// @Value("${GO2_COMPONENT_NAME:NO-SCOPE}")
	// private String defaultScope;

	private String username;
	private String password;
	private String host;
	private int port;
	// private String scope;
	// private String exchangeType; // the type of exchange when, and ONLY WHEN, there is a single exchange config at component level
	private Map<String /* name */, ExchangeConfig> exchanges = new HashMap<>();
	private Map<String /* name */, QueueGroupConfig> queues = new HashMap<>();

	@PostConstruct
	void initMessagingSystemConfigProperties() {
		LOGGER.info("initMessagingSystemConfigProperties - START");

		// exchanges configuration
		exchanges.keySet() //
				.stream() //
				.forEach(key -> exchanges.get(key).init(this, key));

		// queue group configuration
		queues.keySet() //
				.stream() //
				.forEach(key -> queues.get(key).init(this, key));

		LOGGER.info("initMessagingSystemConfigProperties - END");
	}

	public List<String> listExchangeConfigNames() {
		return exchanges.keySet().stream() //
				.sorted() //
				.collect(Collectors.toList());
	}

	public ExchangeConfig getExchangeConfig(String configName) throws QueueConfigException {
		configName = StringUtils.trimToNull(configName);
		if (configName == null) {
			throw new ExchangeConfigInvalidNameException(configName, "Exchange config name can't be empty or null!");
		}

		ExchangeConfig config = exchanges.get(configName);
		if (config == null) {
			throw new ExchangeConfigNotFoundException(configName, "Exchange config was not found. Name=".concat(configName));
		}
		return config;
	}

	public List<String> listGroupConfigNames() {
		return queues.keySet().stream() //
				.sorted() //
				.collect(Collectors.toList());
	}

	public QueueGroupConfig getQueueGroupConfig(String configName) throws QueueConfigException {
		configName = StringUtils.trimToNull(configName);
		if (configName == null) {
			throw new QueueConfigInvalidNameException(configName, "Queue config name can't be empty or null!");
		}

		QueueGroupConfig config = queues.get(configName);
		if (config == null) {
			throw new QueueConfigNotFoundException(configName, "Queue config was not found. Name=".concat(configName));
		}
		return config;
	}
}

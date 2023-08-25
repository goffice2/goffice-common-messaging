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
package net.gvcc.goffice.messaging.amqp.producer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import net.gvcc.goffice.client.GvccClientManager;
import net.gvcc.goffice.language.ILanguageStorage;
import net.gvcc.goffice.messaging.amqp.Constants;
import net.gvcc.goffice.messaging.amqp.Helper;
import net.gvcc.goffice.messaging.amqp.config.ExchangeConfig;
import net.gvcc.goffice.messaging.amqp.config.MessagingSystemConfigProperties;
import net.gvcc.goffice.messaging.amqp.config.QueueConfig;
import net.gvcc.goffice.messaging.amqp.config.QueueGroupConfig;
import net.gvcc.goffice.multitenancy.ITenantStorage;
import net.gvcc.goffice.opentracing.IOpenTracingStorage;

/**
 * <p>
 * The <code>GofficeMessageProducer</code> class
 * </p>
 * <p>
 * class declared as a spring component which is in charge of routing messages to and from the queues based on the routing key and the reference exchange
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@Component
@Import({ MessagingSystemConfigProperties.class, Helper.class })
public class GofficeMessageProducer {
	private static final Logger LOGGER = LoggerFactory.getLogger(GofficeMessageProducer.class);

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private GvccClientManager gvccClientManager;

	@Autowired
	private ITenantStorage storage;

	@Autowired
	private ILanguageStorage languageStorage;

	@Autowired
	private IOpenTracingStorage openTracingStorage;

	@Autowired
	private MessagingSystemConfigProperties queueSystemConfigurator;

	@Autowired
	private Helper helper;

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public <T> void sendMessage(String queueConfigName, T event) throws Exception {
		LOGGER.info("sendMessage - START");

		Map<String, Object> messageHeaders = new HashMap<>();
		addHeader(messageHeaders, Constants.X_GO2_HEADER_TENANT, storage.getTenantName());
		addHeader(messageHeaders, Constants.X_GO2_HEADER_AUTH_TOKEN, gvccClientManager.getAccessToken());
		addHeader(messageHeaders, Constants.X_GO2_HEADER_LANGUAGE, languageStorage.getLanguage());

		Map<String, List<String>> requestHeaders = openTracingStorage == null ? null : openTracingStorage.getHeaders();
		if (requestHeaders != null) {
			requestHeaders.keySet().stream() //
					.forEach(headerName -> {
						List<String> headerValues = requestHeaders.get(headerName);
						if (headerValues != null) {
							messageHeaders.put(headerName, headerValues);
						}
					});
		}

		sendMessage(queueConfigName, event, messageHeaders);

		LOGGER.info("sendMessage - END");
	}

	protected <T> void sendMessage(String queueConfigName, T event, Map<String, Object> messageHeaders) throws Exception {
		LOGGER.info("sendMessage - START");

		QueueGroupConfig groupConfig = queueSystemConfigurator.getQueueGroupConfig(queueConfigName);

		ExchangeConfig exchangeConfig = groupConfig.getExchangeConfig();
		QueueConfig queueConfig = groupConfig.getQueueConfig();

		String exchangeName = exchangeConfig.getName();
		String routingKey = queueConfig.getRoutingKey();

		LOGGER.info("sendMessage - you are publishing a message to the exchange named '{}' with routing key: {})", exchangeName, routingKey);

		addHeader(messageHeaders, Constants.X_GO2_HEADER_CONFIG_NAME, queueConfigName);
		addHeader(messageHeaders, Constants.X_GO2_HEADER_COMPONENT_NAME, helper.getComponentName());
		addHeader(messageHeaders, Constants.X_GO2_HEADER_PUBLISHER_INFO, helper.getNetworkInfo());

		rabbitTemplate.convertAndSend(exchangeName, routingKey, event, message -> {
			MessageProperties properties = message.getMessageProperties();

			properties.getHeaders().putAll(messageHeaders);

			// set the delay time for delayed exchanges/queues
			int interval = groupConfig.getQueueConfig().getRetryInterval();
			if (interval > 0) {
				properties.setDelay(interval);
			}

			return message;
		});

		LOGGER.info("sendMessage - END");
	}

	private static void addHeader(Map<String, Object> messageHeaders, String headerName, Object headerValue) {
		if (headerValue != null) {
			messageHeaders.put(headerName, headerValue);
		}
	}

}

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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.gvcc.goffice.crypt.CryptManager;
import net.gvcc.goffice.messaging.amqp.Constants;
import net.gvcc.goffice.messaging.amqp.Helper;
import net.gvcc.goffice.messaging.amqp.exception.QueueConfigException;

/**
 * <p>
 * The <code>MessagingSystemConfiguratior</code> class
 * </p>
 * <p>
 * The main rabbitmq components such as exchenges, queues and bindings are set dynamically from configuration properties informations
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@Configuration
@Import({ CryptManager.class, Helper.class })
public class MessagingSystemConfigurator {
	private static Logger LOGGER = LoggerFactory.getLogger(MessagingSystemConfigurator.class);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private static final String X_DELAYED_MESSAGE = "x-delayed-message";
	private static final String X_DELAYED_TYPE = "x-delayed-type";

	private static final String X_GO2_GENERATOR_NAME = "x-go2-generator-name";
	private static final String X_GO2_GENERATOR_INFO = "x-go2-generator-info";
	private static final String X_GO2_GENERATED = "x-go2-generated";

	private static final DateFormat DF_GENERATED = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private static class DelayedExchange extends CustomExchange {

		public DelayedExchange(String name, String xDelayedMessage, boolean durable, boolean autoDelete, Map<String, Object> args) {
			super(name, xDelayedMessage, durable, autoDelete, args);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Autowired
	private MessagingSystemConfigProperties messagingSystemConfig;

	@Autowired
	private CryptManager cryptManager;

	@Autowired
	private Helper helper;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * 
	 * @return Declarables
	 */
	@Bean
	public Declarables configureAmqpMessagingSystem() {
		LOGGER.info("configureAmqpMessagingSystem - START");

		Declarables declarables = new Declarables();
		Collection<Declarable> declarableList = declarables.getDeclarables();

		Map<String /* exchange name */, AbstractExchange> exchangesMap = new HashMap<>();

		RabbitAdmin rabbitAdmin = rabbitAdmin();

		List<String> exchangeList = messagingSystemConfig.listExchangeConfigNames();
		LOGGER.info("configureAmqpMessagingSystem - ***** found {} exchange(s) to configure: {}", exchangeList.size(), exchangeList);

		List<String> groupList = messagingSystemConfig.listGroupConfigNames();
		LOGGER.info("configureAmqpMessagingSystem - ***** found {} group(s) of queues to configure: {}", groupList.size(), groupList);

		// creating exchange elements
		if (exchangeList.size() == 0) {
			LOGGER.info("configureAmqpMessagingSystem - ***** found NO exchanges to configure!");
		} else {
			exchangeList.stream() //
					.forEach(configName -> {
						final String msg = "configureAmqpMessagingSystem - ***** starting to configure the exchange with config-name: '{}'...";
						LOGGER.info(msg, configName);

						try {
							ExchangeConfig exchangeConfig = messagingSystemConfig.getExchangeConfig(configName);
							AbstractExchange exchange = createAmqpExchangesEnvironment(rabbitAdmin, exchangeConfig);
							exchangesMap.put(exchangeConfig.getName(), exchange);

							LOGGER.info(msg.concat("DONE"), configName);
						} catch (QueueConfigException e) {
							LOGGER.error("ds", e);
							throw new RuntimeException(e);
						}
					});
		}

		// creating queue elements
		if (groupList.size() == 0) {
			LOGGER.info("configureAmqpMessagingSystem - ***** found NO queue-group to configure!");
		} else {
			groupList.stream() //
					.forEach(configName -> {
						final String msg = "configureAmqpMessagingSystem - ***** starting to configure the queue-group with config-name: '{}'...";
						LOGGER.info(msg, configName);

						try {
							QueueGroupConfig queueConfig = messagingSystemConfig.getQueueGroupConfig(configName);
							declarableList.addAll(createAmqpQueuesEnvironment(rabbitAdmin, exchangesMap, queueConfig));

							LOGGER.info(msg.concat("DONE"), configName);
						} catch (QueueConfigException e) {
							LOGGER.error("ds", e);
							throw new RuntimeException(e);
						}
					});
		}

		LOGGER.info("configureAmqpMessagingSystem - END");

		return declarables;
	}

	private AbstractExchange createAmqpExchangesEnvironment(RabbitAdmin rabbitAdmin, ExchangeConfig exchangeConfig) {
		LOGGER.debug("createAmqpExchangesEnvironment - START");

		final String name = exchangeConfig.getName();
		final boolean durable = true;
		final boolean autoDelete = false;

		Map<String, Object> args = new HashMap<>();
		args.put(X_GO2_GENERATOR_NAME, helper.getComponentName()); // simply for info
		args.put(X_GO2_GENERATOR_INFO, helper.getNetworkInfo()); // simply for info
		args.put(X_GO2_GENERATED, DF_GENERATED.format(new Date())); // simply for info

		AbstractExchange exchange;

		switch (exchangeConfig.getType()) {
			case Constants.FANOUT -> {
				exchange = new FanoutExchange(name, durable, autoDelete, args);
			}
			case Constants.TOPIC -> {
				exchange = new TopicExchange(name, durable, autoDelete, args);
			}
			case Constants.DIRECT -> {
				exchange = new DirectExchange(name, durable, autoDelete, args);
			}
			case Constants.DELAYED -> {
				args.put(X_DELAYED_TYPE, exchangeConfig.getType());
				exchange = new DelayedExchange(name, X_DELAYED_MESSAGE, durable, autoDelete, args);
			}
			default -> {
				throw new RuntimeException("Exchange type not handled: " //
						.concat(exchangeConfig.getType()) //
						.concat(", exchange name: ") //
						.concat(name));
			}
		}

		rabbitAdmin.declareExchange(exchange);

		LOGGER.debug("createAmqpExchangesEnvironment - END");

		return exchange;
	}

	private List<Declarable> createAmqpQueuesEnvironment(RabbitAdmin rabbitAdmin, Map<String, AbstractExchange> exchangesMap, QueueGroupConfig config) {
		LOGGER.debug("createAmqpQueuesEnvironment - START");

		List<Declarable> list = new ArrayList<>();

		ExchangeConfig exchangeConfig = config.getExchangeConfig();
		if (exchangeConfig == null) {
			throw new RuntimeException("No exchange config found for queue: ".concat(config.getName()));
		}

		AbstractExchange exchange = exchangesMap.get(exchangeConfig.getName());
		if (exchange == null) {
			throw new RuntimeException("No exchange found with name: ".concat(exchangeConfig.getName()));
		}

		////////////////
		// Main ////////
		////////////////
		createMainQueue(config, exchangeConfig, exchange, rabbitAdmin, list);

		////////////////
		// DeadLetter //
		////////////////
		createDeadLetterQueue(config, exchange, rabbitAdmin, list);

		////////////////
		// ParkingLot //
		////////////////
		createParkingLotQueue(config, exchange, rabbitAdmin, list);

		LOGGER.debug("createAmqpQueuesEnvironment - END");

		return list;
	}

	private void createMainQueue(QueueGroupConfig config, ExchangeConfig exchangeConfig, AbstractExchange exchange, RabbitAdmin rabbitAdmin, List<Declarable> list) {
		LOGGER.info("createMainQueue - START");

		QueueConfig queueConfig = config.getQueueConfig();
		if (!queueConfig.isEnabled()) {
			LOGGER.info("createMainQueue - Needed NO MAIN queue (disabled by config)!");
		} else {
			DlqConfig dlqConfig = config.getDlqConfig();

			Queue queue = buildQueue(queueConfig, dlqConfig) //
					.build();

			Binding binding = null;

			switch (exchangeConfig.getType()) {
				case Constants.FANOUT -> {
					binding = BindingBuilder.bind(queue) //
							.to((FanoutExchange) exchange);
				}
				case Constants.TOPIC -> {
					binding = BindingBuilder.bind(queue) //
							.to((TopicExchange) exchange) //
							.with(queueConfig.getRoutingKey());
				}
				case Constants.DIRECT -> {
					binding = BindingBuilder.bind(queue) //
							.to((DirectExchange) exchange) //
							.with(queueConfig.getRoutingKey());
				}
				case Constants.DELAYED -> {
					binding = BindingBuilder.bind(queue) //
							.to((DelayedExchange) exchange) //
							.with(queueConfig.getRoutingKey()) //
							.noargs();
				}
				default -> {
					// nothing to do: the check was already done
				}
			}

			// registering configs
			rabbitAdmin.declareQueue(queue);
			rabbitAdmin.declareBinding(binding);

			list.addAll(Arrays.asList(exchange, queue, binding));
		}

		LOGGER.info("createMainQueue - END");
	}

	private void createDeadLetterQueue(QueueGroupConfig config, AbstractExchange exchange, RabbitAdmin rabbitAdmin, List<Declarable> list) {
		LOGGER.info("createDeadLetterQueue - START");

		DlqConfig dlqConfig = config.getDlqConfig();
		if (!dlqConfig.isEnabled()) {
			LOGGER.info("createDeadLetterQueue - Needed NO DLQ queue (disabled by config)!");
		} else {
			ParkingLotConfig parkingLot = config.getParkingLotConfig();

			// configure new queue
			Queue queue = buildQueue(dlqConfig, parkingLot) //
					.build();

			// CustomExchange exchange = new CustomExchange(name, X_DELAYED_MESSAGE, true, false, args);
			Binding binding = BindingBuilder.bind(queue) //
					.to(exchange) //
					.with(dlqConfig.getRoutingKey()) //
					.noargs();

			// registering configs
			rabbitAdmin.declareQueue(queue);
			rabbitAdmin.declareBinding(binding);

			list.addAll(Arrays.asList(queue, binding));
		}

		LOGGER.info("createDeadLetterQueue - END");
	}

	private void createParkingLotQueue(QueueGroupConfig config, AbstractExchange exchange, RabbitAdmin rabbitAdmin, List<Declarable> list) {
		LOGGER.info("createParkingLotQueue - START");

		ParkingLotConfig parkingLot = config.getParkingLotConfig();
		if (!parkingLot.isEnabled()) {
			LOGGER.info("createParkingLotQueue - Needed NO PARKINGLOT queue (disabled by config)!");
		} else {
			String name = parkingLot.getName();
			LOGGER.info("createParkingLotQueue - PARKINGLOT queue name={}", name);

			Queue queue = buildQueue(parkingLot, null) //
					.build();

			// CustomExchange exchange = new CustomExchange(name, X_DELAYED_MESSAGE, true, false, args);
			Binding binding = BindingBuilder.bind(queue) //
					.to(exchange) //
					.with(parkingLot.getRoutingKey()) //
					.noargs();

			// registering configs
			rabbitAdmin.declareQueue(queue);
			rabbitAdmin.declareBinding(binding);

			list.addAll(Arrays.asList(queue, binding));
		}

		LOGGER.info("createParkingLotQueue - END");
	}

	private QueueBuilder buildQueue(QueueConfig newQueueToConfig, QueueConfig targetDeathQueueConfig) {
		LOGGER.info("buildQueue - START");

		String name = newQueueToConfig.getName();
		String type = newQueueToConfig.getType().name().toUpperCase();
		LOGGER.info("buildQueue - {} queue name={}", type, name);

		if (!ExchangeTypes.FANOUT.equals(newQueueToConfig.getExchange().getType())) {
			String routingKey = newQueueToConfig.getRoutingKey();
			LOGGER.info("buildQueue - {} routing key={}", type, routingKey);
		}

		QueueBuilder builder = QueueBuilder.durable(name) //
				.withArgument(X_GO2_GENERATOR_NAME, helper.getComponentName()) // simply for info
				.withArgument(X_GO2_GENERATOR_INFO, helper.getNetworkInfo()) // simply for info
				.withArgument(X_GO2_GENERATED, DF_GENERATED.format(new Date())); // simply for info

		boolean isParkingLotQueue = newQueueToConfig instanceof ParkingLotConfig;
		if (!isParkingLotQueue) { // only for NON-PARKINGLOT queues
			int retryInterval = newQueueToConfig.getRetryInterval(); // in milliseconds
			int attempts = newQueueToConfig.getRetryMaxAttempts();
			int expiration = newQueueToConfig.getExpire(); // in milliseconds
			if (retryInterval > 0 || attempts > 0 || expiration > 0) {
				builder.quorum();

				if (retryInterval > 0) {
					builder.withArgument(Constants.X_DELAY, retryInterval);
				}

				if (attempts > 0) {
					builder.deliveryLimit(attempts);
				}

				if (expiration > 0) {
					builder.ttl(expiration);
				}
			}

			if (targetDeathQueueConfig != null && targetDeathQueueConfig.isEnabled()) {
				builder.deadLetterExchange(targetDeathQueueConfig.getExchange().getName()) //
						.deadLetterRoutingKey(targetDeathQueueConfig.getRoutingKey());
			}
		}

		LOGGER.info("buildQueue - END");

		return builder;
	}

	/**
	 * create a message converter based on ObjectMapper
	 * 
	 * @return MessageConverter
	 */
	@Bean
	public MessageConverter jsonMessageConverter() {
		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
		return new Jackson2JsonMessageConverter(mapper);
	}

	/**
	 * the main configuration features are set for the caching connection factory
	 * 
	 * @return ConnectionFactory
	 */
	@Bean
	public ConnectionFactory connectionFactory() {
		LOGGER.info("connectionFactory - START");

		String host = messagingSystemConfig.getHost();
		int port = messagingSystemConfig.getPort();
		String username = messagingSystemConfig.getUsername();
		String password = decodePassword(messagingSystemConfig.getPassword());

		final String msg = "connectionFactory - attempting to connect to RabbitMQ exposed at address '{}:{}' (login account: {})...";
		LOGGER.info(msg, host, port, username);

		CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(host, port);
		cachingConnectionFactory.setUsername(username);
		cachingConnectionFactory.setPassword(password);

		LOGGER.info(msg.concat("DONE"), host, port, username);

		LOGGER.info("connectionFactory - END");

		return cachingConnectionFactory;
	}

	private String decodePassword(String password) {
		LOGGER.trace("decodePassword - START");

		if (StringUtils.isNotBlank(password)) {
			try {
				password = cryptManager.decryptJasypt(password);
			} catch (RuntimeException e) {
				LOGGER.warn("decodePassword - Unable to decrypt password!! The plain/text value will be used!");
			}
		}

		LOGGER.trace("decodePassword - END");

		return password;
	}

	/**
	 * the rabbittemplate configurations are set in the spring context
	 * 
	 * @param connectionFactory
	 *            connection factory of rabbitmq queue
	 * @return rabbitTemplate
	 */
	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
		final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMessageConverter(jsonMessageConverter());
		return rabbitTemplate;
	}

	/**
	 * @return RabbitAdmin
	 */
	@Bean
	public RabbitAdmin rabbitAdmin() {
		return new RabbitAdmin(connectionFactory());
	}
}
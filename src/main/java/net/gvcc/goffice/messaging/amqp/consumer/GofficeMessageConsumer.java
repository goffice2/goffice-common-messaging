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
package net.gvcc.goffice.messaging.amqp.consumer;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.InputStreamSource;
import org.springframework.messaging.handler.annotation.Header;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;

import lombok.Getter;
import net.gvcc.goffice.language.ILanguageStorage;
import net.gvcc.goffice.messaging.amqp.Constants;
import net.gvcc.goffice.messaging.amqp.Helper;
import net.gvcc.goffice.messaging.amqp.config.QueueConfig;
import net.gvcc.goffice.messaging.amqp.exception.QueueConfigException;
import net.gvcc.goffice.modelmapper.ModelMapperHelper;
import net.gvcc.goffice.multitenancy.ITenantStorage;
import net.gvcc.goffice.opentracing.HeadersFilter;
import net.gvcc.goffice.opentracing.IOpenTracingStorage;
import net.gvcc.goffice.token.ITokenStorage;
import net.gvcc.goffice.troubleshooting.actions.EmailServiceImpl;
import net.gvcc.goffice.troubleshooting.actions.TroubleshootingService;

//@Import({ net.gvcc.goffice.client.GvccClientManager.class, net.gvcc.goffice.client.Configuration.class })
/**
 *
 * <p>
 * The <code>GofficeMessageConsumer</code> class
 * </p>
 * <p>
 * This abstract class must be implemented by the classes that will manage the queuing logic and asynchronous processing of the messages inserted in the queues
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@Import({ GofficeConsumerHelper.class, EmailServiceImpl.class, Helper.class })
public abstract class GofficeMessageConsumer<T> {

	private static final Logger LOGGER = LoggerFactory.getLogger(GofficeMessageConsumer.class);

	private static final DateFormat CURRENTTIME_DF = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Autowired
	private ITenantStorage tenantStorage;

	@Autowired
	private ITokenStorage tokenStorage;

	@Autowired
	private ILanguageStorage languageStorage;

	@Autowired
	private IOpenTracingStorage openTracingStorage;

	@Autowired
	private TroubleshootingService troubleshootingService;

	@Autowired
	private GofficeConsumerHelper gofficeConsumerHelper;

	@Autowired
	private Helper helper;

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private static final Set<String> CLAIMS_FILTER = new HashSet<>();
	static {
		CLAIMS_FILTER.add("telephoneNumber");
		CLAIMS_FILTER.add("locale");
		CLAIMS_FILTER.add("email");
		CLAIMS_FILTER.add("given_name");
		CLAIMS_FILTER.add("aud");
		CLAIMS_FILTER.add("name");
		// CLAIMS_FILTER.add("steuerNummer");
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	protected static enum Ack {
		REJECT, //
		REQUEUE, //
		DELIVERED
	}

	@Getter
	private static class RejectMessageException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private Ack ack;

		RejectMessageException(Ack ack) {
			this.ack = ack;
		}
	}

	/**
	 * 
	 *
	 * <p>
	 * The <code>Context</code> class
	 * </p>
	 * <p>
	 * Data: Jun 23, 2023
	 * </p>
	 * 
	 * @author Renzo Poli
	 * @version 1.0 The object that contains some data about the current elaboration like:
	 *          <ul>
	 *          <li>- {@link QueueConfig}: the queue configuration</li>
	 *          <li>- org.springframework.amqp.core.Message: the queue message instance handled by the consumer</li>
	 *          <li>- org.springframework.amqp.support.AmqpHeaders#DELIVERY_TAG: the delivery tag (if one) provided by the AMQP framework for the current message</li>
	 *          <li>- {@link Ack}: the Ack value used by the AMQP framework to understand the consumer processing result about the current message: REJECT, REQUEUE or DELIVERED</li>
	 *          <li>- delivery count: the times the message was elaborated by the consumer (readonly)</li>
	 *          <li>- retry after: the delay time before the next elaboration. You can specify this value programmatically.</li>
	 *          <li>- other data</li>
	 *          </ul>
	 */
	protected static class Context extends HashMap<String, Object> {
		private static final long serialVersionUID = 1L;

		private static final String X_RETRY_AFTER = "x-retry-after";
		private static final String MAX_ATTEMPTS = "max-attempts";

		public void setQueueConfig(QueueConfig config) {
			put(QueueConfig.class.getName(), config);
		}

		public QueueConfig getQueueConfig() {
			return (QueueConfig) get(QueueConfig.class.getName());
		}

		public void setMessage(Message message) {
			put(Message.class.getName(), message);
		}

		public Message getMessage() {
			return (Message) get(Message.class.getName());
		}

		public void setDeliveryTag(long tag) {
			put(AmqpHeaders.DELIVERY_TAG, tag);
		}

		public long getDeliveryTag() {
			return (long) get(AmqpHeaders.DELIVERY_TAG);
		}

		public void setAck(Ack ack) {
			put(Ack.class.getName(), ack);
		}

		public Ack getAck() {
			return (Ack) get(Ack.class.getName());
		}

		protected void setDeliveryCount(long deliveryCount) {
			put(Constants.X_HEADER_DELIVERY_COUNT, deliveryCount);
		}

		public long getDeliveryCount() {
			Object deliveryCount = get(Constants.X_HEADER_DELIVERY_COUNT);
			return deliveryCount == null ? 0L : (long) deliveryCount;
		}

		public void setRetryMaxAttempts(int maxAttempts) {
			put(MAX_ATTEMPTS, maxAttempts);
		}

		public int getRetryMaxAttempts() {
			Object maxAttempts = get(MAX_ATTEMPTS);
			return maxAttempts == null ? 0 : (int) maxAttempts;
		}

		/**
		 * set delay time in milliseconds
		 * 
		 * @param interval
		 *            delay time in milliseconds
		 */
		public void setRetryAfter(int interval) {
			put(X_RETRY_AFTER, interval);
		}

		public int getRetryAfter() {
			Object retryAfter = get(X_RETRY_AFTER);
			return retryAfter == null ? 0 : (int) retryAfter; // in milliseconds
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// /**
	// * Identify the queue which this consumer has to refer.
	// *
	// * @return The name of the queue config, as specified in then application.properties
	// */
	// protected abstract String queueSelector();

	/**
	 * Extracts the data from the context/message using specific type/class
	 * 
	 * @param context
	 *            The object that contains some data about the current elaboration like:
	 *            <ul>
	 *            <li>- {@link QueueConfig}: the queue configuration</li>
	 *            <li>- org.springframework.amqp.core.Message: the queue message instance handled by the consumer</li>
	 *            <li>- org.springframework.amqp.support.AmqpHeaders#DELIVERY_TAG: the delivery tag (if one) provided by the AMQP framework for the current message</li>
	 *            <li>- {@link Ack}: the Ack value used by the AMQP framework to understand the consumer processing result about the current message: REJECT, REQUEUE or DELIVERED</li>
	 *            <li>- delivery count: the times the message was elaborated by the consumer (readonly)</li>
	 *            <li>- retry after: the delay time before the next elaboration. You can specify this value programmatically.</li>
	 *            <li>- other data</li>
	 *            </ul>
	 *            See: {@link GofficeMessageConsumer.Context}
	 * @return The business data (as a specific type, not generic) provided by the queue publisher
	 * @see convert(Message message, Class<T> className)
	 * @throws Exception
	 */
	protected abstract T getWorkData(Context context) throws Exception;

	/**
	 * Override this method to implement the source code required to handle the business data provided by the AMQP publisher.
	 * 
	 * It will be call each time a message has been published into the queue which the consumer refers.
	 * 
	 * @param context
	 *            The object that contains some data about the current elaboration like:
	 *            <ul>
	 *            <li>- {@link QueueConfig}: the queue configuration</li>
	 *            <li>- org.springframework.amqp.core.Message: the queue message instance handled by the consumer</li>
	 *            <li>- org.springframework.amqp.support.AmqpHeaders#DELIVERY_TAG: the delivery tag (if one) provided by the AMQP framework for the current message</li>
	 *            <li>- {@link Ack}: the Ack value used by the AMQP framework to understand the consumer processing result about the current message: REJECT, REQUEUE or DELIVERED</li>
	 *            <li>- delivery count: the times the message was elaborated by the consumer (readonly)</li>
	 *            <li>- retry after: the delay time before the next elaboration. You can specify this value programmatically.</li>
	 *            <li>- other data</li>
	 *            </ul>
	 *            See: {@link GofficeMessageConsumer.Context}
	 * @param data
	 *            The business data (as a specific type, not generic) provided by the queue publisher
	 */
	protected abstract void consume(Context context, T data);

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Creates a specific configuration about a DATA listener, regarding the consumer concurrency.
	 * 
	 * @param configurer
	 *            The factory configurer provided by the system
	 * @param connectionFactory
	 *            The RabbitMQ connection factory
	 * @param configName
	 *            The name of the custom configuration as provided in the application.properties
	 * @return An instance of listener container factory
	 * @throws QueueConfigException
	 */
	protected SimpleRabbitListenerContainerFactory configureDataContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory, String configName)
			throws QueueConfigException {
		String queueName = gofficeConsumerHelper.getDataQueueName(configName);
		QueueConfig config = gofficeConsumerHelper.getQueueByName(queueName);
		return configureContainerFactory(configurer, connectionFactory, config);
	}

	/**
	 * Creates a specific configuration about a DLQ listener, regarding the consumer concurrency.
	 * 
	 * @param configurer
	 *            The factory configurer provided by the system
	 * @param connectionFactory
	 *            The RabbitMQ connection factory
	 * @param configName
	 * @return An instance of listener container factory
	 * @throws QueueConfigException
	 */
	protected SimpleRabbitListenerContainerFactory configureDlqContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory, String configName)
			throws QueueConfigException {
		String queueName = gofficeConsumerHelper.getDlqQueueName(configName);
		QueueConfig config = gofficeConsumerHelper.getQueueByName(queueName);
		return configureContainerFactory(configurer, connectionFactory, config);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * The system handle each message trough this function.
	 * 
	 * Then, it calls the specific implementation using consume() or condumeDlq() methods.
	 * 
	 * @param message
	 *            The message object handled by the queue system
	 * @param channel
	 *            The RabbitMQ interface to a channel
	 * @param tag
	 *            The unique delivery tag assigned by the AMQP system. It is used to correctly handle the ack information.
	 * @throws IOException
	 */
	protected final void handleMessage(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
		LOGGER.debug("handleMessage - START");

		final String logMsg = "handleMessage - [queue=%s, type=%s, delivery tag=%d]";
		String logPrefix = String.format(logMsg, "???", "???", tag);

		try {
			MessageProperties properties = message.getMessageProperties();

			String queueName = properties.getConsumerQueue();
			LOGGER.debug("handleMessage - queue name as retrieved from message: {}", queueName);

			QueueConfig config = gofficeConsumerHelper.getQueueByName(queueName);

			logPrefix = String.format(logMsg, config.getName(), config.getType().name(), tag);
			LOGGER.debug(logPrefix);

			long xDeliveryCount = getXDeliveryCount(properties);

			// initialize shared context
			Context sharedContext = new Context();
			sharedContext.setQueueConfig(config);
			sharedContext.setMessage(message);
			sharedContext.setDeliveryTag(tag);

			if (LOGGER.isDebugEnabled()) {
				final String _logPrefix = logPrefix;
				List<Map<String, ?>> xDeathHeaders = properties.getXDeathHeader();
				if (xDeathHeaders != null) {
					xDeathHeaders.stream() //
							.forEach(item -> LOGGER.debug(_logPrefix.concat("********************** x-death-header: {}"), item));
				}

				LOGGER.debug(logPrefix.concat("********************** {}={}"), Constants.X_HEADER_DELIVERY_COUNT, xDeliveryCount);
			}

			// < 0 means no data found;
			// >= 0 means the queue has been configured to retry elaboration when errors
			// > 1 means the message has been requeued more then once
			if (xDeliveryCount >= 0) {
				sharedContext.setDeliveryCount(xDeliveryCount);
				sharedContext.setRetryMaxAttempts(config.getRetryMaxAttempts());
			}

			// configure environment
			configureContextFromMessageProperties(message);

			T data = getWorkData(sharedContext);

			// call the logic implementation
			switch (config.getType()) {
				case queue:
					consume(sharedContext, data);
					break;

				case dlq:
					consumeDlq(sharedContext, data);
					break;

				default:
					throw new RuntimeException("Unhandled executor for queue type: ".concat(config.getType().name()));
			}

			Ack ack = sharedContext.getAck();
			LOGGER.info(logPrefix.concat(" received ack: {}"), ack);

			// check results
			if (Ack.DELIVERED == ack) {
				// nothing to do: the message will be removed from the AMQP system
			} else {
				// if re-queuing is needed, we check if there is a value to delay the next elaboration
				if (Ack.REQUEUE == ack) {
					int retryAfter = sharedContext.getRetryAfter();

					if (retryAfter > 0) { // retry-after (set at runtime by the developer), has higher priority then "retry-interval" (set at startup time by the queue config)
						waitIfRequired(sharedContext.getRetryAfter(), "the consumer itself");
					} else {
						waitIfRequired(config.getRetryInterval(), "the retry-interval option in queue configuration");
					}
				}

				throw new RejectMessageException(ack);
			}
		} catch (RejectMessageException e) {
			boolean requeue = Ack.REQUEUE == e.getAck();
			LOGGER.info(logPrefix.concat(" requeuing message required: {}"), requeue);
			channel.basicNack(tag, false /* reject/requeue ONLY this tag */, requeue /* requeue message or not */);
		} catch (RuntimeException e) {
			LOGGER.error(logPrefix, e);
			throw e;
		} catch (Exception e) {
			LOGGER.error(logPrefix, e);
			throw new RuntimeException(e);
		}

		LOGGER.debug("handleMessage - END");
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private SimpleRabbitListenerContainerFactory configureContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory, QueueConfig config) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setConsumerTagStrategy(helper::parseConsumerTagTemplate);

		int maxConsumerCount = config.getConcurrentConsumersMax();

		factory.setBatchListener(maxConsumerCount > 1);
		factory.setConcurrentConsumers(config.getConcurrentConsumers());
		factory.setMaxConcurrentConsumers(maxConsumerCount);

		return factory;
	}

	private static long getXDeliveryCount(MessageProperties properties) {
		LOGGER.debug("getXDeliveryCount - START");

		long xDeliveryCount = 0;

		try {
			Object value = properties.getHeader(Constants.X_HEADER_DELIVERY_COUNT);
			if (value != null) {
				if (value instanceof Long) {
					xDeliveryCount = (Long) value;
				} else if (value instanceof Double) {
					xDeliveryCount = ((Double) value).longValue();
				} else if (value instanceof Integer) {
					xDeliveryCount = ((Integer) value).longValue();
				} else if (value instanceof String) {
					xDeliveryCount = Long.parseLong((String) value);
				} else {
					throw new RuntimeException("Type of value now handled: " + value.getClass().getName());
				}
			}
		} catch (RuntimeException e) {
			LOGGER.error("getXDeliveryCount", e);
			throw e;
		} catch (Exception e) {
			LOGGER.error("getXDeliveryCount", e);
			throw new RuntimeException("Unable to parse ".concat(Constants.X_HEADER_DELIVERY_COUNT).concat(" header value!"));
		}

		LOGGER.debug("getXDeliveryCount - END");

		return xDeliveryCount;
	}

	private static void waitIfRequired(int delayInMilliseconds, String logInfo) {
		if (delayInMilliseconds > 0) {
			try {
				LOGGER.debug("waitIfRequired - waiting for {} (as requested by {})....", Helper.toHuman(delayInMilliseconds), logInfo);
				Thread.sleep(delayInMilliseconds);
			} catch (InterruptedException e) {
				LOGGER.error("waitIfRequired", e);
			}
		}
	}

	private void configureContextFromMessageProperties(Message message) {
		MessageProperties messageProperties = message.getMessageProperties();
		Map<String, Object> messageHeaders = messageProperties.getHeaders();

		Object value = messageHeaders.get(Constants.X_GO2_HEADER_AUTH_TOKEN);
		tokenStorage.setToken(value != null ? value.toString() : null);

		value = messageHeaders.get(Constants.X_GO2_HEADER_TENANT);
		tenantStorage.setTenantName(value != null ? value.toString() : null);

		value = messageHeaders.get(Constants.X_GO2_HEADER_LANGUAGE);
		languageStorage.setLanguage(value != null ? value.toString() : null);

		// opentracing headers
		try {
			@SuppressWarnings("unchecked")
			Map<String, List<String>> headers = messageHeaders.keySet().stream() //
					.filter(HeadersFilter.IS_OPENTRACING_HEADER) //
					.collect(Collectors.toMap(headerName -> headerName, headerName -> (List<String>) messageHeaders.get(headerName)));
			openTracingStorage.setHeaders(headers);
		} catch (Exception e) {
			LOGGER.error("handleMessage", e);
		}
	}

	private void clearAuthToken(Message message) {
		LOGGER.debug("clearAuthToken - START");

		try {
			MessageProperties messageProperties = message.getMessageProperties();
			Map<String, Object> messageHeaders = messageProperties.getHeaders();

			Object value = messageHeaders.get(Constants.X_GO2_HEADER_AUTH_TOKEN);
			String authToken = value == null ? null : value.toString();
			if (authToken != null) {
				messageHeaders.remove(Constants.X_GO2_HEADER_AUTH_TOKEN); // REMOVE THE TOKEN: JUST FOR SECURITY!!!!

				DecodedJWT jwt = JWT.decode(authToken);

				try {
					String issuer = jwt.getIssuer();
					messageHeaders.put(Constants.X_GO2_HEADER_TOKEN_ISSUER, issuer);
				} catch (Exception e) {
					LOGGER.warn("clearAuthToken", e);
				}

				try {
					Claim subjectClaim = jwt.getClaim("preferred_username");
					String subject = subjectClaim == null ? "unknown" : subjectClaim.asString();
					if (subject != null) {
						messageHeaders.put(Constants.X_GO2_HEADER_TOKEN_ISSUED_TO, subject);
					}
				} catch (Exception e) {
					LOGGER.warn("clearAuthToken", e);
				}

				try {
					List<String> filteredClaims = null;
					Map<String, Claim> claims = jwt.getClaims();
					if (claims != null) {
						filteredClaims = claims.keySet().stream() //
								.filter(key -> CLAIMS_FILTER.contains(key)) //
								.map(key -> {
									Claim claimValue = claims.get(key);
									return String.format("%s: %s", key, claims == null ? "[NULL]" : claimValue.asString());
								}) //
								.collect(Collectors.toList());
					}

					if (filteredClaims != null) {
						messageHeaders.put(Constants.X_GO2_HEADER_TOKEN_CLAIMS, filteredClaims.toArray());
					}
				} catch (Exception e) {
					LOGGER.warn("clearAuthToken", e);
				}
			}
		} catch (Exception e) {
			LOGGER.error("clearAuthToken", e);
		}

		LOGGER.debug("clearAuthToken - END");
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * 
	 * @param message
	 *            the message object handled by the queue system
	 * @param className
	 *            the output type you want (which has to be the same as the object into the queue)
	 * @return the object encapsulated in the queue message
	 * @throws JsonProcessingException
	 *             jackson processing error
	 * @throws JsonMappingException
	 *             decoding or encoding error in the json unmarshall process
	 */
	protected T convert(Message message, Class<T> className) throws JsonProcessingException, JsonMappingException {
		LOGGER.debug("convert - START");

		String json = new String(message.getBody(), StandardCharsets.UTF_8);
		ObjectMapper objmapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

		objmapper.registerModule(new JavaTimeModule());
		objmapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
		T serialized = objmapper.readValue(json, className);

		LOGGER.debug("convert - serialized={}", serialized);
		LOGGER.debug("convert - END");

		return serialized;
	}

	/**
	 * This method is invoked by the AMQP framework when a message has been delivered to the DLQ queue.
	 * 
	 * You can override it, if needed.
	 * 
	 * @param context
	 *            The object that contains some data about the current elaboration like:
	 *            <ul>
	 *            <li>- {@link QueueConfig}: the queue configuration</li>
	 *            <li>- org.springframework.amqp.core.Message: the queue message instance handled by the consumer</li>
	 *            <li>- org.springframework.amqp.support.AmqpHeaders#DELIVERY_TAG: the delivery tag (if one) provided by the AMQP framework for the current message</li>
	 *            <li>- {@link Ack}: the Ack value used by the AMQP framework to understand the consumer processing result about the current message: REJECT, REQUEUE or DELIVERED</li>
	 *            <li>- delivery count: the times the message was elaborated by the consumer (readonly)</li>
	 *            <li>- retry after: the delay time before the next elaboration. You can specify this value programmatically.</li>
	 *            <li>- other data</li>
	 *            </ul>
	 *            See: {@link GofficeMessageConsumer.Context}
	 * @param data
	 *            The business data (as a specific type, not generic) provided by the queue publisher
	 */
	protected void consumeDlq(Context context, T data) {
		LOGGER.info("consumeDlq - START");

		Ack ack = Ack.REQUEUE;

		try {
			LOGGER.info("consumeDlq - {}", context.toString());

			Message message = context.getMessage();

			// clear token header and add user account info
			clearAuthToken(message);

			String body = getTroubleshootinMessageBody(context, data, troubleshootingService.getDefaultBody());
			LOGGER.debug("consumeDlq - body={}", body);

			boolean error = troubleshootingService.sendMessage(message, body, getTroubleshootingAttachments(context, data));
			if (!error) {
				ack = Ack.REJECT; // rejected -> means the message will be routed to PARKINGLOT!
			}
		} catch (Exception e) {
			LOGGER.error("consumeDlq - Error during processing  license DLQ message", e);
		}

		context.setAck(ack);

		LOGGER.info("consumeDlq - END");
	}

	/**
	 * The consumer may override this method to provide a map of StreamSource that contains many attachments.
	 * 
	 * They will be included in the troubleshooting notification
	 * 
	 * @param context
	 *            The object that contains some data about the current elaboration like:
	 *            <ul>
	 *            <li>- {@link QueueConfig}: the queue configuration</li>
	 *            <li>- org.springframework.amqp.core.Message: the queue message instance handled by the consumer</li>
	 *            <li>- org.springframework.amqp.support.AmqpHeaders#DELIVERY_TAG: the delivery tag (if one) provided by the AMQP framework for the current message</li>
	 *            <li>- {@link Ack}: the Ack value used by the AMQP framework to understand the consumer processing result about the current message: REJECT, REQUEUE or DELIVERED</li>
	 *            <li>- delivery count: the times the message was elaborated by the consumer (readonly)</li>
	 *            <li>- retry after: the delay time before the next elaboration. You can specify this value programmatically.</li>
	 *            <li>- other data</li>
	 *            </ul>
	 *            See: {@link GofficeMessageConsumer.Context}
	 * @param data
	 *            The business data (as a specific type, not generic) provided by the queue publisher
	 * @return A map that contains, for each required attachment, the name of the attachment (as key of map) and the StreamSource (as value o the map)
	 */
	protected Map<String, InputStreamSource> getTroubleshootingAttachments(Context context, T data) {
		return null; // default: no attachments
	}

	/**
	 * The consumer may override this method to change the troubleshooting message body
	 * 
	 * @param context
	 *            The object that contains some data about the current elaboration like:
	 *            <ul>
	 *            <li>- {@link QueueConfig}: the queue configuration</li>
	 *            <li>- org.springframework.amqp.core.Message: the queue message instance handled by the consumer</li>
	 *            <li>- org.springframework.amqp.support.AmqpHeaders#DELIVERY_TAG: the delivery tag (if one) provided by the AMQP framework for the current message</li>
	 *            <li>- {@link Ack}: the Ack value used by the AMQP framework to understand the consumer processing result about the current message: REJECT, REQUEUE or DELIVERED</li>
	 *            <li>- delivery count: the times the message was elaborated by the consumer (readonly)</li>
	 *            <li>- retry after: the delay time before the next elaboration. You can specify this value programmatically.</li>
	 *            <li>- other data</li>
	 *            </ul>
	 *            See: {@link GofficeMessageConsumer.Context}
	 * @param data
	 *            The business data (as a specific type, not generic) provided by the queue publisher
	 * @param defaultBody
	 *            Default value of troubleshooting notification body
	 * @return The text the system have to use as body of the troubleshooting notification
	 */
	protected String getTroubleshootinMessageBody(Context context, T data, String defaultBody) {
		String body = defaultBody;
		body = ModelMapperHelper.parseObject(body, data, ModelMapperHelper.EMPTY_VALUE_IF_NULL);
		body = ModelMapperHelper.parseMap(body, getContextData(), ModelMapperHelper.EMPTY_VALUE_IF_NULL);
		return body;
	}

	protected Map<String, Object> getContextData() {
		String xRequestId = "";
		try {
			List<String> headers = openTracingStorage.getHeaders().get(HeadersFilter.REQUEST_ID);
			if (headers != null && !headers.isEmpty()) {
				xRequestId = headers.get(0);
			}
		} catch (Exception e) {
		}

		String hostname = "";
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
		}

		Map<String, Object> args = new HashMap<>();

		args.put("ctxTenantName", tenantStorage.getTenantName());
		args.put("ctxLanguage", languageStorage.getLanguage());
		args.put("ctxTraceRequestId", xRequestId);

		args.put("envHostname", hostname);
		args.put("envCurrentTime", CURRENTTIME_DF.format(new Date()));

		args.put("envJavaVersion", System.getProperty("java.vm.version"));
		args.put("envJavaName", System.getProperty("java.vm.name"));

		args.put("envOsName", System.getProperty("os.name"));
		args.put("envOsVersion", System.getProperty("os.version"));

		return args;
	}
}
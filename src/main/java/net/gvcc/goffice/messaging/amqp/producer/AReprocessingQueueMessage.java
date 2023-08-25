package net.gvcc.goffice.messaging.amqp.producer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.google.gson.Gson;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import net.gvcc.goffice.client.GvccClientManager;
import net.gvcc.goffice.messaging.amqp.Constants;
import net.gvcc.goffice.messaging.amqp.config.MessagingSystemConfigProperties;
import net.gvcc.goffice.opentracing.HeadersFilter;

/**
 * 
 *
 * <p>
 * The <code>AReprocessingQueueMessage</code> class
 * </p>
 * <p>
 * Data: 06 may 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@Import({ GofficeMessageProducer.class, GvccClientManager.class, MessagingSystemConfigProperties.class })
public abstract class AReprocessingQueueMessage {
	private static Logger LOGGER = LoggerFactory.getLogger(AReprocessingQueueMessage.class);

	private static final Set<String> CONTEXT_HEADERS = new HashSet<>();
	static {
		CONTEXT_HEADERS.add(Constants.X_GO2_HEADER_COMPONENT_NAME);
		CONTEXT_HEADERS.add(Constants.X_GO2_HEADER_TENANT);
		CONTEXT_HEADERS.add(Constants.X_GO2_HEADER_AUTH_TOKEN);
		CONTEXT_HEADERS.add(Constants.X_GO2_HEADER_LANGUAGE);
	}

	private static final Predicate<String> IS_CONTEXT_HEADER = headerName -> CONTEXT_HEADERS.contains(headerName);

	@Autowired
	private GofficeMessageProducer producer;

	@Autowired
	private GvccClientManager gvccClientManager;

	/**
	 * 
	 * @param messageAsJson
	 *            the original message content as JSON String, as provided by the troubleshooting notification (e.g.: the file attached into the email)
	 * @return The HTTP status: 500 InternalServerError or 200 OK
	 */
	@Operation(summary = "requeue", tags = "queue", description = "This method put the content in the queue so it will be reprocessed")
	@Hidden
	@PreAuthorize("@gofficeSecurityService.hasServicePermission('Queue')")
	@PostMapping(value = "/requeue")
	public ResponseEntity<?> requeue(@RequestBody String messageAsJson) {
		LOGGER.info("requeue - START");

		ResponseEntity<?> response = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

		try {
			Message message = new Gson().fromJson(messageAsJson, Message.class);

			MessageProperties properties = message.getMessageProperties();

			String queueConfigName = properties.getHeader(Constants.X_GO2_HEADER_CONFIG_NAME);
			LOGGER.info("requeue - queue config name as retrieved from message: {}", queueConfigName);

			clearXDeathHeaders(properties);

			Map<String, Object> messageHeaders = parseMessageHeaders(properties);
			setAuthToken(messageHeaders);

			LOGGER.info("requeue - republishing message....");
			producer.sendMessage(queueConfigName, message, messageHeaders);
			LOGGER.info("requeue - republishing message....DONE");

			response = new ResponseEntity<>(HttpStatus.OK);
		} catch (Exception e) {
			LOGGER.error("requeue", e);
		}

		LOGGER.info("requeue - END");

		return response;
	}

	private static void clearXDeathHeaders(MessageProperties properties) {
		LOGGER.info("clearXDeathHeaders - START");

		try {
			Map<String, Object> messageHeaders = properties.getHeaders();
			messageHeaders.remove(Constants.X_DEATH_HEADER);
			// if (messageHeaders != null) {
			// messageHeaders.keySet().stream() //
			// .filter(key -> key.startsWith("x-death)
			// }
		} catch (Exception e) {
			LOGGER.warn("clearXDeathHeaders", e);
		}

		LOGGER.info("clearXDeathHeaders - END");
	}

	private void setAuthToken(Map<String, Object> messageHeaders) {
		messageHeaders.put(Constants.X_GO2_HEADER_AUTH_TOKEN, gvccClientManager.getAccessToken());
	}

	private static Map<String, Object> parseMessageHeaders(MessageProperties properties) {
		LOGGER.info("parseMessageHeaders - START");

		Map<String, Object> toReturnHeaders = new HashMap<>();

		Map<String, Object> headers = properties.getHeaders();
		if (headers != null) {
			toReturnHeaders.putAll(headers.keySet().stream() //
					.filter(HeadersFilter.IS_OPENTRACING_HEADER.or(IS_CONTEXT_HEADER)) //
					.collect(Collectors.toMap(key -> key, key -> headers.get(key))) //
			);
		}

		LOGGER.info("parseMessageHeaders - END");

		return toReturnHeaders;
	}

}

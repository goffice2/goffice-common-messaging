package net.gvcc.goffice.messaging.amqp;

import org.springframework.amqp.core.ExchangeTypes;

/**
 * 
 *
 * <p>
 * The <code>Constants</code> class
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
public class Constants {
	/**
	 * delivery count header: the max number of delivery times the message can be reprocessed
	 */
	public static final String X_DELAY = "x-delay";
	/**
	 * delivery count header: the max number of delivery times the message can be reprocessed
	 */
	public static final String X_HEADER_DELIVERY_COUNT = "x-delivery-count";
	/**
	 * x-death header: the header name that contains the history of death reasons
	 */
	public static final Object X_DEATH_HEADER = "x-death";
	/**
	 * auth token header: the jwt token placeholder
	 */
	public static final String X_GO2_HEADER_AUTH_TOKEN = "x-go2-auth-token";
	/**
	 * auth tenant header: the tenant / citeis placeholder
	 */
	public static final String X_GO2_HEADER_TENANT = "x-go2-tenant";
	/**
	 * auth language header: the user language placeholder
	 */
	public static final String X_GO2_HEADER_LANGUAGE = "x-go2-language";
	/**
	 * name of queue config header name
	 */
	public static final String X_GO2_HEADER_CONFIG_NAME = "x-go2-config-name";
	/**
	 * component name header: the name of the component who produced the message
	 */
	public static final String X_GO2_HEADER_COMPONENT_NAME = "x-go2-component-name";
	/**
	 * publisher info header: some info about the client who publish the message
	 */
	public static final String X_GO2_HEADER_PUBLISHER_INFO = "x-go2-publisher-info";
	/**
	 * token issuer header: the issuer of the jwt token
	 */
	public static final String X_GO2_HEADER_TOKEN_ISSUER = "x-go2-header-token-issuer";
	/**
	 * token account header: the account to which the token was issued
	 */
	public static final String X_GO2_HEADER_TOKEN_ISSUED_TO = "x-go2-header-token-issued-to";
	/**
	 * token claims header: the claims of the account to which the token was issued
	 */
	public static final String X_GO2_HEADER_TOKEN_CLAIMS = "x-go2-header-token-claims";

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public static final String FANOUT = ExchangeTypes.FANOUT;
	public static final String DIRECT = ExchangeTypes.DIRECT;
	public static final String TOPIC = ExchangeTypes.TOPIC;
	public static final String DELAYED = "delayed";

}
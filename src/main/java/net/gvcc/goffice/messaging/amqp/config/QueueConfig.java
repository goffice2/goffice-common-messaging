package net.gvcc.goffice.messaging.amqp.config;

import org.apache.commons.lang3.StringUtils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.gvcc.goffice.messaging.amqp.Constants;
import net.gvcc.goffice.messaging.amqp.Helper;

/**
 * 
 *
 * <p>
 * The <code>QueueConfig</code> class
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
public class QueueConfig extends BaseConfig {
	@Setter(value = AccessLevel.PROTECTED)
	private QueueType type;

	private Boolean enable;

	private String routingKey;

	private int retryInterval;

	private int retryMaxAttempts;

	private int expire;

	private int concurrentConsumers = 1;

	private int concurrentConsumersMax = 1;

	public String getRoutingKey() {
		String value = "";
		String exchangeType = getExchange().getType();
		if (!Constants.FANOUT.equals(exchangeType)) {
			value = StringUtils.defaultIfBlank(this.routingKey, getName());
			if (StringUtils.isBlank(value)) {
				final String msg = "Missing routing key for queue <".concat(getName()).concat(">: ") //
						.concat("mandatory for queues which have to bind to exchange type <").concat(exchangeType).concat(">");
				throw new RuntimeException(msg);
			}
		}
		return value;
	}

	public ExchangeConfig getExchange() {
		return getConfig().getExchangeConfig();
	}

	public void setRetryInterval(String retryInterval) {
		this.retryInterval = Helper.parseInterval(retryInterval);
	}

	public void setExpire(String expire) {
		this.expire = Helper.parseInterval(expire);
	}

	public int getConcurrentConsumersMax() {
		return Math.max(this.concurrentConsumersMax, this.concurrentConsumers);
	}

	protected boolean isEnabled() {
		return Boolean.TRUE == getEnable();
	}
}

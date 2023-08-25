package net.gvcc.goffice.messaging.amqp.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 
 *
 * <p>
 * The <code>QueueGroupConfig</code> class
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@NoArgsConstructor
public class QueueGroupConfig {
	@Getter(value = AccessLevel.PROTECTED)
	private MessagingSystemConfigProperties owner;

	@Setter
	@Getter
	private String name;

	@Setter
	private String exchangeName;

	@Setter
	private Map<String /* element type */, ? super BaseConfig> elements = new HashMap<>();

	public void init(MessagingSystemConfigProperties owner, String configName) {
		this.owner = owner;
		this.name = configName;

		initElement(getQueueConfig(), QueueType.queue); // same as exchange
		initElement(getDlqConfig(), QueueType.dlq);
		initElement(getParkingLotConfig(), QueueType.parkinglot);
	}

	private void initElement(QueueConfig element, QueueType type) {
		element.setConfig(this);

		ExchangeConfig exchangeConfig = element.getExchange();
		String exchangeName = exchangeConfig.getName();

		String elementName;
		if (QueueType.queue == type) {
			elementName = StringUtils.defaultIfBlank(element.getName(), this.name);

			String exchangePrefix = exchangeName.concat(":");
			if (!elementName.startsWith(exchangePrefix)) {
				elementName = exchangePrefix.concat(elementName);
			}
		} else {
			QueueConfig main = getQueueConfig();
			elementName = StringUtils.defaultIfBlank(element.getName(), main.getName());
			elementName = elementName.concat(":").concat(type.name());

			if (element.getEnable() == null) {
				element.setEnable(main.getEnable()); // DLQ and PARKINGLOT (unless explicitly defined) are enabled as the MAIN queue
			}
		}
		element.setName(elementName);
	}

	public ExchangeConfig getExchangeConfig() {
		ExchangeConfig exchangeConfig = null;

		// if a name of exchange is EXPLICITLY defined, we look for it
		if (StringUtils.isNotBlank(this.exchangeName)) {
			exchangeConfig = owner.getExchanges().get(this.exchangeName);
			if (exchangeConfig == null) {
				throw new RuntimeException("No exchange found with name: ".concat(this.exchangeName));
			}
		} else {
			Map<String, ExchangeConfig> exchanges = owner.getExchanges();

			// if a name of exchange is NOT EXPLICITLY defined, ...
			if (exchanges.size() == 1) {
				// ...we use the unique exchange (if one)
				exchangeConfig = exchanges.values().stream().findAny().get();
			} else if (!exchanges.isEmpty()) {
				// ...we look for an exchange with the CONFIG GROUP NAME
				exchangeConfig = owner.getExchanges().get(this.name);
			}

			if (exchangeConfig == null) {
				throw new RuntimeException("No exchange found for config name: ".concat(this.name));
			}
		}

		return exchangeConfig;
	}

	public QueueConfig getQueueConfig() {
		QueueConfig element = (QueueConfig) elements.get(QueueType.queue.name());
		if (element == null) { // element.getName()
			element = new QueueConfig();
			setQueue(element);
		}
		return element;
	}

	// TIP: the name of this method MUST BE "set" + "NAME_OF_PROPERTY"
	// e.g.: goffice.amqp.queues[XXXX].queue.YYYY=ZZZZ -> setQueue (NOT setQueueConfig)
	public void setQueue(QueueConfig element) {
		element.setType(QueueType.queue);
		elements.put(QueueType.queue.name(), element);
	}

	public DlqConfig getDlqConfig() {
		DlqConfig element = (DlqConfig) elements.get(QueueType.dlq.name());
		if (element == null) {
			element = new DlqConfig();
			setDlq(element);
		}
		return element;
	}

	// TIP: the name of this method MUST BE "set" + "NAME_OF_PROPERTY"
	// e.g.: goffice.amqp.queues[XXXX].dlq.YYYY=ZZZZ -> setQueue (NOT setDlqConfig)
	public void setDlq(DlqConfig element) {
		element.setType(QueueType.dlq);
		elements.put(QueueType.dlq.name(), element);
	}

	public ParkingLotConfig getParkingLotConfig() {
		ParkingLotConfig element = (ParkingLotConfig) elements.get(QueueType.parkinglot.name());
		if (element == null) {
			element = new ParkingLotConfig();
			setParkingLot(element);
		}
		return element;
	}

	// TIP: the name of this method MUST BE "set" + "NAME_OF_PROPERTY"
	// e.g.: goffice.amqp.queues[XXXX].parkinglot.YYYY=ZZZZ -> setParkingLot (NOT setParkingLotConfig)
	public void setParkingLot(ParkingLotConfig element) {
		element.setType(QueueType.parkinglot);
		elements.put(QueueType.parkinglot.name(), element);
	}
}

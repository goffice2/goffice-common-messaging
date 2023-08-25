package net.gvcc.goffice.messaging.amqp.consumer;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import net.gvcc.goffice.messaging.amqp.config.MessagingSystemConfigProperties;
import net.gvcc.goffice.messaging.amqp.config.QueueConfig;
import net.gvcc.goffice.messaging.amqp.config.QueueGroupConfig;
import net.gvcc.goffice.messaging.amqp.exception.QueueConfigException;
import net.gvcc.goffice.messaging.amqp.exception.QueueNotFoundException;

/**
 * 
 *
 * <p>
 * The <code>GofficeConsumerHelper</code> class
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@Component("gofficeConsumerHelper")
@Import(MessagingSystemConfigProperties.class)
public class GofficeConsumerHelper {
	@Autowired
	private MessagingSystemConfigProperties messagingSystemConfig;

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public String getDataQueueName(String configName) throws QueueConfigException {
		QueueGroupConfig groupConfig = messagingSystemConfig.getQueueGroupConfig(configName);
		return groupConfig == null ? null : groupConfig.getQueueConfig().getName();
	}

	public String getDlqQueueName(String configName) throws QueueConfigException {
		QueueGroupConfig groupConfig = messagingSystemConfig.getQueueGroupConfig(configName);
		return groupConfig == null ? null : groupConfig.getDlqConfig().getName();
	}

	public String getParkinglotQueueName(String configName) throws QueueConfigException {
		QueueGroupConfig groupConfig = messagingSystemConfig.getQueueGroupConfig(configName);
		return groupConfig == null ? null : groupConfig.getParkingLotConfig().getName();
	}

	public QueueConfig getQueueByName(String queueName) throws QueueConfigException {
		List<QueueConfig> queueList = messagingSystemConfig.listGroupConfigNames().stream() //
				.filter(configName -> {
					try {
						QueueGroupConfig groupConfig = messagingSystemConfig.getQueueGroupConfig(configName);

						return queueName.equals(groupConfig.getQueueConfig().getName()) //
								|| //
								queueName.equals(groupConfig.getDlqConfig().getName()) //
								|| //
								queueName.equals(groupConfig.getParkingLotConfig().getName());
					} catch (QueueConfigException e) {
						throw new RuntimeException(e);
					}
				}) //
				.map(configName -> {
					try {
						QueueGroupConfig groupConfig = messagingSystemConfig.getQueueGroupConfig(configName);

						QueueConfig queue = null;

						if (queueName.equals(groupConfig.getQueueConfig().getName())) {
							queue = groupConfig.getQueueConfig();
						} else if (queueName.equals(groupConfig.getDlqConfig().getName())) {
							queue = groupConfig.getDlqConfig();
						} else if (queueName.equals(groupConfig.getParkingLotConfig().getName())) {
							queue = groupConfig.getParkingLotConfig();
						}

						return queue;
					} catch (QueueConfigException e) {
						throw new RuntimeException(e);
					}
				}) //
				.collect(Collectors.toList());

		if (queueList.isEmpty()) {
			throw new QueueNotFoundException(queueName, "Queue was not configured. Name=".concat(queueName));
		}

		if (queueList.size() != 1) {
			throw new QueueNotFoundException(queueName, "Found more than one queue!!. Name=".concat(queueName));
		}

		return queueList.get(0);
	}
}

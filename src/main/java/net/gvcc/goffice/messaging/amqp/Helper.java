package net.gvcc.goffice.messaging.amqp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 
 *
 * <p>
 * The <code>Helper</code> class
 * </p>
 * <p>
 * Data: 06 giu 2023
 * </p>
 * 
 * @author renzo poli
 * @version 1.0
 */
@Component
public class Helper {
	private static Logger LOGGER = LoggerFactory.getLogger(Helper.class);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// example: goffice.common.messaging.consumer.tagTemplate={network-info}@{component-name} | {strategy} | {uuid}
	private static enum PladeHoldersForConsumerTag {
		COMPONENT_NAME, // the name of the component
		NETWORK_INFO, // the network info (pod-ip and pod-name)
		UUID, // a random unique key
		STRATEGY; // the default value as provided by the AMQP framework

		public String asLabel() {
			return name().toLowerCase().replace("_", "-");
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Value("${GO2_COMPONENT_NAME:N/D}")
	private String componentName;

	@Value("${GO2_POD_IP:}")
	private String podIp;

	@Value("${GO2_POD_NAME:}")
	private String podName;

	@Value("${goffice.common.messaging.networkInfo.useDomain:google.com}")
	private String networkInfoUseDomain;

	@Value("${goffice.common.messaging.networkInfo.usePort:80}")
	private int networkInfoUsePort;

	@Value("${goffice.common.messaging.consumer.tagTemplate:{network-info}@{component-name} | {strategy} | {uuid}}")
	private String consumerTagTemplate;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private static final Pattern INTERVAL_PARSER = Pattern.compile("^(\\d+)([smhd]?)$"); // Seconds, Minutes, Hours, Days

	/**
	 * Parse the text value. E.g.: 10 -> ten minutes, 2s -> two seconds, 5h -> five hours
	 * 
	 * If flag is omitted, then minutes is the default value
	 * 
	 * @param text
	 * @return the interval in seconds
	 */
	public static int parseInterval(String text) {
		LOGGER.debug("parseRetryInterval - START");

		int interval = 0; // in milliseconds

		text = StringUtils.trimToNull(text);
		if (text != null) {
			Matcher matcher = INTERVAL_PARSER.matcher(text);
			if (matcher.matches()) {
				int value = Integer.parseInt(matcher.group(1));
				String flag = matcher.groupCount() == 2 ? matcher.group(2) : "m"; // default: minutes

				int multiplier = 1;

				if ("d".equals(flag)) {
					multiplier *= 24;
					flag = "h";
				}

				if ("h".equals(flag)) {
					multiplier *= 60;
					flag = "m";
				}

				if ("m".equals(flag)) {
					multiplier *= 60;
					flag = "s";
				}

				if ("s".equals(flag)) {
					multiplier *= 1000;
				}

				interval = value * multiplier;
			}
		}

		LOGGER.debug("parseRetryInterval - value={}, interval={}", text, interval);
		LOGGER.debug("parseRetryInterval - END");

		return interval;
	}

	public static String toHuman(int milliseconds) {
		String text = "";

		int value = (int) (milliseconds / 1000.0 + 0.5);
		if (value < 60) {
			text = String.format("%d seccond%s", value, value > 1 ? "s" : "");
		} else {
			int min = value / 60;
			int sec = value % 60;

			if (sec == 0) {
				text = String.format("%d minute%s", min, min > 1 ? "s" : "");
			} else {
				text = String.format("%d min %d sec", min, sec);
			}
		}

		return text;
	}

	public String getNetworkInfo() {
		LOGGER.debug("getNetworkInfo - START");

		String name = StringUtils.trimToNull(podName);
		String ip = StringUtils.trimToNull(podIp);

		if (ip == null) {
			// try (final DatagramSocket socket = new DatagramSocket()) {
			// socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
			// ip = StringUtils.trimToNull(socket.getLocalAddress().getHostAddress());
			// } catch (UnknownHostException | SocketException e) {
			// LOGGER.warn("getNetworkInfo", e);
			// }
			//
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(networkInfoUseDomain, networkInfoUsePort));
				ip = StringUtils.trimToNull(socket.getLocalAddress().getHostAddress());
			} catch (IOException e) {
				LOGGER.warn("getNetworkInfo", e);
			}
		}

		if (name == null || ip == null) {
			try {
				InetAddress localhost = InetAddress.getLocalHost();
				name = StringUtils.defaultIfEmpty(name, localhost.getHostName());
				ip = StringUtils.defaultIfEmpty(ip, localhost.getHostAddress());
			} catch (UnknownHostException e) {
				LOGGER.warn("getNetworkInfo", e);
			}
		}

		name = StringUtils.defaultIfEmpty(name, "user.name=".concat(System.getProperty("user.name")));
		ip = StringUtils.trimToEmpty(ip);

		String networkInfo = ip == null ? name : String.format("%s/%s", name, ip);

		LOGGER.debug("getNetworkInfo - networkInfo={}", networkInfo);
		LOGGER.debug("getNetworkInfo - END");

		return networkInfo;
	}

	public String getComponentName() {
		return componentName;
	}

	public String parseConsumerTagTemplate(String consumerTagStrategy) {
		LOGGER.debug("parseConsumerTagTemplate - START");

		LOGGER.debug("parseConsumerTagTemplate - consumer tag strategy={}", consumerTagStrategy);

		String[] text = new String[] { consumerTagTemplate };

		Arrays.asList(PladeHoldersForConsumerTag.values()).stream() //
				.forEach(placeholder -> {
					String value = "";
					switch (placeholder) {
						case COMPONENT_NAME:
							value = componentName;
							break;
						case NETWORK_INFO:
							value = getNetworkInfo();
							break;
						case UUID:
							value = UUID.randomUUID().toString();
							break;
						case STRATEGY:
							value = consumerTagStrategy;
							break;
					}
					text[0] = text[0].replaceAll("\\{\\s*".concat(placeholder.asLabel()).concat("\\s*\\}"), value);
				});

		LOGGER.debug("parseConsumerTagTemplate - consumer tag={}", text[0]);
		LOGGER.debug("parseConsumerTagTemplate - END");

		return text[0];
	}
}

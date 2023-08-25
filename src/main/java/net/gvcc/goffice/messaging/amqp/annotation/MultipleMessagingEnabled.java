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
package net.gvcc.goffice.messaging.amqp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.gvcc.goffice.messaging.amqp.config.MessagingSystemConfigProperties;
import net.gvcc.goffice.messaging.amqp.config.MessagingSystemConfigurator;
import net.gvcc.goffice.messaging.amqp.producer.GofficeMessageProducer;

/**
 * <p>
 * The <code>FanoutMessagingEnabled</code> class
 * </p>
 * <p>
 * this annotation is to be used to configure microservice modules that will make use of the asynchronous mechanism of queues must be added in the configuration spring class
 * </p>
 * <p>
 * Data: 10 mag 2022
 * </p>
 * 
 * @author cristian muraca
 * @version 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Configuration
@EnableConfigurationProperties({ MessagingSystemConfigurator.class })
@Import({ MessagingSystemConfigProperties.class, MessagingSystemConfigurator.class, GofficeMessageProducer.class })
public @interface MultipleMessagingEnabled {
}
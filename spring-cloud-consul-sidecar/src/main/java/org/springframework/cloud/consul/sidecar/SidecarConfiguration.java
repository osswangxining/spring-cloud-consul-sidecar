/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.consul.sidecar;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.actuator.HasFeatures;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties;
import org.springframework.cloud.consul.discovery.HeartbeatProperties;
import org.springframework.cloud.consul.serviceregistry.ConsulAutoRegistration;
import org.springframework.cloud.consul.serviceregistry.ConsulRegistrationCustomizer;
import org.springframework.cloud.consul.serviceregistry.ConsulServiceRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import com.ecwid.consul.v1.agent.model.NewService;

/**
 * Sidecar Configuration
 * <p>
 * Depends on {@link SidecarProperties} property. 
 * </p>
 * @author Xi Ning Wang
 *
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty(value = "spring.cloud.consul.sidecar.enabled", matchIfMissing = true)
public class SidecarConfiguration {

	@Bean
	public HasFeatures Feature() {
		return HasFeatures.namedFeature("Consul Sidecar", SidecarConfiguration.class);
	}

	@Bean
	public SidecarProperties sidecarProperties() {
		return new SidecarProperties();
	}

	@Bean
	@ConditionalOnMissingBean
	public ConsulAutoRegistration consulRegistration(ConsulDiscoveryProperties properties, ApplicationContext applicationContext,
			ObjectProvider<List<ConsulRegistrationCustomizer>> registrationCustomizers, HeartbeatProperties heartbeatProperties) {
		return registration(properties, applicationContext, registrationCustomizers.getIfAvailable(), heartbeatProperties);
	}
	

	@Autowired
	private InetUtils inetUtils;
	
	@Value("${consul.instance.hostname:${CONSUL_INSTANCE_HOSTNAME:}}")
	private String hostname;

	@Autowired
	private ConfigurableEnvironment env;
	
	private  ConsulAutoRegistration registration(ConsulDiscoveryProperties properties, ApplicationContext context,
			List<ConsulRegistrationCustomizer> registrationCustomizers,
			HeartbeatProperties heartbeatProperties) {
		RelaxedPropertyResolver propertyResolver = new RelaxedPropertyResolver(context.getEnvironment());

		SidecarProperties sidecarProperties = sidecarProperties();
		int port = sidecarProperties.getPort();
		String hostname = sidecarProperties.getHostname();
		String ipAddress = sidecarProperties.getIpAddress();
		if (!StringUtils.hasText(hostname) && StringUtils.hasText(this.hostname)) {
			hostname = this.hostname;
		}
		if(!StringUtils.hasText(hostname)) {
			hostname = properties.getHostname();
		}
		URI healthUri = sidecarProperties.getHealthUri();
		URI homePageUri = sidecarProperties.getHomePageUri();
		NewService service = new NewService();
		
		String appName = ConsulAutoRegistration.getAppName(properties, propertyResolver);
		service.setId(ConsulAutoRegistration.getInstanceId(properties, context));
		if(!properties.isPreferAgentAddress()) {
			service.setAddress(hostname);
		}
		service.setName(ConsulAutoRegistration.normalizeForDns(appName));
		service.setTags(ConsulAutoRegistration.createTags(properties));

		if (properties.getPort() != null) {
			service.setPort(properties.getPort());
			// we know the port and can set the check
			ConsulAutoRegistration.setCheck(service, properties, context, heartbeatProperties);
		}

		ConsulAutoRegistration registration = new ConsulAutoRegistration(service, properties, context, heartbeatProperties);
		ConsulAutoRegistration.customize(registrationCustomizers, registration);
		return registration;
	}

	@Bean
	public LocalApplicationHealthIndicator localApplicationHealthIndicator() {
		return new LocalApplicationHealthIndicator();
	}

	@Bean
	public SidecarController sidecarController() {
		return new SidecarController();
	}

}

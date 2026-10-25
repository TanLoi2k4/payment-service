package com.tlcn.payment_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * A load-balanced RestTemplate for internal service-to-service communication.
     */
    @Bean
    @LoadBalanced
    @Primary
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }

    @Bean("externalRestTemplate")
    public RestTemplate externalRestTemplate() {
        return new RestTemplate();
    }
}

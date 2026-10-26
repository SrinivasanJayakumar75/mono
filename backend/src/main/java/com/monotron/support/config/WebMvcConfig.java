package com.monotron.support.config;

import com.monotron.support.billing.BillingAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC interceptors that are not part of the security filter chain. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final BillingAccessInterceptor billingAccessInterceptor;

    public WebMvcConfig(BillingAccessInterceptor billingAccessInterceptor) {
        this.billingAccessInterceptor = billingAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(billingAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/billing/**", "/api/widget/**");
    }
}

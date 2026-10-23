package ru.strata.tasks.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class WebLimitsConfig {

    /**
     * Регистрируется с наивысшим приоритетом: фильтр срабатывает до цепочки Spring Security,
     * то есть до аутентификации и без затрат на разбор заголовков.
     */
    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter() {
        FilterRegistrationBean<RequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodyLimitFilter(RequestBodyLimitFilter.MAX_BODY_BYTES));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}

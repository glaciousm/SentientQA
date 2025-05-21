package com.projectoracle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for the Web MVC
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Configure simple view controllers for the web UI
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Root now handled by Spring Security login
        registry.addViewController("/").setViewName("forward:/dashboard.html");
        registry.addViewController("/login").setViewName("forward:/login.html");
        
        // Main app routes
        registry.addViewController("/home").setViewName("forward:/dashboard.html");
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
        registry.addViewController("/test-management").setViewName("forward:/test-management.html");
        registry.addViewController("/security-tests").setViewName("forward:/security-tests.html");
        registry.addViewController("/compliance-tests").setViewName("forward:/compliance-tests.html");
    }
}
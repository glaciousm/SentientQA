package com.projectoracle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
        // Login now handled by LoginController
        
        // Main app routes
        registry.addViewController("/home").setViewName("forward:/dashboard.html");
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
        registry.addViewController("/test-management").setViewName("forward:/test-management.html");
        registry.addViewController("/security-tests").setViewName("forward:/security-tests.html");
        registry.addViewController("/compliance-tests").setViewName("forward:/compliance-tests.html");
    }
    
    /**
     * Configure static resource handlers
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Register static resource handlers
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
                
        // For webjars if needed
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
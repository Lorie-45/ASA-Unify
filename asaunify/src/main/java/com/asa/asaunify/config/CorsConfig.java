//package com.asa.asaunify.config;
//
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.springframework.web.filter.CorsFilter;
//
//import java.util.List;
//
//@Configuration
//public class CorsConfig {
//
//    @Value("${websocket.allowed-origins}")
//    private String allowedOrigin;
//
//    @Bean
//    public CorsFilter corsFilter() {
//        CorsConfiguration config = new CorsConfiguration();
//
//        // Allow the React frontend origin
//        config.setAllowedOrigins(List.of(allowedOrigin));
//
//        // Allow all standard HTTP methods
//        config.setAllowedMethods(List.of(
//                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
//        ));
//
//        // Allow these headers in requests
//        config.setAllowedHeaders(List.of(
//                "Authorization",
//                "Content-Type",
//                "Accept",
//                "X-Requested-With",
//                "Cache-Control"
//        ));
//
//        // Allow Authorization header to be read by the frontend
//        config.setExposedHeaders(List.of("Authorization"));
//
//        // Allow cookies and Authorization headers
//        config.setAllowCredentials(true);
//
//        // Cache preflight response for 1 hour
//        config.setMaxAge(3600L);
//
//        UrlBasedCorsConfigurationSource source =
//                new UrlBasedCorsConfigurationSource();
//
//        // Apply CORS config to all endpoints
//        source.registerCorsConfiguration("/**", config);
//
//        return new CorsFilter(source);
//    }
//}


package com.asa.asaunify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
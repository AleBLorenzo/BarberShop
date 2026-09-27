package com.barbershop.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

class CorsConfigTest {

    @Test
    void allowsConfiguredHttpsOriginOnApiPreflight() throws Exception {
        var source = new CorsConfig().corsConfigurationSource(
                " https://frontend.example.com , https://preview.example.com ");
        var request = new MockHttpServletRequest("OPTIONS", "/api/workers");
        request.addHeader("Origin", "https://frontend.example.com");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        var response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("CORS preflight should be handled before the filter chain.");
        });

        assertEquals(200, response.getStatus());
        assertEquals("https://frontend.example.com", response.getHeader("Access-Control-Allow-Origin"));
        assertNotNull(response.getHeader("Access-Control-Allow-Methods"));
        assertNotNull(response.getHeader("Access-Control-Allow-Headers"));
        assertFalse("*".equals(response.getHeader("Access-Control-Allow-Origin")));
    }
}
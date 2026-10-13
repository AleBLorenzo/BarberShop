package com.barbershop.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita la ejecucion asincrona (@Async) usada por el envio de correos
 * para que el SMTP nunca bloquee la respuesta de creacion de reservas.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}

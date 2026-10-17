package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.EmailService;
import com.barbershop.backend.util.IcsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spec booking-without-email-degradation — "Sin email no se envían
 * notificaciones por correo": con email ausente, ni la creación (confirmación)
 * ni la transición a {@code COMPLETADA} (invitación a reseñar) intentan un
 * envío SMTP; con email presente sí se intenta y un fallo de SMTP no altera la
 * reserva.
 *
 * <p><b>Sin Mockito</b> (el mock maker inline no puede instrumentar clases
 * concretas en el JVM de CI, Java 25): se usan dobles MANUALES —
 * {@link EmailEspia} (subclase real que registra el email y delega en la
 * guarda) y un {@link JavaMailSender} por {@code Proxy} dinámico
 * ({@link SmtpEspia}) que cuenta/falla los envíos. El SMTP se fuerza a
 * "configurado" ({@code spring.mail.host=localhost}) para que la guarda de
 * email sea la ÚNICA razón por la que no se envía.</p>
 *
 * <p>H2 compartida: datos únicos por UUID, {@code limpiarContadores()} en
 * {@code @BeforeEach}. Sin {@code Thread.sleep()}: la aserción usa el espía y,
 * cuando hace falta esperar al listener {@code @Async}, un
 * {@code CountDownLatch} con timeout (no un sondeo temporal).</p>
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "app.mail.enabled=true",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"
})
@AutoConfigureMockMvc
class NoEmailNoNotificationTest {

    private static final String PASSWORD = "NoEmailNoNotif12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    /** El primario en este contexto es el espía manual (ver {@link ConfigCorreo}). */
    @Autowired
    private EmailEspia emailEspia;

    @Autowired
    private SmtpEspia smtpEspia;

    private Long trabajadorId;
    private Long servicioId;
    private LocalDate fecha;

    @BeforeEach
    void fixtures() {
        rateLimiter.limpiarContadores();
        emailEspia.reset();
        smtpEspia.reset();

        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Sin Notificacion " + sufijo)
                .email("sin-notificacion-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        trabajadorId = trabajador.getId();

        Servicio servicio = servicioRepository.save(Servicio.builder()
                .nombre("Corte notificacion " + sufijo)
                .precio(new java.math.BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
        servicioId = servicio.getId();

        fecha = diaHabilFuturo();
    }

    @Test
    void creacionYCompletadoSinEmailNoIntentanEnviarCorreo() throws Exception {
        Long reservaId = crearReservaPublica(null, "09:00");

        assertThat(emailEspia.esperarConfirmacion())
                .as("el listener de reserva creada alcanza la guarda del email ausente")
                .isTrue();
        assertThat(emailEspia.emailConfirmacion()).isNull();
        assertThat(smtpEspia.envios.get())
                .as("sin email no se intenta ningún envío SMTP en la confirmación")
                .isZero();

        // Transición a COMPLETADA → evento de reserva completada (sin email)
        String token = loginWorker();
        mockMvc.perform(put("/api/worker/reservas/" + reservaId + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .param("estado", "COMPLETADA")
                        .param("metodoPago", "EFECTIVO"))
                .andExpect(status().isOk());

        assertThat(emailEspia.esperarInvitacion())
                .as("el listener de reserva completada alcanza la guarda del email ausente")
                .isTrue();
        assertThat(emailEspia.emailInvitacion()).isNull();
        assertThat(smtpEspia.envios.get())
                .as("sin email no se envía la invitación a reseñar")
                .isZero();
    }

    @Test
    void conEmailSeIntentaElEnvioYUnFalloDeSmtpNoAlteraLaReserva() throws Exception {
        smtpEspia.fallar = true;
        String email = "aviso-" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.com";

        Long reservaId = crearReservaPublica(email, "09:00");

        // La reserva queda creada igualmente pese al fallo de SMTP (AFTER_COMMIT + try/catch)
        assertThat(reservaRepository.findById(reservaId))
                .as("un fallo de correo nunca afecta a la reserva")
                .isPresent();

        assertThat(emailEspia.esperarConfirmacion()).isTrue();
        assertThat(emailEspia.emailConfirmacion()).isEqualTo(email);
        assertThat(smtpEspia.envios.get())
                .as("con email presente sí se intenta el envío (aunque falle)")
                .isGreaterThanOrEqualTo(1);
    }

    // --- helpers de petición ---

    private Long crearReservaPublica(String email, String hora) throws Exception {
        String telefono = telefonoUnico();
        StringBuilder body = new StringBuilder("{")
                .append("\"servicioId\":").append(servicioId).append(',')
                .append("\"trabajadorId\":").append(trabajadorId).append(',')
                .append("\"fecha\":\"").append(fecha).append("\",")
                .append("\"horaInicio\":\"").append(hora).append("\",")
                .append("\"clienteNombre\":\"Cliente Notificacion\",")
                .append("\"clienteTelefono\":\"").append(telefono).append("\"");
        if (email != null) {
            body.append(",\"clienteEmail\":\"").append(email).append("\"");
        }
        body.append('}');

        MvcResult result = mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.20.30.30")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Long id = data.path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }

    private String loginWorker() throws Exception {
        String email = trabajadorRepository.findById(trabajadorId).orElseThrow().getEmail();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("accessToken").asText();
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (la normalización no lo recorta). */
    private static String telefonoUnico() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** Próximo día hábil futuro (sin domingos: día cerrado por negocio). */
    private static LocalDate diaHabilFuturo() {
        LocalDate dia = LocalDate.now().plusDays(2);
        while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    // --- dobles manuales ---

    /**
     * Espía del {@code EmailService}: registra el email recibido y delega en la
     * guarda real ({@code null}/blank → no envía). El {@code CountDownLatch}
     * señala que el listener {@code @Async} ya invocó el servicio.
     */
    static class EmailEspia extends EmailService {

        private volatile CountDownLatch confirmacion = new CountDownLatch(1);
        private volatile CountDownLatch invitacion = new CountDownLatch(1);
        private final AtomicReference<String> emailConfirmacion = new AtomicReference<>();
        private final AtomicReference<String> emailInvitacion = new AtomicReference<>();

        EmailEspia(ObjectProvider<JavaMailSender> mailSenderProvider, IcsBuilder icsBuilder) {
            super(mailSenderProvider, icsBuilder);
        }

        void reset() {
            confirmacion = new CountDownLatch(1);
            invitacion = new CountDownLatch(1);
            emailConfirmacion.set(null);
            emailInvitacion.set(null);
        }

        @Override
        public void enviarConfirmacion(ReservaResponse r, String email) {
            emailConfirmacion.set(email);
            // Delegar en la guarda real ANTES de señalar el latch: así, cuando el
            // test despierta, el intento de envío (o su omisión) ya terminó y no
            // hay carrera entre la aserción y el hilo @Async.
            super.enviarConfirmacion(r, email);
            confirmacion.countDown();
        }

        @Override
        public void enviarInvitacionResena(ReservaResponse r, String email) {
            emailInvitacion.set(email);
            super.enviarInvitacionResena(r, email);
            invitacion.countDown();
        }

        boolean esperarConfirmacion() throws InterruptedException {
            return confirmacion.await(5, TimeUnit.SECONDS);
        }

        boolean esperarInvitacion() throws InterruptedException {
            return invitacion.await(5, TimeUnit.SECONDS);
        }

        String emailConfirmacion() {
            return emailConfirmacion.get();
        }

        String emailInvitacion() {
            return emailInvitacion.get();
        }
    }

    /** Estado mutable del servidor SMTP simulado (contador de envíos + modo fallo). */
    static class SmtpEspia {
        final AtomicInteger envios = new AtomicInteger();
        volatile boolean fallar = false;

        void reset() {
            envios.set(0);
            fallar = false;
        }
    }

    @TestConfiguration
    static class ConfigCorreo {

        @Bean
        SmtpEspia smtpEspia() {
            return new SmtpEspia();
        }

        /** {@code @Primary} solo en ESTE contexto: el listener recibe el espía. */
        @Bean
        @Primary
        EmailEspia emailEspia(ObjectProvider<JavaMailSender> mailSenderProvider, IcsBuilder icsBuilder) {
            return new EmailEspia(mailSenderProvider, icsBuilder);
        }

        /**
         * SMTP simulado: {@code createMimeMessage} devuelve un mensaje real y
         * {@code send} cuenta el intento (fallando si {@code fallar=true}), de
         * modo que la prueba distingue "no se intenta" de "se intenta y falla".
         */
        @Bean
        @Primary
        JavaMailSender javaMailSenderEspia(SmtpEspia espia) {
            return (JavaMailSender) Proxy.newProxyInstance(
                    JavaMailSender.class.getClassLoader(),
                    new Class<?>[]{JavaMailSender.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "createMimeMessage":
                                return new MimeMessage(Session.getInstance(new Properties()));
                            case "send":
                                espia.envios.incrementAndGet();
                                if (espia.fallar) {
                                    throw new MailSendException("SMTP caído (simulado)");
                                }
                                return null;
                            case "toString":
                                return "JavaMailSenderEspia";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return null;
                        }
                    });
        }
    }
}

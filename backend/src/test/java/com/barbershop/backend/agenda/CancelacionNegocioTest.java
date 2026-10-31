package com.barbershop.backend.agenda;

import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.model.AvisoCancelacion;
import com.barbershop.backend.model.CanalAviso;
import com.barbershop.backend.model.CanceladaPor;
import com.barbershop.backend.model.EstadoAviso;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.AvisoCancelacionRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.AvisoCancelacionService;
import com.barbershop.backend.service.EmailService;
import com.barbershop.backend.service.ReservaService;
import com.barbershop.backend.util.IcsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 4.8: cancelación por el negocio y aviso al cliente (D6, D7).
 *
 * <p>Mismo enfoque que {@code NoEmailNoNotificationTest}: sin Mockito, con
 * dobles manuales — un {@link EmailService} espía, un {@link JavaMailSender}
 * por {@code Proxy} que cuenta/falla los envíos (SMTP "configurado" con
 * {@code spring.mail.host=localhost}) y un {@link AvisoCancelacionService}
 * espía que señala cuándo el listener asíncrono registró el resultado. Las
 * esperas usan {@code CountDownLatch} con timeout, nunca {@code sleep}.</p>
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "app.mail.enabled=true",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"
})
@AutoConfigureMockMvc
class CancelacionNegocioTest extends AgendaTestSupport {

    @Autowired private ReservaService reservaService;
    @Autowired private AvisoCancelacionRepository avisoRepository;
    @Autowired private EmailEspia emailEspia;
    @Autowired private AvisoEspia avisoEspia;
    @Autowired private SmtpEspia smtpEspia;
    @Autowired private PlatformTransactionManager transactionManager;

    private Trabajador admin;
    private Trabajador ana;
    private Servicio corte;
    private String tokenAdmin;
    private LocalDate fecha;

    @BeforeEach
    void fixtures() throws Exception {
        smtpEspia.reset();
        admin = crearTrabajador("Admin Avisos", Rol.ADMIN);
        ana = crearTrabajador("Ana", Rol.WORKER);
        corte = crearServicio(30);
        tokenAdmin = login(admin);
        fecha = proximo(DayOfWeek.TUESDAY);
    }

    @Test
    void clienteConEmailRecibeElCorreoYElAvisoQuedaEnviado() throws Exception {
        String email = emailUnico();
        Reserva reserva = cita(email, "10:00");

        mockMvc.perform(post("/api/admin/reservas/" + reserva.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Barbero enfermo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.data.canceladaPor").value("ADMIN"))
                .andExpect(jsonPath("$.data.motivoCancelacion").value("Barbero enfermo"));

        AvisoCancelacion aviso = avisoRepository.findByReserva_Id(reserva.getId()).orElseThrow();
        assertThat(aviso.getCanal()).isEqualTo(CanalAviso.EMAIL);
        assertThat(avisoEspia.esperar(aviso.getId())).as("el listener registra el resultado").isTrue();

        assertThat(avisoRepository.findById(aviso.getId()).orElseThrow().getEstado()).isEqualTo(EstadoAviso.ENVIADO);
        assertThat(emailEspia.emails.get(reserva.getId())).isEqualTo(email);
        assertThat(smtpEspia.envios.get()).isGreaterThanOrEqualTo(1);
        Reserva tras = reservaRepository.findById(reserva.getId()).orElseThrow();
        assertThat(tras.getCanceladaPor()).isEqualTo(CanceladaPor.ADMIN);
        assertThat(tras.getMotivoCancelacion()).isEqualTo("Barbero enfermo");
    }

    @Test
    void clienteSinEmailGeneraAvisoTelefonicoSinIntentarEnviar() throws Exception {
        Reserva reserva = cita(null, "11:00");

        mockMvc.perform(post("/api/admin/reservas/" + reserva.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        assertThat(emailEspia.esperar(reserva.getId()))
                .as("el listener alcanza la guarda del email ausente").isTrue();
        assertThat(emailEspia.emails.get(reserva.getId())).isEqualTo(EmailEspia.SIN_EMAIL);
        assertThat(smtpEspia.envios.get()).as("sin email no hay envío SMTP").isZero();

        AvisoCancelacion aviso = avisoRepository.findByReserva_Id(reserva.getId()).orElseThrow();
        assertThat(aviso.getCanal()).isEqualTo(CanalAviso.TELEFONO);
        assertThat(aviso.getEstado()).isEqualTo(EstadoAviso.PENDIENTE);
        assertThat(idsPendientes()).contains(aviso.getId());

        MvcResult count = mockMvc.perform(get("/api/admin/avisos/pendientes/count")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(count).asLong()).isPositive();
    }

    @Test
    void falloDeSmtpDejaElAvisoFallidoYEnPendientes() throws Exception {
        smtpEspia.fallar = true;
        Reserva reserva = cita(emailUnico(), "12:00");

        mockMvc.perform(post("/api/admin/reservas/" + reserva.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cierre\"}"))
                .andExpect(status().isOk());

        AvisoCancelacion aviso = avisoRepository.findByReserva_Id(reserva.getId()).orElseThrow();
        assertThat(avisoEspia.esperar(aviso.getId())).isTrue();

        assertThat(avisoRepository.findById(aviso.getId()).orElseThrow().getEstado()).isEqualTo(EstadoAviso.FALLIDO);
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(ReservaEstado.CANCELADA);
        assertThat(idsPendientes()).contains(aviso.getId());
    }

    @Test
    void rollbackDeLaCancelacionNoEnviaCorreo() throws Exception {
        Reserva revertida = cita(emailUnico(), "13:00");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Reserva r = reservaRepository.findById(revertida.getId()).orElseThrow();
            reservaService.cancelarPorNegocio(r, "Se revierte", admin.getId());
            status.setRollbackOnly();
        });

        // Centinela: otra cancelación confirmada sí llega al listener
        Reserva centinela = cita(emailUnico(), "14:00");
        mockMvc.perform(post("/api/admin/reservas/" + centinela.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        assertThat(emailEspia.esperar(centinela.getId())).isTrue();

        assertThat(emailEspia.emails).doesNotContainKey(revertida.getId());
        assertThat(reservaRepository.findById(revertida.getId()).orElseThrow().getEstado())
                .isEqualTo(ReservaEstado.CONFIRMADA);
        assertThat(avisoRepository.findByReserva_Id(revertida.getId())).isEmpty();
    }

    @Test
    void cancelarUnaCitaCompletadaOCanceladaResponde400() throws Exception {
        Reserva completada = cita(null, "15:00");
        completada.setEstado(ReservaEstado.COMPLETADA);
        reservaRepository.save(completada);
        Reserva cancelada = cita(null, "16:00");
        cancelada.setEstado(ReservaEstado.CANCELADA);
        reservaRepository.save(cancelada);

        mockMvc.perform(post("/api/admin/reservas/" + completada.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/reservas/" + cancelada.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest());

        assertThat(reservaRepository.findById(completada.getId()).orElseThrow().getEstado())
                .isEqualTo(ReservaEstado.COMPLETADA);
        assertThat(avisoRepository.findByReserva_Id(completada.getId())).isEmpty();
    }

    @Test
    void cancelacionPorTokenDelClienteNoGeneraAviso() throws Exception {
        Reserva reserva = cita(emailUnico(), "17:00");

        mockMvc.perform(post("/api/public/reservas/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + reserva.getTokenCancelacion() + "\"}"))
                .andExpect(status().isOk());

        Reserva tras = reservaRepository.findById(reserva.getId()).orElseThrow();
        assertThat(tras.getEstado()).isEqualTo(ReservaEstado.CANCELADA);
        assertThat(tras.getCanceladaPor()).isEqualTo(CanceladaPor.CLIENTE);
        assertThat(avisoRepository.findByReserva_Id(reserva.getId())).isEmpty();
    }

    @Test
    void putAdminACanceladaUsaElMismoFlujoConAviso() throws Exception {
        Reserva reserva = cita(null, "18:00");

        mockMvc.perform(put("/api/admin/reservas/" + reserva.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CANCELADA\",\"motivoCancelacion\":\"Barbero enfermo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canceladaPor").value("ADMIN"));

        AvisoCancelacion aviso = avisoRepository.findByReserva_Id(reserva.getId()).orElseThrow();
        assertThat(aviso.getCanal()).isEqualTo(CanalAviso.TELEFONO);
        assertThat(aviso.getMotivo()).isEqualTo("Barbero enfermo");
    }

    @Test
    void marcarAvisadoLoSacaDePendientes() throws Exception {
        Reserva reserva = cita(null, "19:00");
        mockMvc.perform(post("/api/admin/reservas/" + reserva.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        Long avisoId = avisoRepository.findByReserva_Id(reserva.getId()).orElseThrow().getId();
        assertThat(idsPendientes()).contains(avisoId);

        mockMvc.perform(put("/api/admin/avisos/" + avisoId + "/avisado")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("AVISADO"))
                .andExpect(jsonPath("$.data.avisadoPorNombre").value(admin.getNombre()));

        assertThat(idsPendientes()).doesNotContain(avisoId);
        assertThat(avisoRepository.findById(avisoId).orElseThrow().getAvisadoAt()).isNotNull();
    }

    @Test
    void unTrabajadorNoPuedeCancelarNiVerAvisos() throws Exception {
        String tokenAna = login(ana);
        Reserva reserva = cita(null, "20:00");

        mockMvc.perform(post("/api/admin/reservas/" + reserva.getId() + "/cancelar")
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/avisos").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isForbidden());
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(ReservaEstado.CONFIRMADA);
    }

    // --- helpers ---

    private Reserva cita(String email, String hora) {
        return crearReserva(ana, corte, crearCliente(email), fecha, LocalTime.parse(hora), ReservaEstado.CONFIRMADA);
    }

    private List<Long> idsPendientes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/avisos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("pendientes", "true"))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : data(result)) {
            ids.add(n.path("id").asLong());
        }
        return ids;
    }

    private static String emailUnico() {
        return "cancel-" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.com";
    }

    // --- dobles manuales ---

    /** Registra a quién se intentó avisar por reserva y señala el fin de cada intento. */
    static class EmailEspia extends EmailService {
        static final String SIN_EMAIL = "<sin email>";
        final Map<Long, String> emails = new ConcurrentHashMap<>();
        private final Map<Long, CountDownLatch> latches = new ConcurrentHashMap<>();

        EmailEspia(ObjectProvider<JavaMailSender> mailSenderProvider, IcsBuilder icsBuilder) {
            super(mailSenderProvider, icsBuilder);
        }

        @Override
        public boolean enviarCancelacion(ReservaResponse r, String email, String motivo) {
            emails.put(r.getId(), email != null ? email : SIN_EMAIL);
            try {
                return super.enviarCancelacion(r, email, motivo);
            } finally {
                latch(r.getId()).countDown();
            }
        }

        boolean esperar(Long reservaId) throws InterruptedException {
            return latch(reservaId).await(5, TimeUnit.SECONDS);
        }

        private CountDownLatch latch(Long id) {
            return latches.computeIfAbsent(id, k -> new CountDownLatch(1));
        }
    }

    /**
     * Señala cuándo el listener registró (y confirmó) ENVIADO/FALLIDO de un
     * aviso. El {@code @Transactional(REQUIRES_NEW)} del método sobrescrito se
     * hereda (Spring busca la anotación en la jerarquía del método).
     */
    static class AvisoEspia extends AvisoCancelacionService {
        private final Map<Long, CountDownLatch> latches = new ConcurrentHashMap<>();

        AvisoEspia(AvisoCancelacionRepository repo, TrabajadorRepository trabajadorRepository) {
            super(repo, trabajadorRepository);
        }

        @Override
        public void marcarResultadoEnvio(Long avisoId, boolean enviado, String error) {
            // El commit de REQUIRES_NEW ocurre al salir del proxy, después de
            // este método: el latch se libera tras completar la transacción.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    latch(avisoId).countDown();
                }
            });
            super.marcarResultadoEnvio(avisoId, enviado, error);
        }

        boolean esperar(Long avisoId) throws InterruptedException {
            return latch(avisoId).await(5, TimeUnit.SECONDS);
        }

        private CountDownLatch latch(Long id) {
            return latches.computeIfAbsent(id, k -> new CountDownLatch(1));
        }
    }

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

        @Bean
        @Primary
        EmailEspia emailEspia(ObjectProvider<JavaMailSender> mailSenderProvider, IcsBuilder icsBuilder) {
            return new EmailEspia(mailSenderProvider, icsBuilder);
        }

        @Bean
        @Primary
        AvisoEspia avisoEspia(AvisoCancelacionRepository repo, TrabajadorRepository trabajadorRepository) {
            return new AvisoEspia(repo, trabajadorRepository);
        }

        @Bean
        @Primary
        JavaMailSender javaMailSenderEspia(SmtpEspia espia) {
            return (JavaMailSender) Proxy.newProxyInstance(
                    JavaMailSender.class.getClassLoader(),
                    new Class<?>[]{JavaMailSender.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createMimeMessage" -> new MimeMessage(Session.getInstance(new Properties()));
                        case "send" -> {
                            espia.envios.incrementAndGet();
                            if (espia.fallar) {
                                throw new MailSendException("SMTP caído (simulado)");
                            }
                            yield null;
                        }
                        case "toString" -> "JavaMailSenderEspia";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }
    }
}

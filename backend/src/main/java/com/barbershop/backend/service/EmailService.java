package com.barbershop.backend.service;

import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.util.IcsBuilder;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Correo de confirmacion de cita con todos los datos + adjunto .ics.
 * Degradacion elegante: sin SMTP configurado (o app.mail.enabled=false)
 * funciona en modo log; cualquier fallo se registra y no afecta a la reserva.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    /** Fecha larga con dia de la semana, igual que en la pantalla de exito (EEEE dd/MM/yyyy). */
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", new Locale("es"));

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final IcsBuilder icsBuilder;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Value("${app.mail.from:notificaciones@barbershop.local}")
    private String from;

    @Value("${app.business.name:TheTankBarber}")
    private String nombreNegocio;

    @Value("${app.business.maps-url:https://maps.app.goo.gl/12BLsdYKtLRCxFRe8}")
    private String mapsUrl;

    /** Base de la web para los enlaces "Ver mis citas" / "Volver al inicio" del correo. */
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public void enviarConfirmacion(ReservaResponse r, String email) {
        if (email == null || email.isBlank()) {
            log.debug("Reserva {} sin email: no hay confirmacion que enviar", r.getId());
            return;
        }
        JavaMailSender mailSender = senderOrNull();
        if (mailSender == null) {
            log.info("[MODO LOG] Confirmacion de la reserva {} para {} ({}, {}): SMTP no configurado",
                    r.getId(), email, r.getServicioNombre(), r.getFecha());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(asunto(r));
            helper.setText(html(r), true);
            helper.addAttachment("cita.ics",
                    new ByteArrayResource(icsBuilder.build(r).getBytes(StandardCharsets.UTF_8)),
                    "text/calendar");
            mailSender.send(message);
            log.info("Confirmacion de la reserva {} enviada a {}", r.getId(), email);
        } catch (Exception e) {
            // Nunca propaga: la reserva ya existe y la respuesta no se toca
            log.warn("No se pudo enviar el correo a {} (reserva {}): {}", email, r.getId(), e.getMessage());
        }
    }

    /**
     * Invitación a dejar reseña tras completar la cita: solo botón hacia la
     * ficha de Google Maps (mismo enlace que la web). Un envío por transición.
     */
    public void enviarInvitacionResena(ReservaResponse r, String email) {
        if (email == null || email.isBlank()) {
            log.debug("Reserva {} completada sin email: no hay invitacion a reseñar", r.getId());
            return;
        }
        JavaMailSender mailSender = senderOrNull();
        if (mailSender == null) {
            log.info("[MODO LOG] Invitacion a reseñar la reserva {} para {} ({}): SMTP no configurado",
                    r.getId(), email, r.getFecha());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("¿Qué tal tu cita en " + nombreNegocio + "?");
            helper.setText(htmlInvitacion(r), true);
            mailSender.send(message);
            log.info("Invitacion a reseñar la reserva {} enviada a {}", r.getId(), email);
        } catch (Exception e) {
            log.warn("No se pudo enviar la invitacion a reseñar a {} (reserva {}): {}",
                    email, r.getId(), e.getMessage());
        }
    }

    /** Sender SMTP o null si no hay correo configurado (se usa en modo log). */
    private JavaMailSender senderOrNull() {
        if (!enabled) {
            return null;
        }
        JavaMailSender sender = mailSenderProvider.stream().findFirst().orElse(null);
        if (sender == null || mailHost == null || mailHost.isBlank()) {
            return null;
        }
        return sender;
    }

    private String asunto(ReservaResponse r) {
        return "Tu cita en " + nombreNegocio + ": " + r.getServicioNombre()
                + " el " + r.getFecha().format(FECHA)
                + " a las " + r.getHoraInicio().format(HORA);
    }

    private String html(ReservaResponse r) {
        // Base para los enlaces "Ver mis citas" / "Volver al inicio" (sin barra final)
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        String duracion = r.getDuracionMinutos() != null ? r.getDuracionMinutos() + " min" : "—";
        // 19 especificadores %s (width:100%% escapado) <-> 19 argumentos
        return """
                <html><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2933;line-height:1.5">
                <div style="max-width:520px;margin:0 auto;padding:16px">
                  <h2 style="color:#b8860b;margin-bottom:4px">💈 ¡Cita confirmada!</h2>
                  <p style="font-size:16px">Te esperamos el <b>%s</b> a las <b>%s</b>.</p>
                  <p>Hola <b>%s</b>, tu cita en %s ha quedado registrada:</p>
                  <table cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%%;border:1px solid #e5e7eb">
                    <tr style="background:#f8fafc"><td><b>Reserva nº</b></td><td>%s</td></tr>
                    <tr><td><b>Cliente</b></td><td>%s</td></tr>
                    <tr style="background:#f8fafc"><td><b>Teléfono</b></td><td>%s</td></tr>
                    <tr><td><b>Servicio</b></td><td>%s</td></tr>
                    <tr style="background:#f8fafc"><td><b>Duración</b></td><td>%s</td></tr>
                    <tr><td><b>Trabajador</b></td><td>%s</td></tr>
                    <tr style="background:#f8fafc"><td><b>Fecha</b></td><td>%s</td></tr>
                    <tr><td><b>Horario</b></td><td>%s – %s</td></tr>
                    <tr style="background:#f8fafc"><td><b>Precio</b></td><td>%s</td></tr>
                    <tr><td><b>Estado</b></td><td>%s</td></tr>
                  </table>
                  <p>Guarda este token para cancelar o reprogramar:<br>
                  <code style="background:#f1f5f9;padding:4px 8px;border-radius:4px">%s</code></p>
                  <p>📎 Adjuntamos la cita en formato <b>.ics</b>: tócala para añadirla a tu
                  calendario con un recordatorio 30 minutos antes.</p>
                  <p>🔗 <a href="%s">Ver mis citas</a> · <a href="%s">Volver al inicio</a></p>
                  <p style="color:#64748b;font-size:12px">%s · Este es un mensaje automático, no respondas a él.</p>
                </div>
                </body></html>
                """.formatted(
                r.getFecha().format(FECHA_LARGA),
                r.getHoraInicio().format(HORA),
                esc(r.getClienteNombre()),
                esc(nombreNegocio),
                r.getId(),
                esc(r.getClienteNombre()),
                esc(r.getClienteTelefono()),
                esc(r.getServicioNombre()),
                duracion,
                esc(r.getTrabajadorNombre()),
                r.getFecha().format(FECHA_LARGA),
                r.getHoraInicio().format(HORA),
                r.getHoraFin().format(HORA),
                r.getPrecioTotal() != null ? r.getPrecioTotal() + " €" : "—",
                esc(r.getEstado()),
                esc(String.valueOf(r.getTokenCancelacion())),
                esc(base + "/mis-citas"),
                esc(base + "/"),
                esc(nombreNegocio)
        );
    }

    /** CTA simple: botón a la ficha de Google Maps (mismo enlace que la web). */
    private String htmlInvitacion(ReservaResponse r) {
        return """
                <html><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2933;line-height:1.5">
                <div style="max-width:520px;margin:0 auto;padding:16px">
                  <h2 style="color:#b8860b;margin-bottom:4px">💈 ¡Cita completada!</h2>
                  <p>Hola <b>%s</b>, esperamos que haya ido bien tu <b>%s</b>
                  con <b>%s</b> el %s a las %s.</p>
                  <p>¿Nos dejas una reseña? Tarda menos de un minuto y nos ayuda mucho:</p>
                  <p style="text-align:center;margin:24px 0">
                    <a href="%s"
                       style="background:#b8860b;color:#fff;padding:12px 24px;border-radius:6px;
                              text-decoration:none;font-weight:bold">
                      ⭐ Escríbenos en Google Maps
                    </a>
                  </p>
                  <p style="color:#64748b;font-size:12px">%s · Gracias por tu visita.</p>
                </div>
                </body></html>
                """.formatted(
                esc(r.getClienteNombre()),
                esc(r.getServicioNombre()),
                esc(r.getTrabajadorNombre()),
                r.getFecha().format(FECHA),
                r.getHoraInicio().format(HORA),
                esc(mapsUrl),
                esc(nombreNegocio)
        );
    }

    /** Escape basico de HTML para campos del cliente. */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

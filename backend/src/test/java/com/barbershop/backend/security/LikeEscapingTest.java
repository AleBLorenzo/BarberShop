package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.dto.ServicioDTO;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.service.ServicioService;
import com.barbershop.backend.service.TrabajadorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spec sql-injection-hardening — escaping de comodines LIKE (D2): el
 * {@code %} y el {@code _} que escribe el usuario se tratan como caracteres
 * LITERALES vía {@link LikeEscaper} (aplicado en la capa de servicio justo
 * antes del repositorio, sin tocar las {@code @Query}), sin romper la búsqueda
 * parcial case-insensitive existente y sin errores 500.
 *
 * <p>Escenarios de la spec: {@code 100%} → solo "Ana 100%" (no "Ana 100
 * euros"); {@code a_b} → solo "Luis a_b" (no "Luis aXb"); {@code marí} sin
 * comodines → "María López" sigue devolviéndose. Fixtures únicos por método
 * (H2 testdb compartido): teléfonos con {@code UUID} y nombres exactos de la
 * spec (no colisionan con el resto de la suite).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LikeEscapingTest {

    private static final String PASSWORD = "LikeEscapeTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private TrabajadorService trabajadorService;

    @Autowired
    private ServicioService servicioService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void fixtures() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "like-admin-" + sufijo + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Like Escape Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.ADMIN)
                .build());
        adminToken = login(adminEmail);

        // Clientes de los escenarios de la spec (teléfonos únicos por método)
        crearCliente("Ana 100%");
        crearCliente("Ana 100 euros");
        crearCliente("Luis a_b");
        crearCliente("Luis aXb");
        crearCliente("María López");
    }

    // ===================== Escenarios de la spec =====================

    @Test
    void busqueda100PorCientoDevuelveSoloLaCoincidenciaLiteral() throws Exception {
        List<String> nombres = buscarClientes("100%");
        assertThat(nombres)
                .describedAs("El % del usuario no actúa como comodín: «Ana 100%» se encuentra literalmente")
                .contains("Ana 100%");
        assertThat(nombres)
                .describedAs("«Ana 100 euros» NO debe aparecer en una búsqueda de «100%»")
                .doesNotContain("Ana 100 euros");
    }

    @Test
    void busquedaGuionBajoDevuelveSoloLaCoincidenciaLiteral() throws Exception {
        List<String> nombres = buscarClientes("a_b");
        assertThat(nombres)
                .describedAs("El _ del usuario no sustituye a cualquier carácter: «Luis a_b» se encuentra")
                .contains("Luis a_b");
        assertThat(nombres)
                .describedAs("«Luis aXb» NO debe aparecer en una búsqueda de «a_b»")
                .doesNotContain("Luis aXb");
    }

    @Test
    void busquedaParcialSinComodinesSigueFuncionando() throws Exception {
        List<String> nombres = buscarClientes("marí");
        assertThat(nombres)
                .describedAs("El escaping no rompe la búsqueda parcial case-insensitive")
                .contains("María López");
    }

    @Test
    void helperNeutralizaBackslashPorcentajeYGuionBajo() {
        assertThat(LikeEscaper.escapar("100%")).isEqualTo("100\\%");
        assertThat(LikeEscaper.escapar("a_b")).isEqualTo("a\\_b");
        assertThat(LikeEscaper.escapar("a\\b")).isEqualTo("a\\\\b");
        // La barra se escapa PRIMERO: los comodines quedan con UNA barra propia
        assertThat(LikeEscaper.escapar("\\%")).isEqualTo("\\\\\\%");
        // contener() es el patrón listo para CONCAT('%', :patron, '%')
        assertThat(LikeEscaper.contener("100%")).isEqualTo(LikeEscaper.escapar("100%"));
    }

    @Test
    void losTresServicesEscapanLiteralmente() {
        // Trabajadores y servicios no tienen endpoint de búsqueda hoy: se cubre
        // su ruta LIKE (TrabajadorService/ServicioService.searchActivosByNombre)
        // al nivel de servicio, con los mismos escenarios literales.
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        trabajadorRepository.save(Trabajador.builder()
                .nombre("EscTrab a_b " + sufijo)
                .email("esc-trab-b-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true).rol(Rol.WORKER).build());
        trabajadorRepository.save(Trabajador.builder()
                .nombre("EscTrab aXb " + sufijo)
                .email("esc-trab-x-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true).rol(Rol.WORKER).build());
        servicioRepository.save(Servicio.builder()
                .nombre("EscServ 100% " + sufijo)
                .precio(new BigDecimal("10.00")).duracionMinutos(30).activo(true).build());
        servicioRepository.save(Servicio.builder()
                .nombre("EscServ 100 euros " + sufijo)
                .precio(new BigDecimal("10.00")).duracionMinutos(30).activo(true).build());

        List<String> nombresTrabajadores = trabajadorService.searchActivosByNombre("a_b").stream()
                .map(TrabajadorDTO::getNombre).toList();
        assertThat(nombresTrabajadores)
                .anyMatch(n -> n.startsWith("EscTrab a_b"))
                .noneMatch(n -> n.startsWith("EscTrab aXb"));

        List<String> nombresServicios = servicioService.searchActivosByNombre("100%").stream()
                .map(ServicioDTO::getNombre).toList();
        assertThat(nombresServicios)
                .anyMatch(n -> n.startsWith("EscServ 100%"))
                .noneMatch(n -> n.startsWith("EscServ 100 euros"));
    }

    // ===================== helpers =====================

    /** GET /api/admin/clientes?search=… autenticado como ADMIN → 200 (nunca 500). */
    private List<String> buscarClientes(String search) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/admin/clientes")
                        .param("search", search)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper
                .readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        List<String> nombres = new java.util.ArrayList<>();
        for (JsonNode nodo : data) {
            nombres.add(nodo.path("nombre").asText());
        }
        return nombres;
    }

    private void crearCliente(String nombre) {
        clienteRepository.save(Cliente.builder()
                .nombre(nombre)
                .telefono(telefonoUnico())
                .build());
    }

    private String login(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (no lo recorta la normalización). */
    private static String telefonoUnico() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}

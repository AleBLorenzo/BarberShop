package com.barbershop.backend.service;

import com.barbershop.backend.config.HorarioDefaults;
import com.barbershop.backend.dto.BloqueoDTO;
import com.barbershop.backend.dto.DiaAgendaDTO;
import com.barbershop.backend.dto.HorarioDiaDTO;
import com.barbershop.backend.dto.HorarioGuardadoDTO;
import com.barbershop.backend.dto.ReservaConflictoDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.BloqueoHorario;
import com.barbershop.backend.model.CierreDia;
import com.barbershop.backend.model.HorarioBarberia;
import com.barbershop.backend.model.HorarioTrabajador;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.BloqueoHorarioRepository;
import com.barbershop.backend.repository.CierreDiaRepository;
import com.barbershop.backend.repository.HorarioBarberiaRepository;
import com.barbershop.backend.repository.HorarioTrabajadorRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuente única de la jornada efectiva de un trabajador en una fecha (D2):
 * horario general (o el propio del trabajador, intersectado con el general,
 * D1), días cerrados de la semana, cierres puntuales (D4), trabajador
 * inactivo y bloqueos (D5). La usan la disponibilidad pública, la validación
 * de reservas/reprogramaciones, la agenda del trabajador y los conflictos.
 *
 * <p>También gestiona la edición del horario semanal (solo ADMIN): los PUT
 * adquieren el lock de D3 sobre los trabajadores afectados y nunca cancelan
 * citas, solo las devuelven como conflictos.</p>
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    /** Rango máximo de {@link #diasCerrados} (calendario público, D11). */
    public static final int MAX_DIAS_RANGO = 62;

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<ReservaEstado> ACTIVAS = List.of(ReservaEstado.PENDIENTE, ReservaEstado.CONFIRMADA);

    private final HorarioBarberiaRepository horarioBarberiaRepository;
    private final HorarioTrabajadorRepository horarioTrabajadorRepository;
    private final CierreDiaRepository cierreDiaRepository;
    private final BloqueoHorarioRepository bloqueoHorarioRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final ReservaRepository reservaRepository;
    private final HorarioDefaults horarioDefaults;

    @Value("${app.booking.max-advance-days:60}")
    private int maxAdvanceDays;

    // ========== Tipos del cálculo ==========

    /** Por qué un trabajador no tiene jornada en una fecha. */
    public enum CausaSinJornada {
        TRABAJADOR_INACTIVO,
        /** Cierre general puntual de la barbería (festivo). */
        CIERRE_GENERAL,
        /** Día de la semana cerrado en el horario general. */
        BARBERIA_CERRADA,
        /** Cierre puntual del día del trabajador. */
        CIERRE_TRABAJADOR,
        /** Día libre semanal del trabajador (o su horario no cabe en el general). */
        DIA_LIBRE;

        /** ¿Es la barbería entera la que cierra (y no solo el trabajador)? */
        public boolean esDeLaBarberia() {
            return this == CIERRE_GENERAL || this == BARBERIA_CERRADA;
        }
    }

    /** Franja bloqueada dentro de una jornada. */
    public record Franja(Long id, LocalTime inicio, LocalTime fin, String motivo) {
    }

    /** Jornada efectiva: tramo trabajable y bloqueos de esa fecha (ocupados). */
    public record Jornada(LocalTime inicio, LocalTime fin, List<Franja> bloqueos) {

        /** ¿[inicio, fin) cabe completo dentro de la jornada? */
        public boolean contiene(LocalTime desde, LocalTime hasta) {
            return !desde.isBefore(inicio) && !hasta.isAfter(fin);
        }

        /** Bloqueos que se solapan con [desde, hasta). */
        public List<Franja> bloqueosSolapados(LocalTime desde, LocalTime hasta) {
            return bloqueos.stream()
                    .filter(b -> b.inicio().isBefore(hasta) && b.fin().isAfter(desde))
                    .toList();
        }
    }

    /**
     * Agenda de un día: {@code jornada} es {@code null} si no trabaja, con la
     * causa y, para los cierres puntuales, su motivo.
     */
    public record DiaAgenda(LocalDate fecha, Jornada jornada, CausaSinJornada causa, String motivoCierre) {
        public boolean trabaja() {
            return jornada != null;
        }
    }

    // ========== Jornada efectiva (D2) ==========

    @Transactional(readOnly = true)
    public Optional<Jornada> jornadaEfectiva(Trabajador trabajador, LocalDate fecha) {
        return Optional.ofNullable(dia(trabajador, fecha).jornada());
    }

    @Transactional(readOnly = true)
    public DiaAgenda dia(Trabajador trabajador, LocalDate fecha) {
        return agenda(trabajador, fecha, fecha).get(fecha);
    }

    /**
     * Agenda de un trabajador en [desde, hasta] (ambos incluidos). Carga
     * horarios, cierres y bloqueos del rango de una vez (sin N+1).
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, DiaAgenda> agenda(Trabajador trabajador, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta, null);
        Map<DayOfWeek, HorarioBarberia> general = horarioGeneral();
        Map<DayOfWeek, HorarioTrabajador> propios = horarioTrabajadorRepository
                .findByTrabajador_Id(trabajador.getId()).stream()
                .collect(Collectors.toMap(HorarioTrabajador::getDiaSemana, h -> h, (a, b) -> a));
        List<CierreDia> cierres = cierreDiaRepository.findAplicablesEnRango(desde, hasta, trabajador.getId());
        Map<LocalDate, List<BloqueoHorario>> bloqueos = bloqueoHorarioRepository
                .findByTrabajador_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(trabajador.getId(), desde, hasta)
                .stream()
                .collect(Collectors.groupingBy(BloqueoHorario::getFecha));

        Map<LocalDate, DiaAgenda> agenda = new LinkedHashMap<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            agenda.put(fecha, calcularDia(trabajador, fecha, general, propios, cierres,
                    bloqueos.getOrDefault(fecha, List.of())));
        }
        return agenda;
    }

    private DiaAgenda calcularDia(Trabajador trabajador, LocalDate fecha,
                                  Map<DayOfWeek, HorarioBarberia> general,
                                  Map<DayOfWeek, HorarioTrabajador> propios,
                                  List<CierreDia> cierres,
                                  List<BloqueoHorario> bloqueos) {
        if (!Boolean.TRUE.equals(trabajador.getActivo())) {
            return sinJornada(fecha, CausaSinJornada.TRABAJADOR_INACTIVO, null);
        }
        // trabajador == null no inicializa el proxy LAZY: la FK nula da null directamente
        Optional<CierreDia> cierreGeneral = cierres.stream()
                .filter(c -> c.getFecha().equals(fecha) && c.getTrabajador() == null)
                .findFirst();
        if (cierreGeneral.isPresent()) {
            return sinJornada(fecha, CausaSinJornada.CIERRE_GENERAL, cierreGeneral.get().getMotivo());
        }
        HorarioBarberia dia = general.get(fecha.getDayOfWeek());
        if (!Boolean.TRUE.equals(dia.getAbierto())) {
            return sinJornada(fecha, CausaSinJornada.BARBERIA_CERRADA, null);
        }
        Optional<CierreDia> cierrePropio = cierres.stream()
                .filter(c -> c.getFecha().equals(fecha) && c.getTrabajador() != null)
                .findFirst();
        if (cierrePropio.isPresent()) {
            return sinJornada(fecha, CausaSinJornada.CIERRE_TRABAJADOR, cierrePropio.get().getMotivo());
        }

        LocalTime inicio = dia.getHoraApertura();
        LocalTime fin = dia.getHoraCierre();
        HorarioTrabajador propio = propios.get(fecha.getDayOfWeek());
        if (propio != null) {
            if (!Boolean.TRUE.equals(propio.getTrabaja())) {
                return sinJornada(fecha, CausaSinJornada.DIA_LIBRE, null);
            }
            // Intersección con el general (D1): si el general se redujo y dejó
            // el override fuera, se recorta en vez de salirse de la envolvente.
            inicio = max(inicio, propio.getHoraInicio());
            fin = min(fin, propio.getHoraFin());
        }
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            return sinJornada(fecha, CausaSinJornada.DIA_LIBRE, null);
        }
        List<Franja> franjas = bloqueos.stream()
                .map(b -> new Franja(b.getId(), b.getHoraInicio(), b.getHoraFin(), b.getMotivo()))
                .toList();
        return new DiaAgenda(fecha, new Jornada(inicio, fin, franjas), null, null);
    }

    private static DiaAgenda sinJornada(LocalDate fecha, CausaSinJornada causa, String motivo) {
        return new DiaAgenda(fecha, null, causa, motivo);
    }

    /**
     * Agenda efectiva del trabajador para {@code GET /api/worker/agenda}
     * (D11): un {@link DiaAgendaDTO} por fecha de [desde, hasta], con su
     * jornada, cierres que le afectan (general o propio, con motivo) y sus
     * bloqueos. Rango máximo {@value #MAX_DIAS_RANGO} días.
     */
    @Transactional(readOnly = true)
    public List<DiaAgendaDTO> agendaTrabajador(Trabajador trabajador, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta, MAX_DIAS_RANGO);
        return agenda(trabajador, desde, hasta).values().stream()
                .map(dia -> toDTO(trabajador, dia))
                .toList();
    }

    private static DiaAgendaDTO toDTO(Trabajador trabajador, DiaAgenda dia) {
        if (!dia.trabaja()) {
            return DiaAgendaDTO.builder()
                    .fecha(dia.fecha())
                    .trabaja(false)
                    .causa(dia.causa().name())
                    .cerradoBarberia(dia.causa().esDeLaBarberia())
                    .motivoCierre(dia.motivoCierre())
                    .descripcion(describirSinJornada(dia))
                    .bloqueos(List.of())
                    .build();
        }
        Jornada j = dia.jornada();
        return DiaAgendaDTO.builder()
                .fecha(dia.fecha())
                .trabaja(true)
                .inicio(j.inicio())
                .fin(j.fin())
                .cerradoBarberia(false)
                .bloqueos(j.bloqueos().stream()
                        .map(b -> BloqueoDTO.builder()
                                .id(b.id())
                                .trabajadorId(trabajador.getId())
                                .trabajadorNombre(trabajador.getNombre())
                                .fecha(dia.fecha())
                                .horaInicio(b.inicio())
                                .horaFin(b.fin())
                                .motivo(b.motivo())
                                .build())
                        .toList())
                .build();
    }

    /**
     * Fechas de [desde, hasta] sin jornada (D11, calendario público): para el
     * trabajador indicado o, sin él, aquellas en las que ningún trabajador
     * activo y reservable trabaja. Rango máximo {@value #MAX_DIAS_RANGO} días.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> diasCerrados(LocalDate desde, LocalDate hasta, Long trabajadorId) {
        validarRango(desde, hasta, MAX_DIAS_RANGO);
        List<Trabajador> trabajadores;
        if (trabajadorId != null) {
            trabajadores = List.of(trabajadorRepository.findById(trabajadorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado")));
        } else {
            trabajadores = trabajadorRepository.findByActivoTrueAndReservableTrue();
        }
        Set<LocalDate> conJornada = new HashSet<>();
        for (Trabajador t : trabajadores) {
            agenda(t, desde, hasta).values().stream()
                    .filter(DiaAgenda::trabaja)
                    .forEach(d -> conJornada.add(d.fecha()));
        }
        List<LocalDate> cerrados = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (!conJornada.contains(fecha)) {
                cerrados.add(fecha);
            }
        }
        return cerrados;
    }

    // ========== Horario general (D1, D9) ==========

    /** Horario general por día; un día sin fila usa el valor de siembra (red de seguridad, D9). */
    private Map<DayOfWeek, HorarioBarberia> horarioGeneral() {
        Map<DayOfWeek, HorarioBarberia> mapa = new EnumMap<>(DayOfWeek.class);
        for (HorarioBarberia h : horarioBarberiaRepository.findAll()) {
            mapa.put(h.getDiaSemana(), h);
        }
        for (DayOfWeek dia : DayOfWeek.values()) {
            mapa.computeIfAbsent(dia, horarioDefaults::porDefecto);
        }
        return mapa;
    }

    /** Los 7 días del horario general, de lunes a domingo. */
    @Transactional(readOnly = true)
    public List<HorarioDiaDTO> getHorarioBarberia() {
        Map<DayOfWeek, HorarioBarberia> general = horarioGeneral();
        return Arrays.stream(DayOfWeek.values())
                .map(d -> toDTO(general.get(d)))
                .toList();
    }

    /**
     * Reemplaza el horario general (los 7 días). Valida todo antes de escribir
     * (400 sin cambios si algo falla), adquiere el lock de todos los
     * trabajadores activos (D3) y devuelve las citas que quedan fuera sin
     * cancelarlas, más avisos de horarios de trabajador que se recortarán.
     */
    @Transactional
    public HorarioGuardadoDTO guardarHorarioBarberia(List<HorarioDiaDTO> dias) {
        validarDias(dias, true);

        trabajadorRepository.findAllForUpdateOrderById();

        Map<DayOfWeek, HorarioBarberia> existentes = new EnumMap<>(DayOfWeek.class);
        for (HorarioBarberia h : horarioBarberiaRepository.findAll()) {
            existentes.put(h.getDiaSemana(), h);
        }
        List<HorarioBarberia> aGuardar = new ArrayList<>();
        for (HorarioDiaDTO dto : dias) {
            HorarioBarberia fila = existentes.getOrDefault(dto.getDiaSemana(),
                    HorarioBarberia.builder().diaSemana(dto.getDiaSemana()).build());
            boolean abierto = Boolean.TRUE.equals(dto.getAbierto());
            fila.setAbierto(abierto);
            fila.setHoraApertura(abierto ? dto.getHoraInicio() : null);
            fila.setHoraCierre(abierto ? dto.getHoraFin() : null);
            aGuardar.add(fila);
        }
        horarioBarberiaRepository.saveAll(aGuardar);
        horarioBarberiaRepository.flush();

        return HorarioGuardadoDTO.builder()
                .dias(getHorarioBarberia())
                .conflictos(conflictos(LocalDate.now(), LocalDate.now().plusDays(maxAdvanceDays)))
                .avisos(avisosOverridesFuera())
                .build();
    }

    /** Horarios propios que ya no caben en el nuevo general: se aplicará la intersección (D1). */
    private List<String> avisosOverridesFuera() {
        Map<DayOfWeek, HorarioBarberia> general = horarioGeneral();
        List<Long> ids = trabajadorRepository.findAll().stream().map(Trabajador::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> avisos = new ArrayList<>();
        horarioTrabajadorRepository.findByTrabajadorIds(ids).stream()
                .filter(h -> Boolean.TRUE.equals(h.getTrabaja()))
                .sorted(Comparator.comparing((HorarioTrabajador h) -> h.getTrabajador().getId())
                        .thenComparing(HorarioTrabajador::getDiaSemana))
                .forEach(h -> {
                    HorarioBarberia g = general.get(h.getDiaSemana());
                    String quien = h.getTrabajador().getNombre() + " (" + nombreDia(h.getDiaSemana()) + " "
                            + h.getHoraInicio().format(HORA) + "–" + h.getHoraFin().format(HORA) + ")";
                    if (!Boolean.TRUE.equals(g.getAbierto())) {
                        avisos.add("La barbería cierra el " + nombreDia(h.getDiaSemana())
                                + ": el horario propio de " + quien + " no se aplicará");
                    } else if (h.getHoraInicio().isBefore(g.getHoraApertura())
                            || h.getHoraFin().isAfter(g.getHoraCierre())) {
                        avisos.add("El horario propio de " + quien + " queda fuera del horario de la barbería ("
                                + g.getHoraApertura().format(HORA) + "–" + g.getHoraCierre().format(HORA)
                                + "): se recortará al horario de la barbería");
                    }
                });
        return avisos;
    }

    // ========== Horario por trabajador (D1) ==========

    /** Días con horario propio del trabajador (los ausentes heredan el general). */
    @Transactional(readOnly = true)
    public List<HorarioDiaDTO> getHorarioTrabajador(Long trabajadorId) {
        if (!trabajadorRepository.existsById(trabajadorId)) {
            throw new ResourceNotFoundException("Trabajador no encontrado con ID: " + trabajadorId);
        }
        return horarioTrabajadorRepository.findByTrabajador_Id(trabajadorId).stream()
                .sorted(Comparator.comparing(HorarioTrabajador::getDiaSemana))
                .map(AgendaService::toDTO)
                .toList();
    }

    /**
     * Reemplaza el horario propio del trabajador: los días enviados quedan
     * como override y los ausentes vuelven a heredar el general. Un día con
     * horas debe estar contenido en el general (400 si no). Lock del
     * trabajador (D3); devuelve sus citas en conflicto sin cancelarlas.
     */
    @Transactional
    public HorarioGuardadoDTO guardarHorarioTrabajador(Long trabajadorId, List<HorarioDiaDTO> dias) {
        List<HorarioDiaDTO> entrada = dias != null ? dias : List.of();
        validarDias(entrada, false);

        Trabajador trabajador = trabajadorRepository.findByIdForUpdate(trabajadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado con ID: " + trabajadorId));

        Map<DayOfWeek, HorarioBarberia> general = horarioGeneral();
        for (HorarioDiaDTO dto : entrada) {
            if (!Boolean.TRUE.equals(dto.getAbierto())) {
                continue; // día libre: siempre válido
            }
            HorarioBarberia g = general.get(dto.getDiaSemana());
            if (!Boolean.TRUE.equals(g.getAbierto())) {
                throw new IllegalArgumentException("La barbería está cerrada el " + nombreDia(dto.getDiaSemana())
                        + ": el trabajador no puede trabajar ese día");
            }
            if (dto.getHoraInicio().isBefore(g.getHoraApertura()) || dto.getHoraFin().isAfter(g.getHoraCierre())) {
                throw new IllegalArgumentException("El horario del trabajador debe estar dentro del horario de la barbería ("
                        + nombreDia(dto.getDiaSemana()) + " " + g.getHoraApertura().format(HORA) + "–"
                        + g.getHoraCierre().format(HORA) + ")");
            }
        }

        // Actualización en sitio por día (no borrar+insertar: Hibernate ejecuta
        // los INSERT antes que los DELETE y chocaría con la unique (trabajador, día)).
        Map<DayOfWeek, HorarioTrabajador> existentes = new EnumMap<>(DayOfWeek.class);
        for (HorarioTrabajador h : horarioTrabajadorRepository.findByTrabajador_Id(trabajadorId)) {
            existentes.put(h.getDiaSemana(), h);
        }
        Set<DayOfWeek> enviados = EnumSet.noneOf(DayOfWeek.class);
        List<HorarioTrabajador> aGuardar = new ArrayList<>();
        for (HorarioDiaDTO dto : entrada) {
            enviados.add(dto.getDiaSemana());
            HorarioTrabajador fila = existentes.getOrDefault(dto.getDiaSemana(),
                    HorarioTrabajador.builder().trabajador(trabajador).diaSemana(dto.getDiaSemana()).build());
            boolean trabaja = Boolean.TRUE.equals(dto.getAbierto());
            fila.setTrabaja(trabaja);
            fila.setHoraInicio(trabaja ? dto.getHoraInicio() : null);
            fila.setHoraFin(trabaja ? dto.getHoraFin() : null);
            aGuardar.add(fila);
        }
        List<HorarioTrabajador> aBorrar = existentes.values().stream()
                .filter(h -> !enviados.contains(h.getDiaSemana()))
                .toList();
        horarioTrabajadorRepository.deleteAll(aBorrar);
        horarioTrabajadorRepository.saveAll(aGuardar);
        horarioTrabajadorRepository.flush();

        List<ReservaConflictoDTO> conflictos = conflictos(LocalDate.now(), LocalDate.now().plusDays(maxAdvanceDays))
                .stream()
                .filter(c -> c.getTrabajadorId().equals(trabajadorId))
                .toList();
        return HorarioGuardadoDTO.builder()
                .dias(getHorarioTrabajador(trabajadorId))
                .conflictos(conflictos)
                .avisos(List.of())
                .build();
    }

    // ========== Conflictos ==========

    /**
     * Citas activas desde ahora en [desde, hasta] que no encajan en la agenda
     * efectiva de su trabajador: día cerrado, fuera de jornada o sobre un
     * bloqueo. No modifica nada (el admin decide si las cancela).
     */
    @Transactional(readOnly = true)
    public List<ReservaConflictoDTO> conflictos(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta, null);
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        LocalDate inicio = desde.isBefore(hoy) ? hoy : desde;
        if (inicio.isAfter(hasta)) {
            return List.of();
        }
        List<Reserva> activas = reservaRepository.findByFechaBetweenOrderByFechaAscHoraInicioAsc(inicio, hasta).stream()
                .filter(r -> ACTIVAS.contains(r.getEstado()))
                .filter(r -> !r.getFecha().equals(hoy) || r.getHoraInicio().isAfter(ahora))
                .toList();

        Map<Long, List<Reserva>> porTrabajador = activas.stream()
                .collect(Collectors.groupingBy(r -> r.getTrabajador().getId(), LinkedHashMap::new, Collectors.toList()));
        List<ReservaConflictoDTO> resultado = new ArrayList<>();
        for (List<Reserva> reservas : porTrabajador.values()) {
            Trabajador t = reservas.get(0).getTrabajador();
            Map<LocalDate, DiaAgenda> agenda = agenda(t, inicio, hasta);
            for (Reserva r : reservas) {
                DiaAgenda dia = agenda.get(r.getFecha());
                ReservaConflictoDTO conflicto = clasificar(r, dia);
                if (conflicto != null) {
                    resultado.add(conflicto);
                }
            }
        }
        resultado.sort(Comparator.comparing(ReservaConflictoDTO::getFecha)
                .thenComparing(ReservaConflictoDTO::getHoraInicio)
                .thenComparing(ReservaConflictoDTO::getReservaId));
        return resultado;
    }

    private ReservaConflictoDTO clasificar(Reserva r, DiaAgenda dia) {
        String tipo;
        String motivo;
        if (!dia.trabaja()) {
            tipo = "DIA_CERRADO";
            motivo = describirSinJornada(dia);
        } else if (!dia.jornada().contiene(r.getHoraInicio(), r.getHoraFin())) {
            tipo = "FUERA_DE_JORNADA";
            motivo = "Fuera de la jornada (" + dia.jornada().inicio().format(HORA) + "–"
                    + dia.jornada().fin().format(HORA) + ")";
        } else if (!dia.jornada().bloqueosSolapados(r.getHoraInicio(), r.getHoraFin()).isEmpty()) {
            tipo = "SOBRE_BLOQUEO";
            Franja b = dia.jornada().bloqueosSolapados(r.getHoraInicio(), r.getHoraFin()).get(0);
            motivo = "Sobre un bloqueo (" + b.inicio().format(HORA) + "–" + b.fin().format(HORA)
                    + (b.motivo() != null && !b.motivo().isBlank() ? ": " + b.motivo() : "") + ")";
        } else {
            return null;
        }
        String email = r.getCliente().getEmail();
        return ReservaConflictoDTO.builder()
                .reservaId(r.getId())
                .fecha(r.getFecha())
                .horaInicio(r.getHoraInicio())
                .horaFin(r.getHoraFin())
                .estado(r.getEstado().name())
                .clienteNombre(r.getCliente().getNombre())
                .clienteTelefono(r.getCliente().getTelefono())
                .clienteTieneEmail(email != null && !email.isBlank())
                .trabajadorId(r.getTrabajador().getId())
                .trabajadorNombre(r.getTrabajador().getNombre())
                .servicioNombre(r.getServicio().getNombre())
                .tipo(tipo)
                .motivo(motivo)
                .build();
    }

    /** Texto legible de por qué no hay jornada (incluye el motivo del cierre). */
    public static String describirSinJornada(DiaAgenda dia) {
        String base = switch (dia.causa()) {
            case TRABAJADOR_INACTIVO -> "El trabajador no está activo";
            case CIERRE_GENERAL -> "La barbería está cerrada ese día";
            case BARBERIA_CERRADA -> "La barbería está cerrada el " + nombreDia(dia.fecha().getDayOfWeek());
            case CIERRE_TRABAJADOR, DIA_LIBRE -> "El trabajador no trabaja ese día";
        };
        String motivo = dia.motivoCierre();
        return motivo != null && !motivo.isBlank() ? base + " (" + motivo + ")" : base;
    }

    // ========== Validaciones / utilidades ==========

    /**
     * Valida una lista de días: sin repetidos, con horas y cierre posterior a
     * la apertura en los abiertos; {@code exigirSemanaCompleta} = los 7 días.
     */
    private static void validarDias(List<HorarioDiaDTO> dias, boolean exigirSemanaCompleta) {
        if (dias == null) {
            throw new IllegalArgumentException("El horario es obligatorio");
        }
        Set<DayOfWeek> vistos = EnumSet.noneOf(DayOfWeek.class);
        for (HorarioDiaDTO d : dias) {
            if (d == null || d.getDiaSemana() == null) {
                throw new IllegalArgumentException("Cada día del horario debe indicar el día de la semana");
            }
            if (!vistos.add(d.getDiaSemana())) {
                throw new IllegalArgumentException("El " + nombreDia(d.getDiaSemana()) + " aparece repetido en el horario");
            }
            if (d.getAbierto() == null) {
                throw new IllegalArgumentException("Indica si el " + nombreDia(d.getDiaSemana()) + " está abierto");
            }
            if (d.getAbierto()) {
                if (d.getHoraInicio() == null || d.getHoraFin() == null) {
                    throw new IllegalArgumentException("El " + nombreDia(d.getDiaSemana())
                            + " está abierto: indica la hora de inicio y de fin");
                }
                if (!d.getHoraFin().isAfter(d.getHoraInicio())) {
                    throw new IllegalArgumentException("El " + nombreDia(d.getDiaSemana())
                            + ": la hora de fin debe ser posterior a la de inicio");
                }
            }
        }
        if (exigirSemanaCompleta && vistos.size() != 7) {
            throw new IllegalArgumentException("El horario de la barbería debe incluir los 7 días de la semana");
        }
    }

    private static void validarRango(LocalDate desde, LocalDate hasta, Integer maxDias) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indica las fechas desde y hasta");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha hasta debe ser igual o posterior a la fecha desde");
        }
        if (maxDias != null && ChronoUnit.DAYS.between(desde, hasta) + 1 > maxDias) {
            throw new IllegalArgumentException("El rango no puede superar " + maxDias + " días");
        }
    }

    private static HorarioDiaDTO toDTO(HorarioBarberia h) {
        return HorarioDiaDTO.builder()
                .diaSemana(h.getDiaSemana())
                .abierto(h.getAbierto())
                .horaInicio(h.getHoraApertura())
                .horaFin(h.getHoraCierre())
                .build();
    }

    private static HorarioDiaDTO toDTO(HorarioTrabajador h) {
        return HorarioDiaDTO.builder()
                .diaSemana(h.getDiaSemana())
                .abierto(h.getTrabaja())
                .horaInicio(h.getHoraInicio())
                .horaFin(h.getHoraFin())
                .build();
    }

    static String nombreDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, ES);
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}

package com.barbershop.backend.security;

/**
 * Escaping de comodines LIKE (cambio harden-sql-injection-anti-bot, D2).
 *
 * <p>Las tres búsquedas {@code LIKE} del backend ({@code ClienteService.searchByNombre},
 * {@code TrabajadorService.searchActivosByNombre} y {@code ServicioService.searchActivosByNombre})
 * ya están parametrizadas — no hay SQL injection —, pero el {@code %}/{@code _} que escribe el
 * usuario se interpretaba como comodín: "100%" devolvía también "Ana 100 euros" y un patrón
 * {@code %...%...%...%} permitía filtrado/coste no intencionado. Este helper neutraliza los
 * comodines para que el patrón se lea SIEMPRE literalmente.</p>
 *
 * <p>Se aplica en la capa de servicio justo antes de llamar al repositorio (decisión D2); las
 * {@code @Query} NO se tocan: siguen usando {@code CONCAT('%', :patron, '%')} y el único cambio
 * declarativo permitido —añadir {@code ESCAPE} si el dialecto lo exige— se decide con el test
 * {@code LikeEscapingTest}.</p>
 *
 * <p>Orden del escaping: la propia barra se escapa PRIMERO, para que los {@code \} que este
 * helper inserta delante de {@code %}/{@code _} no vuelvan a ser escapados a su vez
 * ({@code a\%} no debe convertirse en {@code a\\%}).</p>
 */
public final class LikeEscaper {

    private LikeEscaper() {
    }

    /**
     * Neutraliza los comodines LIKE del valor escrito por el usuario:
     * {@code \} → {@code \\}, {@code %} → {@code \%}, {@code _} → {@code \_}
     * (la barra se escapa primero). {@code null} → {@code null}.
     */
    public static String escapar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * Patrón listo para {@code CONCAT('%', :patron, '%')}: el valor escapado se
     * envuelve en los comodines de "contener" LA QUERY (el usuario no aporta ninguno).
     */
    public static String contener(String valor) {
        return escapar(valor);
    }
}

import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { publicApi, mediaUrl } from '../../services/api';
import { NOMBRE, EMAIL_CONTACTO, TELEFONO, DESCRIPCION, DIRECCION, GOOGLE_MAPS_URL, INSTAGRAM_URL, HORARIO_TEXTO } from '../../config/business';
import PriceWithOffer from '../../components/ui/PriceWithOffer';
import { SkeletonGrid } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/EmptyState';
import Avatar from '../../components/ui/Avatar';
import ContactMap from '../../components/ContactMap';
import { LuStar, LuMapPin, LuPhone, LuMail, LuClock, LuScissors, LuSparkles, LuBrush, LuArrowRight, LuInstagram } from 'react-icons/lu';
import { useSeo } from '../../components/Seo';
import { resumirHorario } from '../../utils/horario';

/** Términos del marquee de servicios bajo el hero (decorativo). */
const MARQUEE_ITEMS = [
  'Cortes', 'Barba con navaja', 'Corte + barba', 'Afeitado clásico',
  'Diseño de barba', 'Fade', 'Perfiles', 'Bigote',
];

/**
 * Revela los elementos [data-reveal] al entrar en pantalla (one-shot):
 * IntersectionObserver con rootMargin -80px, como el <Reveal> del sitio
 * de referencia. `deps` fuerza el re-escaneo cuando entra contenido asíncrono.
 */
function useReveal(deps) {
  useEffect(() => {
    const els = document.querySelectorAll('[data-reveal]:not(.is-visible)');
    if (!('IntersectionObserver' in window)) {
      els.forEach((el) => el.classList.add('is-visible'));
      return undefined;
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0, rootMargin: '0px 0px -80px 0px' },
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deps: lista dinámica de datos asíncronos
  }, deps);
}

/** Delay de stagger por índice (ms), con tope para no alargar la fila. */
// Único estilo en línea que queda en la portada: es dinámico (depende del
// índice de la tarjeta), por eso no puede vivir en App.css.
const revealDelay = (i, step = 140, cap = 5) => ({
  '--reveal-delay': `${Math.min(i, cap) * step}ms`,
});

/** Parallax del hero por ratón: normaliza a -0.5..0.5 en CSS vars. */
const onHeroMove = (e) => {
  const rect = e.currentTarget.getBoundingClientRect();
  e.currentTarget.style.setProperty('--hero-mx', ((e.clientX - rect.left) / rect.width - 0.5).toFixed(3));
  e.currentTarget.style.setProperty('--hero-my', ((e.clientY - rect.top) / rect.height - 0.5).toFixed(3));
};

const onHeroLeave = (e) => {
  e.currentTarget.style.setProperty('--hero-mx', '0');
  e.currentTarget.style.setProperty('--hero-my', '0');
};

/** Estrellas accesibles (1–5) para una puntuación. */
function Estrellas({ puntuacion }) {
  const llenas = Math.round(puntuacion);
  return (
    <span className="stars" aria-label={`${llenas} de 5 estrellas`}>
      {Array.from({ length: 5 }, (_, i) => (
        <LuStar key={i} aria-hidden="true" fill={i < llenas ? 'currentColor' : 'none'} />
      ))}
    </span>
  );
}

/**
 * Public landing page: hero, services, workers, reviews, contact.
 */
export default function Landing() {
  // Título/descripción de la portada (gestor de <head>, D2). Son los mismos
  // valores base de index.html: la portada es el estado «en reposo» de la SPA.
  useSeo({
    title: `${NOMBRE} — Reserva tu cita`,
    description: 'The Tank Barber es una barbería moderna en Tenerife. Cortes de cabello, degradados, arreglos de barba y estilos personalizados con atención cercana.',
  });

  const [servicios, setServicios] = useState([]);
  const [trabajadores, setTrabajadores] = useState([]);
  const [resenas, setResenas] = useState([]);
  const [resultados, setResultados] = useState([]);
  // Horario real (GET /api/public/horario, D12); HORARIO_TEXTO como respaldo
  const [horarioLineas, setHorarioLineas] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = async () => {
    try {
      const [servs, trabs] = await Promise.all([
        publicApi.getServicios(),
        publicApi.getTrabajadores(),
      ]);
      setServicios(servs.filter((s) => s.activo));
      setTrabajadores(trabs.filter((t) => t.activo));
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudieron cargar los datos. ¿Está el servidor en marcha?');
      setLoading(false);
    }
  };

  // Las reseñas no bloquean la portada: si fallan, la sección queda vacía.
  const loadResenas = async () => {
    try {
      const revs = await publicApi.getResenas();
      setResenas(revs);
    } catch {
      setResenas([]);
    }
  };

  // La galería de resultados tampoco bloquea la portada.
  const loadResultados = async () => {
    try {
      const fotos = await publicApi.getResultados();
      setResultados(fotos);
    } catch {
      setResultados([]);
    }
  };

  // El horario tampoco bloquea: si falla o viene vacío se pinta HORARIO_TEXTO.
  const loadHorario = async () => {
    try {
      const dias = await publicApi.getHorario();
      setHorarioLineas(dias.length > 0 ? resumirHorario(dias) : null);
    } catch {
      setHorarioLineas(null);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
    loadResenas();
    loadResultados();
    loadHorario();
  }, []);

  // Escanea los reveal tras cargar datos (las tarjetas llegan después del fetch)
  useReveal([loading, servicios.length, trabajadores.length, resenas.length, resultados.length]);

  const mediaResenas = resenas.length
    ? resenas.reduce((sum, r) => sum + (r.puntuacion || 0), 0) / resenas.length
    : 0;

  return (
    <div className="landing">
      {/* Hero */}
      <section
        className="landing-hero"
        onMouseMove={onHeroMove}
        onMouseLeave={onHeroLeave}
      >
        {/* Decoración de fondos: loops infinitos + parallax (solo visual) */}
        <span className="hero-deco hero-deco--scissors" aria-hidden="true">
          <LuScissors />
        </span>
        <span className="hero-deco hero-deco--sparkles" aria-hidden="true">
          <LuSparkles />
        </span>
        <span className="hero-deco hero-deco--brush" aria-hidden="true">
          <LuBrush />
        </span>
        <span className="hero-deco hero-deco--spark hero-deco--spark1" aria-hidden="true">
          <LuSparkles />
        </span>
        <span className="hero-deco hero-deco--spark hero-deco--spark2" aria-hidden="true">
          <LuSparkles />
        </span>
        <span className="hero-deco hero-deco--spark hero-deco--spark3" aria-hidden="true">
          <LuSparkles />
        </span>
        <span className="hero-deco hero-deco--spark hero-deco--spark4" aria-hidden="true">
          <LuSparkles />
        </span>
        <h1>
          <span className="sr-only">{NOMBRE}</span>
          <img src="/logo-wordmark.webp" alt="" aria-hidden="true" />
        </h1>
        <Link to="/reservar/servicio" className="btn btn-primary btn-lg">
          Reservar Cita
          <LuArrowRight aria-hidden="true" />
        </Link>
      </section>

      {/* Marquee de servicios (decorativo, 3 copias en bucle) */}
      <section className="marquee" aria-hidden="true">
        <div className="marquee-track">
          {[0, 1, 2].map((copia) => (
            <div className="marquee-group" key={copia}>
              {MARQUEE_ITEMS.flatMap((texto, i) => [
                <span key={`${copia}-${i}-t`}>{texto}</span>,
                <LuScissors key={`${copia}-${i}-s`} className="marquee-sep" />,
              ])}
            </div>
          ))}
        </div>
      </section>

      {/* Services */}
      <section aria-labelledby="servicios-title">
        <h2 id="servicios-title" className="section-title" data-reveal>Nuestros servicios</h2>
        {error && <ErrorState message={error} onRetry={load} />}
        {loading ? (
          <SkeletonGrid count={4} />
        ) : (
          <div className="grid-services">
            {servicios.map((serv, idx) => (
              <div key={serv.id} className="card landing-card" data-reveal style={revealDelay(idx)}>
                {serv.foto && (
                  <img
                    src={mediaUrl(serv.foto)}
                    alt={serv.nombre}
                    loading="lazy"
                    className="landing-card__img"
                    onError={(e) => { e.currentTarget.style.display = 'none'; }}
                  />
                )}
                <h3 className="mt-0">{serv.nombre}</h3>
                {serv.descripcion && <p className="muted-text text-sm">{serv.descripcion}</p>}
                <p className="landing-card__price">
                  <PriceWithOffer
                    precio={serv.precio}
                    precioOferta={serv.precioOferta}
                    ofertaActiva={serv.ofertaActiva}
                    descuentoPorcentaje={serv.descuentoPorcentaje}
                    ofertaInicio={serv.ofertaInicio}
                    ofertaFin={serv.ofertaFin}
                  />
                </p>
                <p className="muted-text text-sm m-0">
                  {serv.duracionMinutos} min
                </p>
              </div>
            ))}
            {!loading && servicios.length === 0 && !error && (
              <p className="muted-text">No hay servicios disponibles.</p>
            )}
          </div>
        )}
      </section>

      {/* Workers */}
      <section aria-labelledby="equipo-title">
        <h2 id="equipo-title" className="section-title" data-reveal>Nuestro equipo</h2>
        {loading ? (
          <SkeletonGrid count={3} />
        ) : (
          <div className="grid-workers">
            {trabajadores.map((trab, idx) => (
              <div key={trab.id} className="card landing-card" data-reveal style={revealDelay(idx)}>
                <Avatar src={mediaUrl(trab.foto)} name={trab.nombre} size="xl" />
                <h3 className="landing-card__name">{trab.nombre}</h3>
                <p className="muted-text text-sm">
                  {trab.especialidad || 'Barbero profesional'}
                </p>
              </div>
            ))}
            {!loading && trabajadores.length === 0 && !error && (
              <p className="muted-text">Nuestro equipo se está formando.</p>
            )}
          </div>
        )}
      </section>

      {/* Results gallery */}
      <section aria-labelledby="resultados-title">
        <h2 id="resultados-title" className="section-title" data-reveal>Nuestros resultados</h2>
        {resultados.length === 0 ? (
          <p className="muted-text text-center">
            Pronto verás aquí nuestros trabajos.
          </p>
        ) : (
          <div className="grid-galeria">
            {resultados.map((foto, idx) => (
              <figure key={foto.id} className="galeria-item" data-reveal style={revealDelay(idx, 110, 6)}>
                <img
                  src={mediaUrl(foto.url)}
                  alt={foto.titulo || `Trabajo de ${NOMBRE}`}
                  loading="lazy"
                  onError={(e) => {
                    // Imagen rota: se oculta y queda el contenedor gris de reserva
                    e.currentTarget.style.display = 'none';
                  }}
                />
                {foto.titulo && <figcaption>{foto.titulo}</figcaption>}
              </figure>
            ))}
          </div>
        )}
      </section>

      {/* Reviews */}
      <section aria-labelledby="resenas-title">
        <h2 id="resenas-title" className="section-title" data-reveal>Lo que dicen nuestros clientes</h2>
        {resenas.length > 0 && (
          <div className="reviews-summary" data-reveal>
            <div className="reviews-summary__score">
              {mediaResenas.toFixed(1)} <Estrellas puntuacion={mediaResenas} />
            </div>
            <p className="muted-text text-sm m-0">
              {resenas.length} {resenas.length === 1 ? 'reseña' : 'reseñas'}
            </p>
          </div>
        )}
        {resenas.length === 0 ? (
          <p className="muted-text text-center">
            Aún no hay reseñas. ¡Sé el primero en contarnos tu experiencia!
          </p>
        ) : (
          <div className="grid-workers">
            {resenas.slice(0, 6).map((rev, idx) => (
              <figure key={rev.id} className="card landing-card landing-card--quote" data-reveal style={revealDelay(idx)}>
                <blockquote className="review-quote">
                  «{rev.texto}»
                </blockquote>
                <figcaption className="review-author">
                  <b>{rev.autor}</b> · <Estrellas puntuacion={rev.puntuacion} />
                </figcaption>
              </figure>
            ))}
          </div>
        )}
        <div className="reviews-cta" data-reveal>
          <a
            href={GOOGLE_MAPS_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="btn btn-secondary"
          >
            <LuStar aria-hidden="true" fill="currentColor" />
            Escríbenos en Google Maps
          </a>
        </div>
      </section>

      {/* About */}
      <section aria-labelledby="about-title">
        <h2 id="about-title" className="section-title" data-reveal>Sobre nosotros</h2>
        <div className="landing-about" data-reveal>
          {DESCRIPCION.map((parrafo) => (
            <p key={parrafo.slice(0, 40)}>{parrafo}</p>
          ))}
        </div>
      </section>

      {/* Contact */}
      <section className="landing-contact" data-reveal>
        <h3>Visítanos</h3>
        <p>
          <LuMapPin aria-hidden="true" className="icon-lead" />
          <a href={GOOGLE_MAPS_URL} target="_blank" rel="noopener noreferrer" className="link-inherit">
            {DIRECCION}
          </a>
        </p>
        <p>
          <LuPhone aria-hidden="true" className="icon-lead" />
          <a href={`tel:${TELEFONO}`} className="link-inherit">{TELEFONO}</a>
        </p>
        <p>
          <LuMail aria-hidden="true" className="icon-lead" />
          <a href={`mailto:${EMAIL_CONTACTO}`} className="link-inherit">{EMAIL_CONTACTO}</a>
        </p>
        {INSTAGRAM_URL && (
          <p>
            <LuInstagram aria-hidden="true" className="icon-lead" />
            <a
              href={INSTAGRAM_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="link-inherit"
              aria-label="Instagram de TheTankBarber (@the_tank_barber)"
            >
              @the_tank_barber
            </a>
          </p>
        )}
        <p>
          <LuClock aria-hidden="true" className="icon-lead" />
          {horarioLineas
            ? horarioLineas.map((linea, i) => (
              <span key={linea}>
                {i > 0 && <br />}
                {linea}
              </span>
            ))
            : HORARIO_TEXTO}
        </p>
        {/* Mapa embebido (fachada clic-para-cargar); el enlace externo de la
            dirección se conserva arriba como vía para las indicaciones. */}
        <ContactMap />
        <Link to="/mis-citas" className="btn btn-secondary landing-contact__cta">
          Consultar mis citas
        </Link>
      </section>
    </div>
  );
}

# Base de datos de desarrollo (PostgreSQL) — BarberShop

Desde la migración `migrate-h2-to-postgresql`, **el backend ya no usa H2**: el
perfil `dev` se conecta a un PostgreSQL local con esta configuración por
defecto (sobreescribible con `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, p. ej.
en tu `backend/.env` local):

| Parámetro | Valor por defecto |
|-----------|-------------------|
| URL | `jdbc:postgresql://localhost:5432/barbershop` |
| Usuario | `barbershop` |
| Contraseña | `barbershop` |

Son credenciales **locales y de desarrollo**, no secretos. En producción se
cambian en `/etc/barbershop/barbershop.env` (ver `deploy/README.md`).

El esquema lo crea automáticamente Hibernate (`ddl-auto=update`) en el primer
arranque y el `DataSeeder` siembra la demo solo si la BD está vacía.

## Opción A — Docker Compose (recomendada)

```bash
cd backend
docker compose up -d      # arranca postgres:17-alpine con volumen y healthcheck
docker compose ps         # debe aparecer "healthy"
docker compose down       # para la BD (los datos persisten en el volumen)
```

Si ya tienes un PostgreSQL nativo en el puerto 5432, no hace falta este
compose: el backend usará el que ya esté escuchando (o mapea el compose en
otro puerto cambiando `"5432:5432"` por `"5433:5432"`).

## Opción B — PostgreSQL nativo (sin Docker)

En Debian/Ubuntu:

```bash
sudo apt update
sudo apt install -y postgresql
sudo -u postgres psql <<'SQL'
CREATE ROLE barbershop LOGIN PASSWORD 'barbershop';
CREATE DATABASE barbershop OWNER barbershop;
SQL
```

En otras distribuciones o sistemas, crea un rol y una base con los mismos
nombres. Comprueba la conexión:

```bash
PGPASSWORD=barbershop psql -h localhost -U barbershop -d barbershop -c 'select 1;'
```

## Arranque del backend contra la BD

```bash
cd backend
./mvnw spring-boot:run     # perfil dev por defecto
```

En el primer arranque verás los logs de creación de esquema y el aviso
`[DataSeeder] ...` con los logins de demo.

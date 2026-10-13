# Despliegue en Oracle Cloud Always Free — BarberShop €0 para siempre

Guía paso a paso para publicar BarberShop en una VM de Oracle Cloud con coste
**0 € de forma permanente**, datos persistentes entre reinicios y correos de
Brevo operativos.

**Arquitectura resultante**

```
Internet ──80/443──▶ nginx (VM Oracle)
                       ├── /            → frontend estático (React/Vite) en /var/www/barbershop
                       ├── /api/        → proxy → 127.0.0.1:8080 (Spring Boot)
                       └── /uploads/    → proxy → 127.0.0.1:8080 (fotos)
                                             │
                                     Spring Boot (systemd)
                                             │
                                     PostgreSQL (localhost:5432, BD "barbershop")
```

El frontend se construye con `VITE_API_BASE_URL=/api` ⇒ todo va al **mismo
origen** y no hay peticiones CORS. El puerto 8080 queda escuchando solo en
localhost: en Oracle solo abres 22/80/443.

---

## 1. Por qué es gratis para siempre

- **Oracle Cloud Always Free**: incluye, mientras te mantengas en sus límites,
  hasta **2 VMs AMD micro** (aprox. 1/8 de OCPU y 1 GB de RAM cada una) **o**
  hasta 4 VMs ARM A1 (hasta 4 OCPU y 24 GB en total), más ~46 GB de volumen de
  arranque y varios TB/mes de tráfico saliente.
- **Sin "sleep"**: a diferencia de Heroku/Render/Railway free, la VM está
  encendida 24/7 mientras exista. Nada se apaga de noche.
- La **tarjeta de crédito** es solo verificación de identidad; no se cobra
  mientras no actives recursos fuera del nivel Always Free.
- ⚠️ Vigila la consola **Billing → Usage & Cost** de vez en cuando y responde a
  los correos de Oracle sobre recursos inactivos (si la VM estuviera mucho tiempo
  sin uso, Oracle puede avisar para reclamarla).

**Elegir forma AMD micro** es suficiente para esta app (Java + PostgreSQL + nginx
caben en 1 GB con `-Xmx512m`; PostgreSQL local consume ~100-150 MB). Si más
adelante quieres más potencia y tienes cuota ARM, la forma `VM.Standard.A1.Flex`
(hasta 4 OCPU/24 GB gratis) también es €0.

---

## 2. Crear la cuenta Oracle Cloud

1. Entra en <https://www.oracle.com/cloud/free/> y pulsa **Start for Free**.
2. Rellena país, email y datos. Pide **tarjeta** (verificación de identidad; no
   se realiza cargo) y un teléfono.
3. Verifica email y teléfono. Al terminar llegas a la consola **Oracle Cloud
   Infrastructure (OCI)**.

## 3. Crear la VM (instance)

1. Menú lateral → **Compute → Instances → Create Instance**.
2. **Name**: `barbershop`.
3. **Image**: elige **Ubuntu** (recomendado **24.04**; si solo aparece 22.04,
   instala Temurin 21 en el paso 6 en lugar de openjdk — ver nota).
   - *ARM A1 (opcional)*: si tienes cuota, en **Shape → Change shape** elige
     `VM.Standard.A1.Flex` con 1 OCPU/6 GB y la imagen **Ubuntu aarch64**. Si
     no, deja la **AMD micro "Always Free eligible"**.
4. **Shape**: confirma que aparezca como *Always Free eligible*
   (p. ej. `VM.Standard.E2.1.MPU` / `E4.1.MPU`).
5. **Add SSH keys**: sube tu clave pública (`~/.ssh/id_ed25519.pub`) o genera
   una en la consola y guárdala. Opción alternativa: **Password** (más
   sencillo si nunca has usado SSH).
6. **Boot volume**: déjalo por defecto (~46 GB, incluidos en Always Free).
7. **Create** y espera que pase a *Running*.
8. Anota su **IP pública**.

## 4. Abrir puertos (22, 80, 443)

Oracle tiene **dos** cortafuegos: la Security List de la red y (opcional) el
firewall del SO.

**En OCI:** Networking → Virtual Cloud Networks → (la red de tu instancia) →
**Security Lists** → *Default Security List* → **Add Ingress Rules** y añade
tres reglas con **Source CIDR `0.0.0.0/0`**:

| Protocolo | Puerto destino | Destino |
|-----------|----------------|---------|
| TCP | 22 | SSH |
| TCP | 80 | HTTP |
| TCP | 443 | HTTPS (opcional, para SSL) |

**En la VM (opcional pero recomendado):**

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

## 5. Conectarte e instalar dependencias

```bash
ssh -i ~/.ssh/tu_clave ubuntu@<IP_PUBLICA>
```

```bash
sudo apt update && sudo apt upgrade -y

# Java 21 (la versión del pom.xml) + nginx + git + PostgreSQL
sudo apt install -y openjdk-21-jdk-headless nginx git postgresql

# Node.js 20 LTS (para compilar el frontend; Vite lo necesita)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Comprobaciones
java -version   # debe decir 21
node -v         # debe decir v20.x
nginx -v
psql --version  # debe decir psql (PostgreSQL) 14 o superior
```

> Nota (Ubuntu 22.04 u otras sin openjdk-21): instala **Temurin 21** desde
> <https://adoptium.net/temurin/releases/?version=21> y verifica otra vez
> `java -version`.

### 5bis. Crear la base de datos y el usuario de PostgreSQL

El backend usa PostgreSQL local (migración `migrate-h2-to-postgresql`; ya no
hay fichero H2). Crea una base y un usuario dedicados (cámbialos y anótalos;
luego van al env file):

```bash
sudo -u postgres psql <<'SQL'
CREATE ROLE barbershop LOGIN PASSWORD 'CAMBIA_ESTA_PASSWORD';
CREATE DATABASE barbershop OWNER barbershop;
SQL

# Verificación (pedirá la contraseña que acabas de poner):
PGPASSWORD='CAMBIA_ESTA_PASSWORD' psql -h localhost -U barbershop -d barbershop -c 'select 1;'
```

Asegúrate de que el servicio queda activo y arranca con la VM:

```bash
sudo systemctl enable --now postgresql
sudo systemctl status postgresql --no-pager
```

## 6. Clonar el repositorio

```bash
cd /opt
sudo git clone https://github.com/AleBLorenzo/BarberShop.git
sudo mv /opt/BarberShop /opt/barbershop
sudo chown -R ubuntu:ubuntu /opt/barbershop
```

Si el repositorio es **privado**, clona con un Personal Access Token (GitHub →
Settings → Developer settings → Tokens → alcance `repo`) o configura una
*deploy key* SSH:

```bash
git clone https://<TU_TOKEN>@github.com/AleBLorenzo/BarberShop.git
```

> ⚠️ El token queda en `/opt/barbershop/.git/config`: no lo compartas y no lo
> subas nunca a git (ese fichero **no** está en el repo; solo está en la VM).

## 7. Compilar backend y frontend

```bash
# Backend (usa el wrapper incluido; mvn NO hace falta instalar)
cd /opt/barbershop/backend
./mvnw -DskipTests package
ls target/backend-0.0.1-SNAPSHOT.jar   # debe existir

# Frontend — OBLIGATORIO con VITE_API_BASE_URL=/api (mismo origen + nginx)
cd /opt/barbershop/frontend
npm ci
VITE_API_BASE_URL=/api npm run build

# Publicar el estático
sudo mkdir -p /var/www/barbershop
sudo cp -R dist/. /var/www/barbershop/
```

## 8. Fichero de entorno del servidor (credenciales)

Copia el ejemplo y rellénalo **en la VM** (aquí van los valores reales; nunca
en git):

```bash
sudo mkdir -p /etc/barbershop
sudo cp /opt/barbershop/backend/.env.example /etc/barbershop/barbershop.env
sudo chmod 600 /etc/barbershop/barbershop.env
sudo chown root:root /etc/barbershop/barbershop.env
sudoedit /etc/barbershop/barbershop.env
```

Rellena como mínimo:

| Variable | Valor |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` (viene en el ejemplo) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/barbershop` |
| `SPRING_DATASOURCE_USERNAME` | el usuario creado en el paso 5bis (p. ej. `barbershop`) |
| `SPRING_DATASOURCE_PASSWORD` | la contraseña de ese usuario (¡no la de ejemplo!) |
| `JWT_SECRET` | `openssl rand -hex 32` (genera uno y pégalo) |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | credenciales del **primer** administrador (solo se usan con la BD vacía) |
| `MAIL_HOST` | `smtp-relay.brevo.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USER` | tu email de acceso en Brevo |
| `MAIL_PASS` | la **SMTP Key** de Brevo (panel Brevo → SMTP & API) |
| `MAIL_FROM` | un remitente **verificado** en Brevo |
| `MAIL_AUTH` / `MAIL_STARTTLS` / `MAIL_ENABLED` | `true` |

Variables opcionales `SPRING_DATASOURCE_*` y el resto están documentadas en el
propio fichero. **Aviso**: si borras `SEED_ADMIN_*` con la BD vacía, la app
arranca pero no habrá ningún usuario con el que entrar (habría que
restaurarlas y reiniciar mientras la BD siga vacía).

## 9. Instalar el servicio (systemd) y nginx

```bash
# Backend
sudo cp /opt/barbershop/deploy/barbershop-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable barbershop-backend

# nginx
sudo cp /opt/barbershop/deploy/nginx-barbershop.conf /etc/nginx/sites-available/barbershop
sudo ln -s /etc/nginx/sites-available/barbershop /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl enable nginx

# Arrancar
sudo systemctl start barbershop-backend
sudo systemctl reload nginx
```

Si tu usuario de la VM no se llama `ubuntu`, ajusta `User=` en
`/etc/systemd/system/barbershop-backend.service` y haz
`sudo systemctl daemon-reload`.

## 10. Primeras comprobaciones

```bash
# 1. El backend está vivo y creó el admin inicial (BD vacía)
journalctl -u barbershop-backend -n 100 --no-pager | grep ProdSeeder
#    Esperado: "[ProdSeeder] Administrador inicial creado: <tu email>"

# 2. API responde por detrás de nginx
curl -s http://127.0.0.1:8080/api/public/servicios | head -c 300
curl -s http://127.0.0.1/api/public/servicios        | head -c 300   # vía nginx

# 3. El frontend se sirve y las rutas SPA funcionan
curl -sI http://127.0.0.1/ | head -3
curl -sI http://127.0.0.1/reservar | head -3          # debe devolver index.html

# 4. Desde tu navegador: http://<IP_PUBLICA>/
```

Desde el navegador:

1. Abre `http://<IP_PUBLICA>/` y entra en **Admin** con `SEED_ADMIN_EMAIL` /
   `SEED_ADMIN_PASSWORD` → deben verse servicios (9 del catálogo) y el panel.
2. Crea una **reserva de prueba** con tu email real → debe llegar el correo de
   confirmación de Brevo (si no: `journalctl -u barbershop-backend -f` — un
   aviso `[MODO LOG]` significa que falta algún `MAIL_*`).
3. Reinicia el backend (`sudo systemctl restart barbershop-backend`) y comprueba
   que **los datos siguen ahí** (viven en PostgreSQL, BD `barbershop`).

## 11. Actualizar el despliegue

```bash
cd /opt/barbershop
git pull

# Si cambió solo backend:
./deploy/deploy.sh

# Si cambió también frontend (rebuild con /api + publicar en /var/www):
./deploy/deploy.sh --build-frontend
```

El script es idempotente: se puede ejecutar tantas veces como haga falta.
Si prefieres hacerlo a mano: `./mvnw -DskipTests package` + `sudo systemctl
restart barbershop-backend` (y `npm ci && VITE_API_BASE_URL=/api npm run build`
+ copiar `dist/` si cambió el frontend).

## 12. Backup de los datos (PostgreSQL con pg_dump)

Los datos viven en la base **PostgreSQL** de la VM (BD `barbershop`). La forma
más sencilla de respaldarla es `pg_dump` con el formato comprimido `-Fc`
(**no hace falta parar el servicio**: PostgreSQL admite volcados en caliente).

### Backup manual

```bash
sudo mkdir -p /var/backups/barbershop
sudo -u postgres pg_dump -Fc barbershop \
    -f /var/backups/barbershop/barbershop-$(date +%Y%m%d-%H%M%S).dump
sudo chown ubuntu /var/backups/barbershop/*.dump   # si los quieres copiar desde tu usuario
```

### Backup automático diario (cron)

```bash
sudo crontab -e
# 0 4 * * * mkdir -p /var/backups/barbershop && sudo -u postgres pg_dump -Fc barbershop -f /var/backups/barbershop/barbershop-$(date +\%Y\%m\%d).dump
```

> Con `pg_dump` no hay que parar el backend a diferencia del antiguo backup
> del fichero H2, que exigía detener el servicio para copiarlo sin corromper.

### Restaurar un backup

```bash
sudo systemctl stop barbershop-backend
sudo -u postgres dropdb barbershop
sudo -u postgres createdb -O barbershop barbershop
sudo -u postgres pg_restore -d barbershop --no-owner \
    /var/backups/barbershop/barbershop-AAAAMMDD-HHMMSS.dump
sudo systemctl start barbershop-backend
```

Conviene copiar también `/opt/barbershop/backend/uploads/` (fotos subidas).
Las copias locales protegen contra fallos del disco; para protección contra
pérdida total de la VM, súbelas a otro sitio (GitHub con LFS, otro cloud…).

## 13. Opcional: dominio y HTTPS

- **Dominio gratis apuntando a la IP**: usa *nip.io* o *sslip.io*, p. ej.
  `barbershop.12.34.56.78.nip.io` resuelve siempre a `12.34.56.78` (hace falta
  que el puerto 80 esté abierto).
- **Certificado SSL gratuito** con certbot:

  ```bash
  sudo apt install -y certbot python3-certbot-nginx
  sudo certbot --nginx -d barbershop.12.34.56.78.nip.io
  ```

  Certbot renueva solo (timer de systemd) y reescribe la config de nginx.
- Con dominio propio apuntando por DNS `A` a la IP, el proceso es el mismo.

## 14. Seguridad — resumen

- `backend/.env` y `/etc/barbershop/barbershop.env` **nunca** se commitean
  (`.env` está en `.gitignore`; el del servidor además `chmod 600`).
- Puerto **8080 no se abre** en Oracle: solo llega tráfico de nginx en
  localhost. PostgreSQL solo escucha en `localhost` de la VM (no exponer el
  5432 en el cortafuegos de Oracle).
- `JWT_SECRET` fuerte y distinto del de desarrollo; se genera con
  `openssl rand -hex 32`.
- El primer admin sale de `SEED_ADMIN_*` y **solo** se crea con BD vacía; los
  datos de demostración (reservas/clientes ficticios) jamás se siembran en
  producción.
- Mantén el sistema actualizado: `sudo apt update && sudo apt upgrade -y` de
  vez en cuando.

## 15. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| `502 Bad Gateway` | backend caído | `journalctl -u barbershop-backend -n 100 -f` |
| Backend caído con `Connection refused` (PSQL) | PostgreSQL parado o URL/credenciales mal | `sudo systemctl status postgresql`; revisa `SPRING_DATASOURCE_*` en el env file |
| `password authentication failed for user` | credenciales del env file ≠ las del rol | comprueba con `PGPASSWORD=... psql -h localhost -U barbershop -d barbershop` y ajusta el env file |
| Rutas tipo `/reservar` dan 404 en nginx | falta el fallback SPA | revisa `try_files` de `deploy/nginx-barbershop.conf` |
| No llegan correos | faltan `MAIL_*` o remitente sin verificar en Brevo | revisa el env file y el journal (`[MODO LOG]`) |
| Login 401 con credenciales correctas | falta `JWT_SECRET` (la app ni arrancaría) o admin no creado | journal: mira `[ProdSeeder]` y el env file |
| Frontend llama a `localhost:8080` | se construyó sin `VITE_API_BASE_URL=/api` | rebuild con `./deploy/deploy.sh --build-frontend` |
| "Address already in use" al arrancar | otro proceso en 8080 | `sudo ss -ltnp \| grep 8080` |
| La app arrancó pero no hay admin | BD vacía sin `SEED_ADMIN_*` | añade las claves (con BD aún vacía) y reinicia |
| Tras migrar desde H2 no se ven los datos antiguos | el fichero H2 ya no se usa | los datos viven ahora en PostgreSQL; un volcado del H2 antiguo requiere exportarlo a tablas (ver diseño OpenSpec) |

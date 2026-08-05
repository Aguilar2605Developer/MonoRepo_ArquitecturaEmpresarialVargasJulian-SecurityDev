# Proyecto Integrador — Arquitectura Empresarial (PUCE)

**Integrates:** Julian Solorzano & Alejandro Vargas
**Paralelo:** 1462 — Período 2026-01
**Autenticación y autorización:** AWS Cognito (User Pool propio)

Monorepo de microservicios que expone un único punto de entrada (nginx) hacia dos servicios
independientes, cada uno con su propia base de datos PostgreSQL

---

## Índice

1. [Arquitectura general](#arquitectura-general)
2. [Estructura del monorepo](#estructura-del-monorepo)
3. [Microservicio: users-microservice](#microservicio-users-microservice)
4. [Microservicio: securitydev](#microservicio-securitydev)
5. [Reverse proxy (nginx)](#reverse-proxy-nginx)
6. [Autenticación con Cognito](#autenticación-con-cognito)
7. [Cómo levantar el proyecto](#cómo-levantar-el-proyecto)
8. [Pruebas con Postman](#pruebas-con-postman)
9. [Variables de entorno](#variables-de-entorno)

---

## Arquitectura general

```
                              ┌─────────────────────────┐
                              │        AWS Cognito       │
                              │   (User Pool propio)     │
                              │  emite JWT firmados       │
                              └────────────┬─────────────┘
                                           │ Authorization: Bearer <JWT>
                                           ▼
   Cliente (Postman / app)  ──────►  nginx :8888  (único puerto expuesto)
                                           │
                       ┌───────────────────┴───────────────────┐
                       ▼                                       ▼
              /users/  →  users-microservice:8686      /securitydev/  →  securitydev-backend:8080
                       │                                       │
                       ▼                                       ▼
              postgres-users:5432                    postgres-securitydev:5432
              (BD propia, aislada)                    (BD propia, aislada)
```

Principios de diseño respetados:

- **Database-per-service**: cada microservicio tiene su propio contenedor de Postgres, con
  usuario, contraseña, base de datos y volumen independientes. Ninguno accede a la base del otro.
- **Single entry point**: nginx es el único servicio con `ports:` publicado al host
  (`8888:80`). `users-microservice` y `securitydev-backend` usan `expose:`, es decir, solo son
  alcanzables entre contenedores de la red interna de Docker Compose — nunca directamente desde
  el host.
- **Autenticación delegada**: ningún microservicio guarda contraseñas. Ambos actúan como
  *OAuth2 Resource Server*, validando la firma, el emisor (`iss`) y la expiración (`exp`) de los
  JWT contra el mismo `issuer-uri` de Cognito.
- **Arquitectura en capas** en ambos servicios: `controller` → `service` → `repository`, con
  DTOs en la frontera y mappers dedicados.

---

## Estructura del monorepo

```
Proyecto_Integrador/
├── users-microservice/
│   ├── Dockerfile
│   ├── build.gradle.kts
│   ├── .env.docker(.example)
│   └── src/main/kotlin/com/pucetec/users/
│       ├── controllers/
│       ├── services/
│       ├── repositories/
│       ├── entities/
│       ├── dto/
│       ├── mappers/
│       ├── security/
│       ├── exceptions/
│       └── logging/
├── securitydev/
│   ├── Dockerfile
│   ├── build.gradle.kts
│   ├── .env.docker(.example)
│   └── src/main/kotlin/com/pucetec/securitydev/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       ├── dto/
│       ├── mappers/
│       ├── security/
│       ├── exceptions/
│       ├── logging/
│       └── client/           # cliente HTTP hacia users-microservice
├── nginx/
│   ├── nginx.conf
│   └── proxy_headers.conf
├── docker-compose.yml
└── README.md
```

---

## Microservicio: users-microservice

Microservicio base de gestión de perfiles de usuario, vinculados 1:1 a una identidad de
Cognito mediante el claim `sub` del token.

**Paquete base:** `com.pucetec.users`
**Puerto interno:** `8686` (`expose:` únicamente, no accesible directo desde el host)
**Base de datos:** `postgres-users` (Postgres 16, contenedor y volumen propios)

### Endpoints principales

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `POST` | `/api/users/me` | JWT | Crea o actualiza el perfil del usuario autenticado. El `cognitoId` se toma siempre del claim `sub` del token, nunca del body. |
| `GET` | `/api/users/me` | JWT | Devuelve el perfil del usuario autenticado. |
| `PUT` | `/api/users/me` | JWT | Actualiza datos del perfil propio. |

### Capas internas

- **`controllers/UserController`** — expone los endpoints REST, sin lógica de negocio.
- **`services/UserService`** — lógica de dominio: valida datos, resuelve el `cognitoId` desde
  el `SecurityContext`, orquesta creación/actualización.
- **`repositories/UserRepository`** — acceso a datos vía Spring Data JPA.
- **`entities/User`** — entidad persistida (`id`, `cognitoId`, `name`, `email`, `phone`).
- **`security/SecurityConfig`** — configura el *Resource Server* de Spring Security: valida JWT
  contra el `issuer-uri` de Cognito, `SessionCreationPolicy.STATELESS`, CSRF deshabilitado.
- **`security/CurrentUser`** — helper para extraer el `sub` del JWT autenticado.
- **`exceptions/GlobalExceptionHandler`** — traduce excepciones de dominio
  (`UserNotFoundException`, `DuplicateCognitoIdException`, `BlankNameException`) a respuestas
  HTTP con código y mensaje consistentes.
- **`logging/RequestResponseLoggingFilter` + `MdcCleanupFilter`** — dejan rastro de cada
  petición (`event=http.request` / `event=http.response`) con el `sub` del usuario en el MDC,
  siguiendo el estándar de logging de una sola línea.

---

## Microservicio: securitydev

Microservicio de dominio propio (SecurityApp Backend): gestiona **hotspots de seguridad** y
**compartición de ubicación en tiempo real (location sharing)**, además de un panel de
administración que gestiona usuarios directamente contra Cognito.

**Paquete base:** `com.pucetec.securitydev`
**Puerto interno:** `8080` (`expose:` únicamente)
**Base de datos:** `postgres-securitydev` (Postgres 16, contenedor y volumen propios)

### Endpoints principales

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/health` | Público | Health check del servicio. |
| `GET` | `/api/health/cognito` | Público | Verifica la configuración de Cognito. |
| `POST` | `/api/users/register` | Público | Registro de usuario nuevo en Cognito. |
| `POST` | `/api/users/confirm` | Público | Confirmación de registro con código enviado por Cognito. |
| `POST` | `/api/users/resend-code` | Público | Reenvío del código de confirmación. |
| `POST` | `/api/users/sync` | JWT | Sincroniza el usuario autenticado con el registro local. |
| `GET` | `/api/hotspots` | Público | Lista de hotspots activos. |
| `GET` | `/api/hotspots/{id}` | Público | Detalle de un hotspot. |
| `POST` | `/api/hotspots` | JWT | Crea un hotspot, asociado al usuario autenticado. |
| `PUT` | `/api/hotspots/{id}` | JWT, solo dueño | Actualiza un hotspot propio. |
| `PUT` | `/api/hotspots/{id}/deactivate` | JWT, solo dueño | Desactiva un hotspot propio. |
| `DELETE` | `/api/hotspots/{id}` | JWT, solo dueño | Elimina un hotspot propio. |
| `POST` | `/api/location-shares` | JWT | Inicia una compartición de ubicación. |
| `GET` | `/api/location-shares/{id}` | JWT, dueño o destinatario | Consulta una compartición. |
| `PUT` | `/api/location-shares/{id}` | JWT, solo dueño | Actualiza la ubicación compartida. |
| `PUT` | `/api/location-shares/{id}/stop` | JWT, solo dueño | Detiene la compartición. |
| `POST` | `/api/location-shares/{id}/share-email` | JWT, solo dueño | Invita a un destinatario por correo. |
| `GET` | `/api/location-shares/{id}/recipients` | JWT, solo dueño | Lista destinatarios. |
| `DELETE` | `/api/location-shares/{id}/recipients/{email}` | JWT, solo dueño | Revoca acceso de un destinatario. |
| `GET` | `/api/admin/dashboard` | JWT, rol `ADMIN` | Métricas generales. |
| `GET` \| `POST` \| `PUT` \| `DELETE` | `/api/admin/users/**` | JWT, rol `ADMIN` | CRUD completo de usuarios (Cognito + BD local). |
| `POST` | `/api/admin/users/purge-orphans` | JWT, rol `ADMIN` | Elimina usuarios huérfanos entre Cognito y la BD. |
| `POST` | `/api/admin/users/sync-from-cognito` | JWT, rol `ADMIN` | Reimporta usuarios desde Cognito. |
| `GET` \| `POST` \| `PUT` \| `DELETE` | `/api/admin/hotspots/**` | JWT, rol `ADMIN` | Gestión total de hotspots, incluyendo inactivos. |

### Capas internas

- **`controller/`** — `UserController`, `HotSpotController`, `LocationShareController`,
  `AdminController`, `HealthController`.
- **`service/`** — `UserService`, `HotSpotService`, `LocationShareService`, `AdminService`,
  `AuditService`, `EmailService`, `CognitoService` y `CognitoAdminService` (operaciones
  administrativas directas contra el SDK de Cognito, como crear/eliminar usuarios y resetear
  contraseñas).
- **`repository/`** — `HotSpotRepository`, `HotSpotReportRepository`, `LocationShareRepository`,
  `LocationShareRecipientRepository`, `AdminRosterUserRepository`, `AuditLogRepository`.
- **`entity/`** — `HotSpot`, `Hotspotreport`, `LocationShare`, `LocationShareRecipient`,
  `AdminRosterUser`, `AuditLog`.
- **`security/SecurityConfig`** — mismas reglas que `users-microservice`: JWT contra el
  `issuer-uri` de Cognito, rutas públicas explícitas (`permitAll`) y el resto autenticado; las
  reglas de "solo dueño" y "solo ADMIN" se validan explícitamente en el `service`, no solo con
  anotaciones de rol de Spring Security.
- **`client/UsersMicroserviceClient`** — cliente HTTP que llama a `users-microservice`
  propagando el token del usuario, para resolver datos de perfil sin acceder a su base de datos
  directamente (comunicación *service-to-service*, nunca *database-to-database*).
- **`exceptions/GlobalExceptionHandler`** — traduce `HotSpotNotFoundException`,
  `LocationShareNotFoundException` y errores de autorización por dueño a 403/404 consistentes.
- **`logging/`** — mismo estándar de logging de una línea que `users-microservice`.

---

## Reverse proxy (nginx)

Único servicio con puerto publicado al host (`8888:80`). Enruta por prefijo de ruta y **recorta
el prefijo** antes de reenviar al microservicio correspondiente:

```nginx
location /securitydev/ {
    proxy_pass http://securitydev-backend:8080/;
}

location /users/ {
    proxy_pass http://users-microservice:8686/;
}

location /pgadmin/ {
    proxy_pass http://pgadmin:80/pgadmin/;
}
```

Ejemplo de traducción de ruta:

```
http://localhost:8888/users/api/users/me
        ↓ (nginx quita "/users/")
http://users-microservice:8686/api/users/me
```

`proxy_headers.conf` se incluye en cada `location` para propagar cabeceras estándar
(`Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`) — incluyendo el header
`Authorization`, indispensable para que el JWT llegue intacto al microservicio de destino.

---

## Autenticación con Cognito

Ambos microservicios actúan como **OAuth2 Resource Server** y resuelven el mismo
`issuer-uri`, apuntando al mismo User Pool:

```
issuer-uri: https://cognito-idp.us-east-1.amazonaws.com/${COGNITO_USER_POOL_ID}
```

Flujo utilizado para pruebas manuales (Authorization Code, vía Postman):

1. Postman abre la Hosted UI de Cognito (`Auth URL`) usando el App Client `postman-client`.
2. El usuario inicia sesión con sus credenciales de Cognito.
3. Cognito redirige a `https://oauth.pstmn.io/v1/callback` con un `code` de un solo uso.
4. Postman canjea el `code` por un `access_token` + `id_token` contra el `Access Token URL`.
5. El `access_token` se envía como `Authorization: Bearer <JWT>` en cada request protegida.
6. Cada microservicio descarga el JWKS del `issuer-uri` (cacheado) y valida firma, `iss` y
   `exp` — sin llamar a Cognito en cada petición ni guardar contraseñas.

El `sub` del token es el identificador estable del usuario en ambos servicios (columna
`cognitoId`) y también el campo `sub` que se registra en cada línea de log.

---

## Cómo levantar el proyecto

```bash
docker compose up --build --force-recreate
```

Esto construye ambas imágenes, levanta:

- `postgres-users` y `postgres-securitydev` (con healthcheck `pg_isready`)
- `users-microservice` (espera a que su base esté `healthy`)
- `securitydev-backend` (espera a que su base esté `healthy`)
- `pgadmin` (explorador de BD, accesible vía `/pgadmin/`)
- `nginx` (único puerto publicado: `8888`)

Verificación rápida:

```bash
curl http://localhost:8888/users/api/users/me        # 401 sin token (esperado)
curl http://localhost:8888/securitydev/api/health    # 200 (público)
```

---

## Pruebas con Postman

Se incluyen dos colecciones:

- **Users Microservice** — flujos de perfil (`/api/users/me`).
- **securitydev API - SecurityApp Backend** — hotspots, location-shares, admin y casos
  negativos (401 / 403 / 404).

Variables clave por colección (ajustar el prefijo según el microservicio):

| Variable | Valor |
|---|---|
| `base_url` (users) | `http://localhost:8888/users` |
| `base_url` (securitydev) | `http://localhost:8888/securitydev` |
| `access_token` | Token de un usuario autenticado normal |
| `admin_token` | Token de un usuario del grupo Cognito `ADMIN` |

Configuración de Authorization (OAuth 2.0) a nivel de colección:

| Campo | Valor |
|---|---|
| Grant Type | Authorization Code |
| Callback URL | `https://oauth.pstmn.io/v1/callback` |
| Auth URL | `https://<dominio-cognito>/oauth2/authorize` |
| Access Token URL | `https://<dominio-cognito>/oauth2/token` |
| Client ID / Secret | App Client `postman-client` del User Pool |
| Scope | `openid email profile` |

> Postman resuelve variables con prioridad **Environment > Colección > Request**. Si una
> request sigue fallando con `ECONNREFUSED` tras cambiar `base_url`, revisar los tres niveles.

Casos cubiertos por la colección de `securitydev` (carpeta *Casos negativos*):

- `401` — request a ruta protegida sin token.
- `403` — JWT válido pero sin rol `ADMIN` en endpoint de administración.
- `403` — intento de editar un hotspot que pertenece a otro usuario.
- `404` — usuario o hotspot con id inexistente.

---

## Variables de entorno

### `users-microservice/.env.docker`

```env
COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
SERVER_PORT=8686
```

(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` los inyecta `docker-compose.yml` directamente, apuntando
a `postgres-users`.)

### `securitydev/.env.docker`

```env
COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
COGNITO_REGION=us-east-1

AWS_ACCESS_KEY_ID=xxxxxxxxxxxxxxxxxxxx
AWS_SECRET_ACCESS_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

MAIL_USERNAME=tu-cuenta@gmail.com
MAIL_PASSWORD=contraseña-de-aplicacion-de-gmail

FRONTEND_URL=http://localhost:8100
ADMIN_EMAIL=admin@example.com
```

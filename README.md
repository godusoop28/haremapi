# HAREMS API

Backend Spring Boot 4 (Java 21, Jackson 3 / Spring Security 7) para la plataforma de chat con personajes de IA "HAREMS". Sirve a la app Next.js en `harems/`.

## Requisitos

- JDK con soporte para `--release 21` y compatible con Lombok 1.18.x para generación de getters/setters/builders en *annotation processing*.
  - **JDK 23 (el que trae `C:\Program Files\Java\jdk-23`) tiene un problema conocido**: Lombok no genera código (los getters/setters/builders quedan ausentes) aunque la compilación "parece" correcta hasta que aparecen errores de `cannot find symbol`.
  - Se verificó que **JDK 22 (Amazon Corretto 22.0.2)** funciona correctamente con Lombok 1.18.36 y `--release 21`.
  - Si vas a compilar/ejecutar manualmente, exporta `JAVA_HOME` apuntando a un JDK 21/22 compatible, por ejemplo:
    ```bash
    export JAVA_HOME="/c/Users/emili/.jdks/corretto-22.0.2"
    ```
- MySQL 8 corriendo localmente (o accesible vía las variables de entorno de abajo).

## Variables de entorno

| Variable | Descripción | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de MySQL | `jdbc:mysql://localhost:3306/harems_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `SPRING_DATASOURCE_USERNAME` | Usuario MySQL | `root` |
| `SPRING_DATASOURCE_PASSWORD` | Password MySQL | `root` |
| `JWT_SECRET` | Clave secreta para firmar JWT | valor de desarrollo incluido |
| `JWT_EXPIRATION` | Expiración del JWT en ms | `86400000` (24h) |
| `FRONTEND_URL` | URL del frontend | `http://localhost:3000` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (separados por coma) | `http://localhost:3000,https://haremsweb.vercel.app` |
| `ADMIN_BOOTSTRAP_EMAIL` | Email que se registrará automáticamente como ADMIN | `emy.rodriguezc28@gmail.com` |
| `OPENAI_API_KEY` / `OPENROUTER_API_KEY` | Para integración futura de IA real | vacío |
| `COMFYUI_BASE_URL` / `RUNPOD_API_KEY` | Para integración futura de generación de imágenes | vacío |
| `PAYMENT_PROVIDER` / `PAYMENT_SECRET_KEY` / `PAYMENT_WEBHOOK_SECRET` | Para integración futura de pagos | vacío |

## Cómo correr el backend

```bash
cd haremapi/harem
JAVA_HOME="/c/Users/emili/.jdks/corretto-22.0.2" ./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080/api`.

Al iniciar:
- Se crean/actualizan las tablas vía `ddl-auto: update`.
- Se siembran los 12 personajes (si no existen) mediante `CharacterDataSeeder`.
- El primer usuario que se registre con el email `emy.rodriguezc28@gmail.com` recibe el rol `ADMIN`; el resto recibe `USER`.

## Tablas creadas

- `users`
- `profiles`
- `subscriptions`
- `characters`
- `conversations`
- `messages`
- `usage_limits`
- `image_generations`

## Probar con Postman / Thunder Client

1. **Registro**: `POST /api/auth/register`
   ```json
   { "name": "Ana", "email": "ana@example.com", "password": "12345678", "ageVerified": true }
   ```
   Devuelve `{ "token": "...", "user": {...} }`.

2. Usa el `token` como `Authorization: Bearer <token>` en el resto de peticiones.

3. **Personajes**: `GET /api/characters` (público), `GET /api/characters/{slug}`.

4. **Chat**: `POST /api/chat/send`
   ```json
   { "characterSlug": "luna-valmont", "message": "Hola!" }
   ```

5. **Conversaciones**: `GET /api/conversations`, `GET /api/conversations/{id}`.

6. **Simular plan** (mientras no hay pagos reales): `POST /api/subscriptions/simulate`
   ```json
   { "plan": "PREMIUM" }
   ```
   Planes válidos: `FREE`, `TRIAL_3_DAYS`, `PREMIUM`, `VIP`.

7. **Generar imagen** (requiere plan con créditos): `POST /api/images/generate`
   ```json
   { "characterSlug": "aurora-sterling", "type": "portrait" }
   ```

8. **Admin** (requiere usuario con rol `ADMIN`):
   - `GET /api/admin/users`
   - `GET /api/admin/users/{id}`
   - `PUT /api/admin/users/{id}/plan` con `{ "plan": "VIP" }`
   - `GET /api/admin/conversations`
   - `GET /api/admin/image-generations`

## Recomendación de base de datos

MySQL 8 (ya configurado por defecto). PostgreSQL también funcionaría cambiando el driver y la URL JDBC, pero no ha sido probado en este proyecto.
"# haremapi" 

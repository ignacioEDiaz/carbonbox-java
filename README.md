# CarbonBox Java

Sistema de procesamiento asíncrono de archivos CSV con datos de actividad (consumo de electricidad, combustible, viajes…), hecho con **Java 21 y Spring Boot 3**.

Es una reimplementación en Java de [CarbonBox](https://github.com/ignacioEDiaz/CarbonBox), la prueba técnica full stack que resolví con **Node.js + TypeScript** (Fastify, Prisma, SQS, Next.js). Toma la misma consigna y las mismas decisiones de arquitectura (DDD, Clean Architecture, CQRS, colas con reintentos y DLQ) y las lleva al ecosistema Spring.

> *English:* Java 21 / Spring Boot 3 rewrite of my CarbonBox take-home (originally Node.js + TypeScript). Users upload activity CSVs; a queue worker processes them asynchronously with retries, a dead-letter queue and idempotent processing; a small UI polls for status and shows results.

## Qué hace

1. Subís un CSV (`category,amount,unit,date`) desde la web o la API.
2. El backend valida el encabezado, guarda el archivo, crea el upload en `PENDING`, lo encola y responde enseguida con el id (`202 Accepted`).
3. Un worker toma el mensaje, procesa el archivo fila por fila y guarda los datos válidos y los errores de cada fila (`PROCESSING` → `COMPLETED` o `FAILED`).
4. El frontend consulta el estado cada 3 segundos y, al terminar, muestra los datos, los errores por fila y la actividad agrupada por mes.

## Stack

| | |
|---|---|
| Lenguaje y framework | Java 21, Spring Boot 3.3 (Web, Data JPA, JDBC, Scheduling) |
| Base de datos | PostgreSQL 16 (H2 en modo PostgreSQL para desarrollo y tests) |
| Cola | Tabla `queue_messages` con la semántica de SQS (visibility timeout, maxReceiveCount, DLQ) |
| Almacenamiento | Disco local, detrás del puerto `FileStorage` |
| Frontend | HTML, CSS y JavaScript sin dependencias (servido por Spring) |
| Tests | JUnit 5, AssertJ, Spring Boot Test, MockMvc |
| Infra | Docker, Docker Compose, GitHub Actions (tests + prueba de punta a punta con PostgreSQL) |

## Arquitectura

```
src/main/java/io/github/ignacioediaz/carbonbox/
├── shared/domain/                  Either y errores de dominio
├── documentprocessing/             Bounded context
│   ├── domain/                     Upload (aggregate root), UploadStatus, value objects, repositorios (puertos)
│   ├── application/
│   │   ├── commands/               CreateUpload, ProcessFile, RecordProcessingFailure, RedriveUpload
│   │   ├── queries/                Consultas y vistas de lectura (CQRS)
│   │   ├── services/               CsvParser
│   │   └── ports/                  FileStorage, MessagePublisher, UnitOfWork
│   ├── infrastructure/
│   │   ├── persistence/            JPA para el agregado, JDBC batch para filas, read model con SQL
│   │   ├── messaging/              Cola sobre base de datos y worker
│   │   └── storage/                Archivos en disco
│   └── presentation/               Controladores REST y manejo de errores
└── config/                         Composición: conecta casos de uso con adaptadores
```

Decisiones principales:

- **Dominio sin Spring.** `domain` y `application` no importan nada de Spring; los casos de uso reciben sus puertos por constructor y se arman en `DocumentProcessingConfig`. Por eso los tests de los casos de uso corren con dobles en memoria, sin base ni contexto.
- **Errores como valores.** Los errores esperables (validación, no encontrado, transición inválida) viajan en un `Either`; las excepciones quedan para lo inesperado (disco, base caída), que es lo que el worker reintenta.
- **Máquina de estados en el agregado.** `UploadStatus.canTransitionTo` define qué cambios de estado existen; `Upload` no deja completar algo que no empezó ni tocar algo ya completado.
- **CQRS.** Los comandos cargan y guardan el agregado con JPA. Las consultas no pasan por el dominio: leen proyecciones con SQL (`JdbcUploadReadModel`), incluida la agregación por mes.
- **Cola con semántica de SQS sobre la misma base.** Tomar un mensaje es un `UPDATE` condicional (si dos workers compiten, solo uno lo consigue) que lo oculta durante el *visibility timeout*; si el worker se cae, el mensaje vuelve a aparecer solo. Cada recepción suma `receive_count`; al llegar a `maxReceiveCount` va a la DLQ y el upload queda `FAILED` con el motivo. Como la cola vive en la misma base, crear el upload y encolarlo es una sola transacción (patrón outbox): no puede quedar un upload sin mensaje ni un mensaje sin upload. Reemplazarla por SQS es implementar `MessagePublisher` y un consumidor.
- **Procesamiento idempotente.** La entrega es "al menos una vez": un mensaje repetido de un upload terminado no hace nada, y uno a medio procesar reemplaza sus filas en vez de duplicarlas.
- **Subida idempotente.** El header `Idempotency-Key` (o, si no viene, el SHA-256 del contenido) evita crear dos uploads del mismo archivo; la restricción `UNIQUE` cubre dos pedidos simultáneos.
- **Reintentos con backoff exponencial** y **redrive**: un upload que falló se puede volver a encolar desde la web o con `POST /api/uploads/{id}/redrive`.

## Cómo correrlo

Con Docker (PostgreSQL + app):

```bash
docker compose up --build
# http://localhost:8080
```

Sin Docker (H2 en memoria, necesita JDK 21 y Maven):

```bash
mvn spring-boot:run
# http://localhost:8080
```

Hay un CSV de ejemplo con filas válidas y con errores en `ejemplos/actividad.csv`.

## API

| Método | Ruta | |
|---|---|---|
| POST | `/api/uploads` | Subir CSV (multipart, campo `file`; header opcional `Idempotency-Key`). 202 si lo crea, 200 si ya existía |
| GET | `/api/uploads?page=1&size=10` | Lista paginada |
| GET | `/api/uploads/summary` | Totales por estado |
| GET | `/api/uploads/{id}` | Estado y contadores |
| GET | `/api/uploads/{id}/results?page=1&size=50` | Filas procesadas |
| GET | `/api/uploads/{id}/errors` | Errores por fila |
| POST | `/api/uploads/{id}/redrive` | Volver a encolar un upload fallido |
| GET | `/api/activity/by-month` | Totales por mes, categoría y unidad |
| GET | `/api/queue/stats` · `/api/queue/dead-letters` | Estado de la cola y DLQ |
| GET | `/api/health` | Estado de la app y la base |

## Tests

```bash
mvn verify
```

- **Dominio:** transiciones del upload, nombre de archivo, clave de idempotencia.
- **Parser:** filas válidas, BOM y saltos de Windows, comillas, fechas imposibles (2026-02-30), montos negativos o no numéricos, columnas faltantes.
- **Casos de uso** con dobles en memoria: subida idempotente, archivo inválido que no se guarda ni se encola, mensaje repetido, error de disco que se propaga para reintentar.
- **Integración** (Spring + H2 + MockMvc): flujo completo por la API, paginación, errores HTTP; reintentos hasta la DLQ, redrive y visibility timeout con la cola real.
- **CI:** además de los tests, levanta la app contra un PostgreSQL 16 real y sube el CSV de ejemplo (`scripts/smoke-test.sh`).

## Limitaciones conocidas

- No tiene autenticación (el original tenía registro y login con JWT).
- El almacenamiento es en disco local; en producción iría S3 detrás del mismo puerto `FileStorage`.
- El worker corre dentro de la misma app. Escala a varias instancias porque tomar un mensaje es atómico, pero no hay un proceso de worker separado.
- La barra de progreso es indeterminada: no se informa el avance fila por fila.

## Uso de IA

Igual que en la prueba original, usé IA (Claude Code) como asistente para escribir este proyecto, tomando como especificación mi implementación en Node.js: la consigna, el modelo de dominio, los estados y las decisiones de arquitectura vienen de ahí.

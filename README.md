# GPN Test Task

Проект содержит три самостоятельных сервиса из задания, собранных в одном sbt multi-module репозитории:

- `password-service` — REST-хранилище паролей с PostgreSQL, CSV import/export, историей, статистикой и OpenAPI.
- `url-downloader-service` — асинхронный downloader StackOverflow Search API по тегам, с ограничением параллелизма и сохранением JSON-файлов.
- `scheduler-service` — Планировщик, который генерирует записи отходов в PostgreSQL и независимо сохраняет агрегаты веса.

## Архитектура

Сервисы не завязаны друг на друга бизнес-логикой. Их лучше рассматривать как три отдельных приложения с общей инфраструктурой:

- `modules/common` содержит общие `AppError`, конфиг через env, CSV helper, JSON codecs, минимальный HTTP router на `jdk.httpserver`, JDBC/Hikari слой и запуск SQL-миграций.
- Каждый сервис имеет свой домен, HTTP routes, миграции, repository trait и конкретную PostgreSQL/JDBC реализацию.
- PostgreSQL легко заменить: HTTP и бизнес-логика зависят от `PasswordRepository` или `WasteRepository`, а не от SQL-кода.
- URL downloader не использует БД: его состояние job хранится in-memory, а результат сохраняется в файловую систему.

## Требования

- JDK `25.0.2`
- Scala `3.3.7`
- sbt `1.12.10`
- Docker и Docker Compose для запуска PostgreSQL и контейнеров

Если `sbt` не установлен, проект можно запускать через IntelliJ IDEA или любой sbt launcher. В этой рабочей копии также есть `.bsp/sbt.json` с launcher из IntelliJ.

## Быстрый Запуск Через Docker Compose

```bash
docker compose up --build
```

После запуска:

- Password service: `http://localhost:8081`
- URL downloader service: `http://localhost:8082`
- Scheduler service: `http://localhost:8083`
- Swagger UI: `/swagger` у каждого сервиса
- OpenAPI JSON: `/openapi.json` у каждого сервиса

Остановка:

```bash
docker compose down
```

Остановка с удалением данных PostgreSQL и downloader-файлов:

```bash
docker compose down -v
```

## Локальный Запуск Из sbt

Сначала поднимите PostgreSQL:

```bash
docker compose up -d postgres
```

Password service:

```bash
DB_JDBC_URL=jdbc:postgresql://localhost:5432/gpn \
DB_USER=gpn \
DB_PASSWORD=gpn \
PASSWORD_HTTP_PORT=8081 \
sbt passwordService/run
```

URL downloader service:

```bash
URL_DOWNLOADER_SERVER_PORT=8082 \
URL_DOWNLOADER_PATH=./data/stackoverflow \
URL_DOWNLOADER_PAGES=2 \
URL_DOWNLOADER_PAGE_SIZE=10 \
URL_DOWNLOADER_MAX_PARALLEL=2 \
sbt urlDownloaderService/run
```

Scheduler service:

```bash
DB_JDBC_URL=jdbc:postgresql://localhost:5432/gpn \
DB_USERNAME=gpn \
DB_PASSWORD=gpn \
SCHEDULER_HTTP_PORT=8083 \
SCHEDULER_GENERATE_INTERVAL_MS=10000 \
SCHEDULER_SNAPSHOT_INTERVAL_MS=15000 \
SCHEDULER_ITEMS_PER_TYPE=3 \
sbt schedulerService/run
```

## API Примеры

### Password Service

Создать запись:

```bash
curl -s -X POST http://localhost:8081/api/passwords \
  -H 'Content-Type: application/json' \
  -d '{"name":"ГосУслуги","password":"Russkie_Vpered_66","comment":"main account"}'
```

Поиск по частичному совпадению:

```bash
curl -s 'http://localhost:8081/api/passwords/search?q=Гос&field=name&mode=partial'
```

Экспорт в CSV:

```bash
curl -s http://localhost:8081/api/passwords/export
```

Импорт CSV:

```bash
curl -s -X POST http://localhost:8081/api/passwords/import \
  -H 'Content-Type: text/csv' \
  --data-binary $'name,password,comment\nmail,secret,work\n'
```

Статистика:

```bash
curl -s 'http://localhost:8081/api/passwords/stats?oldDays=365&oldestLimit=20'
```

### URL Downloader Service

Запустить асинхронную загрузку:

```bash
curl -s -X POST 'http://localhost:8082/downloads?tagged=scala&tagged=zio'
```

Получить статус job:

```bash
curl -s http://localhost:8082/downloads/<jobId>
```

Файлы сохраняются в `URL_DOWNLOADER_PATH/<tag>/page-N.json`. В docker-compose это volume `downloader-data`.

### Scheduler Service

Статус:

```bash
curl -s http://localhost:8083/status
```

Ручной tick генерации и snapshot:

```bash
curl -s -X POST 'http://localhost:8083/tick?mode=both'
```

Агрегаты:

```bash
curl -s http://localhost:8083/stats
```

## Конфигурация

### Password Service

- `PASSWORD_HTTP_HOST`, default `0.0.0.0`
- `PASSWORD_HTTP_PORT`, default `8081`
- `DB_JDBC_URL` или `PASSWORD_DB_URL`
- `DB_USER` или `PASSWORD_DB_USER`
- `DB_PASSWORD` или `PASSWORD_DB_PASSWORD`
- `DB_POOL_SIZE` или `PASSWORD_DB_POOL_SIZE`

### URL Downloader Service

- `URL_DOWNLOADER_SERVER_HOST`, default `0.0.0.0`
- `URL_DOWNLOADER_SERVER_PORT`, default `8082`
- `URL_DOWNLOADER_PAGES`, default from `url-downloader.properties`
- `URL_DOWNLOADER_PAGE_SIZE`, must be `1..100`
- `URL_DOWNLOADER_SORT`, default `activity`
- `URL_DOWNLOADER_ORDER`, `asc` or `desc`
- `URL_DOWNLOADER_PATH`
- `URL_DOWNLOADER_MAX_PARALLEL`
- `URL_DOWNLOADER_STACKEXCHANGE_BASE_URL`
- `URL_DOWNLOADER_REQUEST_TIMEOUT_SECONDS`

### Scheduler Service

- `SCHEDULER_HTTP_HOST`, default `0.0.0.0`
- `SCHEDULER_HTTP_PORT`, default `8083`
- `DB_JDBC_URL` или `SCHEDULER_DB_JDBC_URL`
- `DB_USERNAME` или `SCHEDULER_DB_USERNAME`
- `DB_PASSWORD` или `SCHEDULER_DB_PASSWORD`
- `SCHEDULER_DB_POOL_SIZE`
- `SCHEDULER_GENERATE_INTERVAL_MS`
- `SCHEDULER_SNAPSHOT_INTERVAL_MS`
- `SCHEDULER_ITEMS_PER_TYPE`
- `SCHEDULER_STATS_LIMIT`

## Тесты

Быстрые unit и route-тесты:

```bash
sbt test
```

По модулям:

```bash
sbt common/test
sbt passwordService/test
sbt urlDownloaderService/test
sbt schedulerService/test
```

PostgreSQL integration tests добавлены через Testcontainers и по умолчанию выключены:

```bash
PASSWORD_SERVICE_IT=1 sbt passwordService/testOnly ru.gpn.password.PostgresPasswordRepositorySpec
SCHEDULER_RUN_IT=1 sbt schedulerService/testOnly ru.gpn.scheduler.JdbcWasteRepositorySpec
```

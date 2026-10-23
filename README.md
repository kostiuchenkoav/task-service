# task-service

Микросервис управления задачами: REST API + PostgreSQL + миграции Flyway.
Проект-шаблон: слоистая структура (web → service → domain), валидация,
страницы/сортировка, оптимистичная блокировка, ошибки в формате RFC 9457,
доступ по API-ключам.

## Стек

| Компонент | Версия / выбор |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.1.2 |
| Сборка | Maven |
| Безопасность | Spring Security, аутентификация по заголовку `X-API-Key` |
| Хранение | PostgreSQL 17 (прод), H2 в режиме PostgreSQL (dev/тесты) |
| Схема БД | Flyway, `ddl-auto: validate` |
| Доступ к данным | Spring Data JPA |
| Наблюдаемость | Spring Boot Actuator (`/actuator/health`, `/actuator/info`), graceful shutdown |
| Документация API | springdoc-openapi 3.1.1 (ветка 3.x под Spring Boot 4): OpenAPI 3 + Swagger UI, только в dev и test |

## Структура

```
src/main/java/ru/strata/tasks
├── TaskServiceApplication.java     входная точка
├── config/AppConfig.java           Bean Clock (тестируемость времени)
├── config/OpenApiConfig.java       метаданные OpenAPI: название, версия, описание правил
├── config/SecurityConfig.java      правила доступа: API закрыт, health публичен
├── config/ApiKeyAuthenticationFilter.java  проверка X-API-Key по SHA-256
├── config/SecurityProperties.java  список ключей клиентов (только хеши)
├── config/ProductionDataSourceGuard.java   проверка пароля к боевой БД
├── domain/                         задача (с тенантом и мягким удалением), статусы и переходы,
│                                   журнал аудита, записи идемпотентности, репозитории
├── service/                        бизнес-операции, команды, предметные исключения
└── web/                            контроллер, DTO, единый обработчик ошибок
src/main/resources
├── application.yml                 общий конфиг (значения через переменные окружения)
├── application-dev.yml             H2 в памяти, H2-консоль, Swagger, ключ dev
├── application-prod.yml            боевой профиль: без Swagger, без деталей ошибок
└── db/migration/                   V1 — таблица, V2 — метки времени с часовым поясом,
                                    V3 — тенант, мягкое удаление, аудит, идемпотентность
```

## Запуск

Нужны JDK 21 и Maven. JDK: `winget install Microsoft.OpenJDK.21`
(ставится в `C:\Program Files\Microsoft\jdk-21.*`), затем `JAVA_HOME` на этот путь.
Maven в winget отсутствует — берём архив `apache-maven-3.9.x-bin.zip` с `maven.apache.org`
и добавляем его `bin` в `PATH`.

**Без внешней БД (профиль `dev`, H2 в памяти):**

```
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Приложение на `http://localhost:8080`, консоль H2: `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:tasks;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`).
Ключ для запросов в dev: `dev-key` (заголовок `X-API-Key: dev-key`), тенант `local`.

**С PostgreSQL (Docker):**

```
cp .env.example .env      # задать POSTGRES_PASSWORD и API_KEY_SHA256
docker compose up --build
```

Пароль БД и хеш ключа берутся только из `.env` — без них compose отказывается стартовать.
Оба порта (5432 и 8080) публикуются только на `127.0.0.1`, поэтому стенд не торчит
в локальную сеть. Контейнер с приложением поднимается только после готовности
PostgreSQL (healthcheck), а сам образ проверяет `/actuator/health` (HEALTHCHECK).

## Аутентификация

Все эндпоинты API требуют заголовок `X-API-Key: <ключ>`. Публично доступны только
`/actuator/health` и `/actuator/info`. Без ключа или с неизвестным ключом ответ —
`401 Unauthorized` в формате `application/problem+json`.

В конфигурации хранится **не сам ключ, а его SHA-256**. Сгенерировать ключ клиенту:

```bash
KEY=$(openssl rand -hex 32); echo "$KEY"                  # ключ передаём клиенту
printf '%s' "$KEY" | sha256sum | cut -d' ' -f1           # хеш — в конфиг
```

Хеш задаётся переменными окружения (можно несколько ключей, по одному на клиента):

```
APP_SECURITY_APIKEYS_0_ID=billing
APP_SECURITY_APIKEYS_0_TENANT=billing
APP_SECURITY_APIKEYS_0_SHA256=<64 hex-символа>
APP_SECURITY_APIKEYS_1_ID=reporting
APP_SECURITY_APIKEYS_1_TENANT=reporting
APP_SECURITY_APIKEYS_1_SHA256=<64 hex-символа>
```

**Тенант** — это граница данных. Задачи, созданные ключом, видны только ключам того же
тенанта. Чужая задача возвращает `404`, неотличимый от несуществующей, поэтому по ответам
нельзя понять, что чужой идентификатор занят. Два ключа могут делить один тенант, если
они принадлежат одной организации.

Если ключей нет, API закрыт для всех, а при старте в журнал пишется предупреждение.
Некорректный хеш (не 64 hex-символа) останавливает запуск с понятным сообщением.

## Конфигурация (переменные окружения)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tasks` | JDBC-ссылка |
| `DB_USER` | `tasks` | пользователь |
| `DB_PASSWORD` | — | пароль; **обязателен** для боевого запуска |
| `APP_SECURITY_APIKEYS_<n>_ID` | — | имя клиента для журнала |
| `APP_SECURITY_APIKEYS_<n>_TENANT` | — | тенант клиента (граница данных) |
| `APP_SECURITY_APIKEYS_<n>_SHA256` | — | SHA-256 ключа клиента |
| `APP_RATE_LIMIT_REQUESTS_PER_MINUTE` | `120` | лимит запросов в минуту на один ключ API |
| `SERVER_PORT` | `8080` | порт |
| `SPRING_PROFILES_ACTIVE` | в образе: `prod` | профиль (`dev`, `prod`) |

Пароль намеренно не имеет значения по умолчанию: в профилях `dev` и `test` datasource
переопределён на H2, а запуск на PostgreSQL обязан получить `DB_PASSWORD`
(или `SPRING_DATASOURCE_PASSWORD`) из окружения. `ProductionDataSourceGuard` проверяет это
до подключения к базе и останавливает старт с понятным текстом ошибки — иначе Spring
отдал бы `null` и падение произошло бы уже на `password authentication failed`.
Пароли не хранятся ни в репозитории, ни в образе.

## Профиль prod

Боевой образ запускается с `SPRING_PROFILES_ACTIVE=prod`:
- Swagger UI и `/v3/api-docs` выключены;
- тексты исключений, сообщения и трассы не отдаются клиенту;
- в `/actuator/health` не показываются детали;
- `forward-headers-strategy: framework` — корректный адрес и схема за балансировщиком.

## API

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/api/v1/tasks` | создать задачу |
| `GET` | `/api/v1/tasks?page=0&size=20&status=OPEN` | список, фильтрация по статусу |
| `GET` | `/api/v1/tasks/{id}` | одна задача |
| `PUT` | `/api/v1/tasks/{id}` | изменить заголовок/описание |
| `POST` | `/api/v1/tasks/{id}/transitions` | смена статуса (`{"status": "IN_PROGRESS"}`) |
| `DELETE` | `/api/v1/tasks/{id}` | мягкое удаление (204), история сохраняется |
| `GET` | `/api/v1/tasks/{id}/history` | журнал изменений: кто, когда, какой переход |
| `GET` | `/actuator/health` | состояние сервиса (без ключа) |
| `GET` | `/swagger-ui.html` | интерактивная документация — только dev и test |
| `GET` | `/v3/api-docs` | спецификация OpenAPI в JSON — только dev и test |

Документация строится по аннотациям контроллера и охватывает только пакет
`ru.strata.tasks.web`, поэтому служебные эндпоинты (`/actuator/*`, `/h2-console`)
в бизнес-спецификацию не попадают. В Swagger UI можно выбрать параметр `status`,
вставить JSON тела и выполнить запрос прямо из браузера («Try it out»).

**Идемпотентное создание.** Заголовок `Idempotency-Key` (1–128 символов) делает повтор безопасным
при сетевых сбоях: повторный POST с тем же ключом от того же тенанта возвращает `200` и уже
созданную задачу, дубликата не будет. Новая задача отвечает `201`. Ключи разных тенантов не
пересекаются. Если исходная задача к моменту повтора удалена, повтор вернёт `404`.
Параллельные запросы с одним ключом: второй получает `409`, и клиент должен повторить запрос.

**Аудит.** Каждое создание, изменение, переход статуса и удаление пишется в `task_events`.
Запись не меняется и не удаляется, даже когда задача мягко удалена. В `actor` — идентификатор
ключа API, а не секрет.

Статусы: `OPEN` → `IN_PROGRESS` → `DONE`; `CANCELLED` доступен из `OPEN` и `IN_PROGRESS`;
переходы из терминальных статусов запрещены и дают `409 Conflict`.

```
curl -X POST localhost:8080/api/v1/tasks \
  -H 'X-API-Key: <ключ>' \
  -H 'Content-Type: application/json' \
  -d '{"title": "Собрать отчёт", "description": "за квартал"}'

curl -X POST localhost:8080/api/v1/tasks/1/transitions \
  -H 'X-API-Key: <ключ>' \
  -H 'Content-Type: application/json' \
  -d '{"status": "IN_PROGRESS"}'
```

Ошибки — единый формат `application/problem+json` (RFC 9457):

```json
{
  "title": "Illegal status transition",
  "status": 409,
  "detail": "Cannot move task 1 from DONE to IN_PROGRESS",
  "instance": "/api/v1/tasks/1/transitions",
  "taskId": 1,
  "from": "DONE",
  "to": "IN_PROGRESS"
}
```

Ошибки валидации дополняются массивом `errors` (`field` + `message`).

## Тесты

```
mvn verify
```

- `TaskServiceTest` — правила переходов, тенант, идемпотентность, мягкое удаление (Mockito + фиксированный `Clock`).
- `TenantIsolationTest` — граница тенантов на реальном стеке безопасности: чужая задача не видна,
  список не пересекается, история чужого тенанта недоступна, ключ идемпотентности привязан к тенанту.
- `RateLimitFilterTest` — лимит запросов на ключ, ответ `429` и `Retry-After`, сброс окна, независимость ключей, анонимные запросы не считаются.
- `TaskApiTest` — контракт веб-слоя: коды ответов, валидация, формат ошибок (MockMvc без контейнера).
- `TaskServiceApplicationTests` — H2 + миграции Flyway: сверка схемы с JPA-маппингом и полный жизненный цикл задачи.
- `ProductionDataSourceGuardTest` — запуск на PostgreSQL без пароля запрещён, встроенная база работает без пароля.
- `ApiKeySecurityTest` — без ключа и с неверным ключом API отвечает 401, с верным — 200, health открыт. Ключ `test-key` принадлежит `tenant-a`, `test-key-b` — `tenant-b`.
- `OpenApiTest` — спецификация `v3/api-docs` отдаётся, описывает все шесть операций и не содержит служебных путей; Swagger UI доступен.

## Ограничение частоты запросов

Каждый ключ API может сделать не более `APP_RATE_LIMIT_REQUESTS_PER_MINUTE` запросов в минуту
(по умолчанию 120). Окно фиксированное: счётчик обнуляется в начале каждой минуты. Сверх лимита
возвращается `429 Too Many Requests` в формате problem+json с заголовком `Retry-After`
(сколько секунд ждать до следующего окна).

Лимит считается только для запросов с действующим ключом. Анонимные запросы он не учитывает,
их отсекает `401`. Счётчики хранятся в памяти одной реплики: при нескольких репликах лимит
действует на каждую отдельно. Общий лимит потребует внешнего хранилища счётчиков.

## Схема БД

Меняется только миграциями Flyway: новый шаг — `src/main/resources/db/migration/V<n>__<что_делаем>.sql`,
правки уже применённых шагов недопустимы.

- `V1__create_tasks.sql` — таблица задач;
- `V2__timestamps_with_time_zone.sql` — `created_at` и `updated_at` переведены на `TIMESTAMP WITH TIME ZONE`.
  Для PostgreSQL важно, чтобы сервер работал в UTC (в образе `postgres:17` так и есть).
- `V3__tenancy_audit_idempotency.sql` — колонка `tenant_id` (существующие строки получают `default`),
  `deleted_at` для мягкого удаления, таблицы `task_events` (журнал) и `idempotency_keys`
  с уникальностью `(tenant_id, idem_key)`. Существующие задачи без владельца относятся к тенанту `default`:
  при переходе на новые ключи их нужно перенести в нужный тенант отдельной миграцией или вручную.

## CI

`.github/workflows/ci.yml` — сборка и тесты на Temurin 21 при пуше в `main` и в PR.
Перед сборкой вся история коммитов прогоняется через сканер секретов `gitleaks`
(версия зафиксирована, архив проверяется по контрольной сумме), права `GITHUB_TOKEN`
ограничены `contents: read`.

## Секреты

В репозитории нет паролей и ключей клиентов: только SHA-256 ключа для dev и test
(эти ключи тестовые и в боевом профиле не используются). Доступ к БД и хеш боевого ключа —
только через переменные окружения или локальный файл `.env` (в git не попадает; в репозитории
лежит шаблон `.env.example`). `.gitignore` прячет `.env`, ключи, сертификаты и хранилища ключей.
Если секрет всё же появится, его не пропустит `gitleaks` в CI.

## Что дальше

- общий лимит частоты для нескольких реплик (внешнее хранилище счетчиков);
- очистка старых записей идемпотентности (сейчас хранятся бессрочно);
- физическое удаление данных по запросу (retention) — сейчас удаление мягкое;
- метрики Prometheus, структурированные логи с идентификатором запроса, трассировка;
- сортировка по другим полям;
- замена предметной области (сейчас «задачи»): переименовать пакет `ru.strata.tasks`, артефакт и таблицу.

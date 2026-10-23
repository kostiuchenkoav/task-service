# task-service

Микросервис управления задачами: REST API + PostgreSQL + миграции Flyway.
Проект-шаблон: слоистая структура (web → service → domain), валидация,
страницы/сортировка, оптимистичная блокировка, ошибки в формате RFC 9457.

## Стек

| Компонент | Версия / выбор |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.1.2 |
| Сборка | Maven |
| Хранение | PostgreSQL 17 (прод), H2 в режиме PostgreSQL (dev/тесты) |
| Схема БД | Flyway, `ddl-auto: validate` |
| Доступ к данным | Spring Data JPA |
| Наблюдаемость | Spring Boot Actuator (`/actuator/health`, `/actuator/info`), graceful shutdown |

## Структура

```
src/main/java/ru/strata/tasks
├── TaskServiceApplication.java     входная точка
├── config/AppConfig.java           Bean Clock (тестируемость времени)
├── domain/                         сущность, enum статусов + правила переходов, репозиторий
├── service/                        бизнес-операции, команды, предметные исключения
└── web/                            контроллер, DTO, единый обработчик ошибок
src/main/resources
├── application.yml                 общий конфиг (значения через переменные окружения)
├── application-dev.yml             H2 в памяти + H2-консоль
└── db/migration/V1__create_tasks.sql
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

**С PostgreSQL (Docker):**

```
docker compose up --build
```

Контейнер с приложением поднимается только после готовности PostgreSQL (healthcheck).

## Конфигурация (переменные окружения)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tasks` | JDBC-ссылка |
| `DB_USER` | `tasks` | пользователь |
| `DB_PASSWORD` | `tasks` | пароль |

В проде достаточно переопределить эти три значения; пароли в образ не попадают.

## API

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/api/v1/tasks` | создать задачу |
| `GET` | `/api/v1/tasks?page=0&size=20&status=OPEN` | список, фильтрация по статусу |
| `GET` | `/api/v1/tasks/{id}` | одна задача |
| `PUT` | `/api/v1/tasks/{id}` | изменить заголовок/описание |
| `POST` | `/api/v1/tasks/{id}/transitions` | смена статуса (`{"status": "IN_PROGRESS"}`) |
| `DELETE` | `/api/v1/tasks/{id}` | удалить (204) |
| `GET` | `/actuator/health` | состояние сервиса |

Статусы: `OPEN` → `IN_PROGRESS` → `DONE`; `CANCELLED` доступен из `OPEN` и `IN_PROGRESS`;
переходы из терминальных статусов запрещены и дают `409 Conflict`.

```
curl -X POST localhost:8080/api/v1/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title": "Собрать отчёт", "description": "за кварталу"}'

curl -X POST localhost:8080/api/v1/tasks/1/transitions \
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

- `TaskServiceTest` — правила переходов и временные метки (Mockito + фиксированный `Clock`).
- `TaskApiTest` — контракт веб-слоя: коды ответов, валидация, формат ошибок (MockMvc без контейнера).
- `TaskServiceApplicationTests` — H2 + миграции Flyway: сверка схемы с JPA-маппингом и полный жизненный цикл задачи.

## Схема БД

Меняется только миграциями Flyway: новый шаг — `src/main/resources/db/migration/V2__<что_делаем>.sql`,
правки уже применённых шагов недопустимы.

## CI

`.github/workflows/ci.yml` — сборка и тесты на Temurin 21 при пуше в `main` и в PR.

## Что дальше

- заменить предметную область (сейчас «задачи») на вашу: переименовать пакет `ru.strata.tasks`, артефакт и таблицу;
- аутентификация/авторизация (Spring Security) и привязка задач к владельцу;
- OpenAPI-документация (springdoc) — версия под Spring Boot 4 на момент шаблона не зафиксирована, поэтому не подключена;
- метрики Prometheus, трассировка, вычитывание событий (outbox), сортировка по другим полям.

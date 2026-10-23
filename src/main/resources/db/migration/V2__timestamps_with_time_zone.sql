-- Метки времени переводятся на TIMESTAMP WITH TIME ZONE. Приложение пишет и читает
-- их в UTC (hibernate.jdbc.time_zone). Значения, записанные по V1, интерпретируются
-- в часовом поясе сессии PostgreSQL, поэтому сервер БД должен работать в UTC
-- (образ postgres:17 по умолчанию работает именно в UTC).
ALTER TABLE tasks ALTER COLUMN created_at SET DATA TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE tasks ALTER COLUMN updated_at SET DATA TYPE TIMESTAMP WITH TIME ZONE;

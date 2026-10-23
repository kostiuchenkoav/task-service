package ru.strata.tasks.domain;

/** Типы записей журнала аудита по задаче. */
public enum TaskEventType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    DELETED
}

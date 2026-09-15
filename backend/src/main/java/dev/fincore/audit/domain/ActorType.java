package dev.fincore.audit.domain;

/** Quem realizou a ação registrada: uma pessoa autenticada, ou o próprio sistema (TDS 7.2). */
public enum ActorType {
    USER,
    SYSTEM
}

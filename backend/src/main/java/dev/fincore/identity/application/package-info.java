/**
 * Casos de uso da identidade (Implementation Plan M2): {@code Login}, {@code RefreshSession},
 * {@code Logout}, {@code ChangeUserRole}. {@code @PreAuthorize} vive aqui, não no
 * controller (TDS 21.2) — o caso de uso é a fronteira de segurança.
 */
package dev.fincore.identity.application;

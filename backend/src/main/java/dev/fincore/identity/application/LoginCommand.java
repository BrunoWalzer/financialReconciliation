package dev.fincore.identity.application;

/** O que o cliente envia para autenticar. Papel nunca vem daqui — só e-mail e senha. */
public record LoginCommand(String email, String password) {
}

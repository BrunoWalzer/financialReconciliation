package dev.fincore.platform;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O relógio da aplicação, injetável em vez de chamado estaticamente.
 *
 * <p>Nasce no M2: expiração de token, bloqueio de login e rotação de sessão precisam ser
 * testáveis sem esperar minutos reais (regra 34 do Plano — nenhum teste depende do
 * relógio real). Um teste troca este bean por um {@link Clock} fixo/mutável; a produção
 * usa {@link Clock#systemUTC()}.
 *
 * <p>Isto não se aplica a {@code matching}: lá o relógio é proibido por completo
 * (ArchUnit {@code MATCHING_DOES_NOT_READ_THE_CLOCK}), porque a data de avaliação é
 * sempre parâmetro, nunca "agora". Aqui o caso é o oposto — expiração de sessão é
 * inerentemente sobre o instante real, só que injetado, não chamado direto.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

package dev.fincore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do monólito modular FINCORE.
 *
 * <p>Um único processo expõe a API, e a partir do M8 hospeda consumidores RabbitMQ e o
 * scheduler do sweep de trabalhos travados (TDS 3, 17.5). O pacote raiz {@code dev.fincore}
 * é deliberado: o component scan cobre todos os módulos sem configuração adicional.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FincoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FincoreApplication.class, args);
    }
}

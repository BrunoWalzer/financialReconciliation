package dev.fincore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do monólito modular FINCORE.
 *
 * <p>Um único processo expõe a API, e a partir do M8 hospedará consumidores e scheduler
 * (TDS 3). O pacote raiz {@code dev.fincore} é deliberado: o component scan cobre todos
 * os módulos sem configuração adicional.
 */
@SpringBootApplication
public class FincoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FincoreApplication.class, args);
    }
}

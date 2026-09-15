package dev.fincore.architecture.violation;

/** Repositorio alcancado diretamente por {@code LeakyController}. */
public class LeakyRepository {

    public String findSomething() {
        return "algo";
    }
}

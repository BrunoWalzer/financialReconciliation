package dev.fincore.architecture.violation;

/** Viola CONTROLLERS_DO_NOT_DEPEND_ON_REPOSITORIES: controller falando com repositorio. */
public class LeakyController {

    private final LeakyRepository repository = new LeakyRepository();

    public String read() {
        return repository.findSomething();
    }
}

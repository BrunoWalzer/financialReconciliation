package dev.fincore.configuration.api;

import dev.fincore.configuration.application.ListSourcesUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /config/sources} (TDS 19.2). Só leitura — sem endpoint de mutação no M3. */
@RestController
@RequestMapping("/config/sources")
public class SourceController {

    private final ListSourcesUseCase listSourcesUseCase;

    public SourceController(ListSourcesUseCase listSourcesUseCase) {
        this.listSourcesUseCase = listSourcesUseCase;
    }

    @GetMapping
    public List<SourceResponse> list() {
        return listSourcesUseCase.execute().stream().map(SourceResponse::from).toList();
    }
}

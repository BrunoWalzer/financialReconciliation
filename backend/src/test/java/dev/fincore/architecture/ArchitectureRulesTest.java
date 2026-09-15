package dev.fincore.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Aplica as fronteiras da TDS 4.4 ao codigo de producao.
 *
 * <p>Roda antes dos testes unitarios no CI: fronteira quebrada torna o resto ruido.
 */
@Tag("architecture")
class ArchitectureRulesTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.fincore");
    }

    static Stream<ArchRule> rules() {
        return ArchitectureRules.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void deveRespeitarAFronteiraQuandoOCodigoDeProducaoEAnalisado(ArchRule rule) {
        rule.check(productionClasses);
    }
}

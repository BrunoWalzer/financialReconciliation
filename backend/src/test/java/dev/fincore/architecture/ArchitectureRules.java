package dev.fincore.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * As fronteiras arquiteturais do FINCORE, expressas como regras verificáveis (TDS 4.4).
 *
 * <p>Estão aqui, e não dentro de uma classe de teste, porque são verificadas duas vezes:
 * {@link ArchitectureRulesTest} as aplica ao código de produção e
 * {@link ArchitectureRulesAreEnforcedTest} as aplica a classes que violam de propósito,
 * para provar que uma violação real seria detectada.
 *
 * <p>A maior parte dos pacotes-alvo ainda está vazia no M0. As regras passam vacuamente e
 * ficam armadas — ver {@code src/test/resources/archunit.properties}.
 */
public final class ArchitectureRules {

    private static final String ROOT = "dev.fincore";

    private ArchitectureRules() {
    }

    // ------------------------------------------------------------ 1. domain sem Spring

    /**
     * {@code domain} nao depende de Spring. O dominio precisa ser testavel sem contexto e
     * legivel por quem entende de conciliacao, nao de framework (TDS 4.4).
     */
    public static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_SPRING =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("o dominio e testavel sem contexto Spring (TDS 4.4)");

    // ------------------------------------------------------------ 2. sem ciclo entre modulos

    /** Nenhum ciclo entre modulos. Um ciclo torna a fronteira decorativa. */
    public static final ArchRule MODULES_ARE_FREE_OF_CYCLES =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

    // ------------------------------------------------------------ 3. proibicoes da TDS 4.3

    /** Nenhum modulo depende da camada {@code api}: seria inversao de camada. */
    public static final ArchRule NO_MODULE_DEPENDS_ON_THE_API_LAYER =
            noClasses()
                    .that().resideInAPackage(ROOT + "..")
                    .and().resideOutsideOfPackage("..api..")
                    .should().dependOnClassesThat(
                            resideInAPackage(ROOT + "..").and(resideInAPackage("..api..")))
                    .because("a camada api e o topo: ninguem depende dela (TDS 4.3)");

    /**
     * {@code matching} nao conhece {@code divergence}. O motor devolve decisoes;
     * {@code reconciliation} e quem as aplica. Sem isso ha ciclo.
     */
    public static final ArchRule MATCHING_DOES_NOT_DEPEND_ON_DIVERGENCE =
            noClasses()
                    .that().resideInAPackage(ROOT + ".matching..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".divergence..")
                    .because("o motor devolve decisoes; reconciliation as aplica (TDS 4.3)");

    /**
     * {@code matching} nao conhece {@code configuration}: o motor recebe o snapshot de
     * configuracao como parametro e permanece uma funcao pura (TDS P4).
     */
    public static final ArchRule MATCHING_DOES_NOT_DEPEND_ON_CONFIGURATION =
            noClasses()
                    .that().resideInAPackage(ROOT + ".matching..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".configuration..")
                    .because("o motor recebe o snapshot como parametro (TDS P4)");

    /**
     * {@code evidence.domain}/{@code application}/{@code infrastructure} so dependem de
     * {@code shared} e {@code audit}. TDS 4.2 lista só {@code evidence --> shared}, mas TDS
     * 22.2 exige que a criação de {@code RecordAnnotation} seja auditada ("anotação
     * criada") na mesma transação (TDS P6) — o que exige {@code AuditService}
     * ({@code audit.application}). Extensão mínima do grafo, mesmo raciocínio de
     * {@code configuration --> audit} (M3); ver o relatório do M4, seção Decisões.
     *
     * <p>{@code evidence.api} fica de fora de propósito, mesmo padrão de
     * {@code configuration.api}: resolve {@code CurrentUser} via {@code identity} (autor da
     * anotação) e {@code sourceCode} via {@code configuration} (filtro de
     * {@code GET /records}).
     */
    public static final ArchRule EVIDENCE_CORE_ONLY_DEPENDS_ON_SHARED_AND_AUDIT =
            noClasses()
                    .that().resideInAPackage(ROOT + ".evidence..")
                    .and().resideOutsideOfPackage(ROOT + ".evidence.api..")
                    .should().dependOnClassesThat(
                            resideInAPackage(ROOT + "..")
                                    .and(not(resideInAnyPackage(
                                            ROOT + ".evidence..", ROOT + ".shared..", ROOT + ".audit.."))))
                    .as("evidence.domain/application/infrastructure so dependem de shared e audit");

    /** {@code audit} e folha: so pode depender de {@code shared} (viabiliza M1 antes de M2). */
    public static final ArchRule AUDIT_ONLY_DEPENDS_ON_SHARED =
            moduleMayOnlyDependOn("audit", "shared");

    /**
     * {@code configuration.domain}/{@code application}/{@code infrastructure} so dependem
     * de {@code shared} e {@code audit} (TDS 4.2: as unicas arestas listadas para este
     * modulo). Em particular, nao dependem de {@code identity} — e por isso que os casos
     * de uso de configuration recebem o ator (para auditoria) como UUID/String simples,
     * nunca como {@code CurrentUser}; e nao dependem de {@code divergence} nem
     * {@code reconciliation} — a garantia de FD-6 ("configuracao nunca encerra
     * divergencia") vem da ausencia de qualquer caminho de codigo entre os dois, nao de
     * uma checagem em tempo de execucao.
     *
     * <p>{@code configuration.api} fica de fora de propósito: a camada api tem a aresta
     * {@code api --> identity} liberada pelo grafo (TDS 4.2) — é ela quem resolve
     * {@code CurrentUser} para email antes de chamar a aplicação (ver
     * {@code GetCurrentUserUseCase} nos controllers de configuration).
     */
    public static final ArchRule CONFIGURATION_CORE_ONLY_DEPENDS_ON_SHARED_AND_AUDIT =
            noClasses()
                    .that().resideInAPackage(ROOT + ".configuration..")
                    .and().resideOutsideOfPackage(ROOT + ".configuration.api..")
                    .should().dependOnClassesThat(
                            resideInAPackage(ROOT + "..")
                                    .and(not(resideInAnyPackage(
                                            ROOT + ".configuration..", ROOT + ".shared..", ROOT + ".audit.."))))
                    .as("configuration.domain/application/infrastructure so dependem de shared e audit");

    /** Importacao nao sabe o que sera feito com os dados que ela traz. */
    public static final ArchRule INGESTION_DOES_NOT_DEPEND_ON_MATCHING_OR_RECONCILIATION =
            noClasses()
                    .that().resideInAPackage(ROOT + ".ingestion..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(ROOT + ".matching..", ROOT + ".reconciliation..")
                    .because("importacao nao sabe o que sera feito com os dados (TDS 4.3)");

    /**
     * {@code ingestion.domain}/{@code application}/{@code parser}/{@code infrastructure}
     * so dependem de {@code shared}, {@code evidence}, {@code configuration} e
     * {@code audit} — as arestas que a TDS 4.2 lista para {@code ingestion}.
     *
     * <p>{@code ingestion.api} fica de fora de proposito, mesmo padrao de
     * {@code configuration.api}/{@code evidence.api}: resolve {@code CurrentUser} via
     * {@code identity} para saber quem fez upload.
     */
    public static final ArchRule INGESTION_CORE_ONLY_DEPENDS_ON_ALLOWED_MODULES =
            noClasses()
                    .that().resideInAPackage(ROOT + ".ingestion..")
                    .and().resideOutsideOfPackage(ROOT + ".ingestion.api..")
                    .should().dependOnClassesThat(
                            resideInAPackage(ROOT + "..")
                                    .and(not(resideInAnyPackage(
                                            ROOT + ".ingestion..", ROOT + ".shared..", ROOT + ".evidence..",
                                            ROOT + ".configuration..", ROOT + ".audit.."))))
                    .as("ingestion.domain/application/parser/infrastructure so dependem de shared, evidence, configuration e audit");

    /**
     * {@code analytics} e somente leitura, por construcao.
     *
     * <p>"Nao escrever em tabela nenhuma" nao e decidivel estaticamente. O que esta regra
     * verifica e o caminho por onde uma escrita realmente apareceria: chamada de metodo de
     * mutacao em JDBC, Spring Data ou JPA. E uma aproximacao, e e a que pega o caso real.
     */
    public static final ArchRule ANALYTICS_NEVER_WRITES =
            noClasses()
                    .that().resideInAPackage(ROOT + ".analytics..")
                    .should().callMethodWhere(mutatingPersistenceCall())
                    .because("analytics le views e nao escreve em tabela alguma (TDS 4.3)");

    // ------------------------------------------------------------ 4. matching sem relogio

    /**
     * {@code matching} nao le o relogio. Determinismo e o que permite reprocessar uma
     * execucao e obter o mesmo resultado; a data de avaliacao entra como parametro
     * (TDS 11.7).
     */
    public static final ArchRule MATCHING_DOES_NOT_READ_THE_CLOCK =
            noClasses()
                    .that().resideInAPackage("..matching..")
                    .should().callMethodWhere(systemClockCall())
                    .because("o motor e deterministico: a data de avaliacao e parametro (TDS 11.7)");

    // ------------------------------------------------------------ 5. matching sem score

    /**
     * {@code matching} nao referencia tipo com {@code Score} no nome. Score ordena fila de
     * divergencia; nunca decide conciliacao (Plano, regra 17).
     */
    public static final ArchRule MATCHING_DOES_NOT_REFERENCE_SCORE_TYPES =
            noClasses()
                    .that().resideInAPackage("..matching..")
                    .should().dependOnClassesThat().haveSimpleNameContaining("Score")
                    .because("score ordena; nunca decide conciliacao (TDS 12.5)");

    // ------------------------------------------------------------ 6. sem ponto flutuante no dominio

    /**
     * Nenhum campo {@code double} ou {@code float} em {@code *.domain}. Dinheiro e inteiro
     * de unidade minima; ponto flutuante binario nao representa decimo de centavo (TDS P3).
     */
    public static final ArchRule DOMAIN_HAS_NO_FLOATING_POINT_FIELDS =
            noFields()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should().haveRawType(floatingPointType())
                    .because("dinheiro e inteiro de unidade minima; ponto flutuante nao o representa (TDS P3)");

    // ------------------------------------------------------------ 7. repositorio e privado do modulo

    /**
     * Um repositorio so e acessado de dentro do proprio modulo. Modulos conversam por
     * interface de servico; o acesso direto ao repositorio do vizinho e a forma mais comum
     * de a fronteira apodrecer sem ninguem perceber.
     */
    public static final ArchRule REPOSITORIES_ARE_PRIVATE_TO_THEIR_MODULE =
            classes()
                    .that().resideInAPackage(ROOT + "..")
                    .should(notDependOnForeignRepositories())
                    .because("modulos conversam por servico, nao pelo repositorio do vizinho (TDS 4.3)");

    // ------------------------------------------------------------ 8. controller nao ve repositorio

    /** Controller nao fala com repositorio: sempre ha um caso de uso no meio. */
    public static final ArchRule CONTROLLERS_DO_NOT_DEPEND_ON_REPOSITORIES =
            noClasses()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .because("entre controller e repositorio existe sempre um caso de uso (TDS 4.4)");

    // ------------------------------------------------------------ 9. sem REQUIRES_NEW

    /**
     * Nenhum {@code @Transactional(propagation = REQUIRES_NEW)}.
     *
     * <p>Auditoria participa da transacao de quem a chama (TDS P6); uma transacao nova
     * escaparia do rollback e deixaria rastro de algo que nao aconteceu. O TDS admite
     * excecao documentada — quando a primeira surgir, o milestone que precisar dela
     * introduz o mecanismo de excecao junto com a justificativa. Ate la a regra e fechada,
     * que e o estado seguro.
     */
    public static final ArchRule NO_REQUIRES_NEW_PROPAGATION =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage(ROOT + "..")
                    .should(useRequiresNewPropagation())
                    .because("auditoria participa da transacao do chamador (TDS P6, 16.1)");

    /** Todas as regras, na ordem da TDS 4.4. */
    public static List<ArchRule> all() {
        return List.of(
                DOMAIN_DOES_NOT_DEPEND_ON_SPRING,
                MODULES_ARE_FREE_OF_CYCLES,
                NO_MODULE_DEPENDS_ON_THE_API_LAYER,
                MATCHING_DOES_NOT_DEPEND_ON_DIVERGENCE,
                MATCHING_DOES_NOT_DEPEND_ON_CONFIGURATION,
                EVIDENCE_CORE_ONLY_DEPENDS_ON_SHARED_AND_AUDIT,
                AUDIT_ONLY_DEPENDS_ON_SHARED,
                CONFIGURATION_CORE_ONLY_DEPENDS_ON_SHARED_AND_AUDIT,
                INGESTION_DOES_NOT_DEPEND_ON_MATCHING_OR_RECONCILIATION,
                INGESTION_CORE_ONLY_DEPENDS_ON_ALLOWED_MODULES,
                ANALYTICS_NEVER_WRITES,
                MATCHING_DOES_NOT_READ_THE_CLOCK,
                MATCHING_DOES_NOT_REFERENCE_SCORE_TYPES,
                DOMAIN_HAS_NO_FLOATING_POINT_FIELDS,
                REPOSITORIES_ARE_PRIVATE_TO_THEIR_MODULE,
                CONTROLLERS_DO_NOT_DEPEND_ON_REPOSITORIES,
                NO_REQUIRES_NEW_PROPAGATION);
    }

    // ------------------------------------------------------------ construcao das regras

    private static ArchRule moduleMayOnlyDependOn(String module, String... allowedModules) {
        String[] allowedPackages = Stream.concat(Stream.of(module), Arrays.stream(allowedModules))
                .map(name -> ROOT + "." + name + "..")
                .toArray(String[]::new);

        return noClasses()
                .that().resideInAPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat(
                        resideInAPackage(ROOT + "..").and(not(resideInAnyPackage(allowedPackages))))
                .as("o modulo " + module + " so depende de " + String.join(", ", allowedModules));
    }

    private static DescribedPredicate<JavaMethodCall> systemClockCall() {
        Set<String> javaTimeClockMethods = Set.of("now", "systemUTC", "systemDefaultZone");

        return new DescribedPredicate<>("le o relogio do sistema") {
            @Override
            public boolean test(JavaMethodCall call) {
                String owner = call.getTarget().getOwner().getFullName();
                String method = call.getTarget().getName();
                if (owner.startsWith("java.time.") && javaTimeClockMethods.contains(method)) {
                    return true;
                }
                return owner.equals("java.lang.System")
                        && (method.equals("currentTimeMillis") || method.equals("nanoTime"));
            }
        };
    }

    private static DescribedPredicate<JavaMethodCall> mutatingPersistenceCall() {
        Set<String> persistencePackages =
                Set.of("org.springframework.jdbc.", "org.springframework.data.", "jakarta.persistence.");
        Set<String> mutatingPrefixes =
                Set.of("save", "delete", "remove", "persist", "merge", "insert", "update");

        return new DescribedPredicate<>("escreve por JDBC, Spring Data ou JPA") {
            @Override
            public boolean test(JavaMethodCall call) {
                String owner = call.getTarget().getOwner().getFullName();
                if (persistencePackages.stream().noneMatch(owner::startsWith)) {
                    return false;
                }
                String method = call.getTarget().getName();
                return mutatingPrefixes.stream().anyMatch(method::startsWith);
            }
        };
    }

    private static DescribedPredicate<JavaClass> floatingPointType() {
        Set<String> forbidden = Set.of("double", "float", "java.lang.Double", "java.lang.Float");
        return new DescribedPredicate<>("double, float ou seus involucros") {
            @Override
            public boolean test(JavaClass type) {
                return forbidden.contains(type.getName());
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOnForeignRepositories() {
        return new ArchCondition<>("nao depender de repositorio de outro modulo") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originModule = moduleOf(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    boolean foreignRepository = target.getSimpleName().endsWith("Repository")
                            && target.getPackageName().startsWith(ROOT + ".")
                            && !moduleOf(target).equals(originModule);
                    if (foreignRepository) {
                        events.add(SimpleConditionEvent.violated(origin, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> useRequiresNewPropagation() {
        return new ArchCondition<>("usar Transactional com propagation REQUIRES_NEW") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.tryGetAnnotationOfType(Transactional.class)
                        .filter(transactional -> transactional.propagation() == Propagation.REQUIRES_NEW)
                        .ifPresent(transactional -> events.add(SimpleConditionEvent.violated(
                                method, method.getFullName() + " usa propagation = REQUIRES_NEW")));
            }
        };
    }

    /** O modulo de uma classe e o primeiro segmento sob {@code dev.fincore}. */
    private static String moduleOf(JavaClass type) {
        String packageName = type.getPackageName();
        if (!packageName.startsWith(ROOT)) {
            return "";
        }
        String relative = packageName.substring(ROOT.length());
        if (relative.isEmpty()) {
            return "";
        }
        String withoutLeadingDot = relative.substring(1);
        int nextDot = withoutLeadingDot.indexOf('.');
        return nextDot < 0 ? withoutLeadingDot : withoutLeadingDot.substring(0, nextDot);
    }
}

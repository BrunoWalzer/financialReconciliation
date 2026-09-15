plugins {
    // Resolve e baixa o JDK 21 do toolchain quando a máquina não o tiver instalado.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "fincore"

include("backend")

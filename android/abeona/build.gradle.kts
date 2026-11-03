import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.nexus.android.application)
}

// SDK levels, version, signing and the Supabase settings come from the
// nexus.android.application convention (build-logic/). Abeona adds the map
// key the websites use; it has no settings of its own in config.yaml.
android {
    defaultConfig {
        buildConfigField("String", "CARTO_API_KEY", nexus.setting("PUBLIC_CARTO_API_KEY").asBuildConfigString())
    }
}

dependencies {
    // The live companion's auto-start and the widget's periodic refresh.
    implementation(libs.androidx.work.runtime)
    // The home screen widget.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // The trip map.
    implementation(libs.maplibre.android)
    // Reads the text layer of a ticket PDF on the phone, never on a server (intake).
    implementation(libs.pdfbox.android)

    testImplementation(libs.androidx.work.testing)
}

// The shared rules' cases (transfer.fixtures.json, travelday.fixtures.json,
// intake.fixtures.json) live with the web's copy of the rules, so both test
// suites read one file. Only the fixtures are copied onto the unit tests'
// classpath, not the TypeScript beside them — plus intake's samples (the real,
// anonymised share texts and ticket text, and the synthetic PDF), flat.
abstract class CopyFixtures : DefaultTask() {
    @get:InputFiles
    abstract val fixtures: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        fixtures.files.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
    }
}

val transferFixtures = tasks.register<CopyFixtures>("transferFixtures") {
    val lib = rootDir.resolve("../app/abeona/src/lib")
    fixtures.from(lib.listFiles { file -> file.name.endsWith(".fixtures.json") })
    fixtures.from(lib.resolve("intake").listFiles { file -> file.name.endsWith(".fixtures.json") })
    fixtures.from(lib.resolve("intake/fixtures").listFiles { file -> file.extension in setOf("txt", "pdf") })
}

androidComponents {
    onVariants { variant ->
        (variant as? HasHostTests)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)
            ?.sources?.resources?.addGeneratedSourceDirectory(transferFixtures, CopyFixtures::outputDir)
    }
}

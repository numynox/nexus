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

    testImplementation(libs.androidx.work.testing)
}

// The shared rules' cases (transfer.fixtures.json) live with the web's copy of
// the rules, so both test suites read one file. Only the fixtures are copied
// onto the unit tests' classpath, not the TypeScript beside them.
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
    fixtures.from(rootDir.resolve("../app/abeona/src/lib").listFiles { file -> file.name.endsWith(".fixtures.json") })
}

androidComponents {
    onVariants { variant ->
        (variant as? HasHostTests)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)
            ?.sources?.resources?.addGeneratedSourceDirectory(transferFixtures, CopyFixtures::outputDir)
    }
}

group = "app.bicybert"

patches {
    about {
        name = "bicybert patches"
        description = "DeepSeek: run re-signed without Google Play services (PairIP license checks removed), " +
            "install alongside the stock app under its own package name, and sign in to Google through " +
            "microG-RE (MicroG-RE or ReVanced GmsCore)."
        source = "git@github.com:bicybert/deepseek-patches.git"
        author = "bicybert"
        contact = "https://github.com/bicybert"
        website = "https://github.com/bicybert/deepseek-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

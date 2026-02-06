import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.buildKonfig)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm()


    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.onnxruntime)
            implementation(libs.common.csv)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.icons)
            implementation(libs.kotlinx.serialization.json)
//            implementation(libs.androidx.material.icons.core)

//            implementation(compose.material.pck)
//            implementation(compose.materialIconsExtended)

            //ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.java)
            implementation(libs.ktor.client.core.java)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.json)
            implementation(libs.ktor.client.logging.jvm)
            implementation(libs.ktor.client.cio)


            // KOin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
//            implementation(libs.koin.android)
//            implementation(libs.koin.androidx.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)


//            filekit
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.filekit.coil)
            // coil
            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)

            implementation(libs.buildKonfig)

            implementation(libs.reordable.item)

            //metadata
            implementation(libs.metadata.extractor)
            implementation(libs.common.imaging)
            implementation(libs.kim)


            //logger
            implementation(libs.kermit)

            //djl
            implementation(project.dependencies.platform(libs.djl.bom))
            implementation(libs.djl.api)
            implementation(libs.djl.onnxruntime.engine)
            implementation(libs.djl.pytorch.engine)
//            implementation(libs.djl.modality.cv)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

buildkonfig {
    packageName = "com.ghost.tagger"

    // This is the "Base" configuration
    defaultConfigs {
        // Notice: No quotes around Type.STRING
        buildConfigField(Type.STRING, "APP_NAME", "Next")
        buildConfigField(Type.STRING, "VERSION", "1.0.0")

        // If you needed a boolean later, it would be:
        // buildConfigField(Type.BOOLEAN, "IS_DEBUG", "true")
    }
}

compose.desktop {
    application {
        mainClass = "com.ghost.tagger.MainKt" // Ensure this matches your package structure!

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Next"
            packageVersion = "1.0.0"

            windows {
                // Ensure icon.ico is actually at this path
                iconFile.set(project.file("src/jvmMain/composeResources/drawable/icon.ico"))
                menu = true
                shortcut = true
            }

            // This "flattens" the drawable folder into the app's resource directory
            appResourcesRootDir.set(project.file("src/jvmMain/composeResources/drawable"))
        }

        jvmArgs(
            // Try this path first. If it fails, check the 'app' subfolder in the build directory.
            "-splash:${"$"}{APPDIR}/splash.png",

        )
    }
}

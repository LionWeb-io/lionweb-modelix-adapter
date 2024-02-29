import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.test.logger)
}

description = "Adapter forwarding the LionWeb API to the modelix API"

defaultTasks.add("build")

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

group = "io.lionweb"
version = "1.0-SNAPSHOT"

repositories {
    val modelixRegex = "org\\.modelix.*"
    mavenLocal {
        content {
            includeGroupByRegex(modelixRegex)
        }
    }
    maven {
        url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
        content {
            includeGroupByRegex(modelixRegex)
            includeGroup("com.jetbrains") // for our mps dependencies
        }
    }
    mavenCentral {
        content {
            excludeGroupByRegex(modelixRegex)
        }
    }
}

dependencies {
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.stdlib.common)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.serialization.json)

    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.modelix.model.server)

    implementation(libs.logback.classic)
    implementation(libs.ktor.client.java)

    implementation(libs.modelix.model.client)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.modelix.model.server)
    testImplementation(libs.modelix.bulk.model.sync.lib)

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}
// openAPI server

// OpenAPI integration
val basePackage = project.group.toString()
val openAPIServerGenPath = "${project.layout.buildDirectory.get()}/generated"

val generateTargetTaskNameServer = "openApiGenerate-server"
val targetPackageName = "$basePackage.lionweb"
val outputPath = "$openAPIServerGenPath/openapi-server/lionweb"
tasks.register<GenerateTask>(generateTargetTaskNameServer) {
    // we let the Gradle OpenAPI generator plugin build data classes and API interfaces based on the provided
    // OpenAPI specification. That way, the code is forced to stay in sync with the API specification.
    generatorName.set("kotlin-server")
    inputSpec.set(layout.projectDirectory.file("api/lionweb-bulk.yaml").toString())
    outputDir.set(outputPath)
    packageName.set(targetPackageName)
    apiPackage.set(targetPackageName)
    modelPackage.set(targetPackageName)
    // We use patched mustache so that only the necessary parts (i.e. resources and models)
    // are generated. additionally we patch the used serialization framework as the `ktor` plugin
    // uses a different one than we do in the model-server. The templates are based on
    // https://github.com/OpenAPITools/openapi-generator/tree/809b3331a95b3c3b7bcf025d16ae09dc0682cd69/modules/openapi-generator/src/main/resources/kotlin-server
    templateDir.set("$projectDir/src/main/resources/openapi/templates")
    configOptions.set(
        mapOf(
            // we use the ktor generator to generate server side resources and model (i.e. data classes)
            "library" to "ktor",
            // the generated artifacts are not built independently, thus no dedicated build files have to be generated
            "omitGradleWrapper" to "true",
            // the path to resource generation we need
            "featureResources" to "true",
            // disable features we do not use
            "featureAutoHead" to "false",
            "featureCompression" to "false",
            "featureHSTS" to "false",
            "featureMetrics" to "false",
        ),
    )
    // generate only Paths and Models - only this set will produce the intended Paths.kt as well as the models
    // the openapi generator is generally very picky and configuring it is rather complex
    globalProperties.putAll(
        mapOf(
            "models" to "",
            "apis" to "",
            "supportingFiles" to "Paths.kt",
        ),
    )
}

// Ensure that the OpenAPI generator runs before starting to compile
tasks.named("processResources") {
    dependsOn(generateTargetTaskNameServer)
}
tasks.named("compileKotlin") {
    dependsOn(generateTargetTaskNameServer)
}

// add openAPI generated artifacts to the sourceSets
sourceSets["main"].kotlin.srcDir("$outputPath/src/main/kotlin")




// copies the openAPI specifications from the api folder into a resource
// folder so that they are packaged and deployed with the model-server
tasks.register<Copy>("copyApis") {
    from("api/")
    include("lionweb-bulk.yaml")
    into(project.layout.buildDirectory.dir("openapi/src/main/resources/api"))
    sourceSets["main"].resources.srcDir(project.layout.buildDirectory.dir("openapi/src/main/resources/"))
}


tasks.named("compileKotlin") {
    dependsOn("copyApis")
}

tasks.named("build") {
    dependsOn("copyApis")
}

tasks.named("processResources") {
    dependsOn("copyApis")
}

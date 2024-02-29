pluginManagement {
    repositories {
        val modelixRegex = "org\\.modelix.*"
        mavenLocal {
            content {
                includeGroupByRegex(modelixRegex)
            }
        }
        gradlePluginPortal {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
        maven {
            url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
            content {
                includeGroupByRegex(modelixRegex)
            }
        }
        mavenCentral {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
    }
}

rootProject.name = "modelix-adapter"

plugins {
    `kotlin-dsl`
}

group = "com.airbnb.viaduct.build"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

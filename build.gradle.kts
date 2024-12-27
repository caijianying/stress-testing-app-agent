import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.23"
    id("java")
    id("distribution")
    id("com.github.johnrengelman.shadow") version ("8.1.1")
}

group = "com.xiaobaicai.agent"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.12.8")
    implementation("net.bytebuddy:byte-buddy-agent:1.12.8")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    // 单测
    testCompileOnly("junit:junit:4.13.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    // jsqlparser需要单独引入，见 https://github.com/baomidou/mybatis-plus/releases?page=1
    // https://mvnrepository.com/artifact/com.baomidou/mybatis-plus-jsqlparser
    implementation("com.baomidou:mybatis-plus-jsqlparser:3.5.9")
    implementation("org.mybatis:mybatis:3.5.9")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("cn.hutool:hutool-all:5.8.25")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

tasks.withType<ShadowJar> {
    manifest {
        attributes(
            "Manifest-Version" to version,
            "Premain-Class" to "com.xiaobaicai.stress.testing.app.agent.StressTestingAppAgent",
            "Agent-Class" to "com.xiaobaicai.stress.testing.app.agent.StressTestingAppAgent",
            "Can-Redefine-Classes" to true,
            "Can-Retransform-Classes" to true
        )
    }
}
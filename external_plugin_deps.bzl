load("//tools/bzl:maven_jar.bzl", "MAVEN_LOCAL", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "junit-platform",
        artifact = "org.junit.platform:junit-platform-commons:1.4.0",
        sha1 = "34d9983705c953b97abb01e1cd04647f47272fe5",
    )

    maven_jar(
        name = "kinesis-client",
        artifact = "software.amazon.kinesis:amazon-kinesis-client:2.2.9",
        sha1 = "da8b162cf62c2a752db22ba55ff1934cc3b7a6b5",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4-alpha-20210205083200",
        sha1 = "3fec2bfee13b9b0a2889616e3c039ead686b931f",
    )

load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "kinesis-events",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: kinesis-events",
        "Gerrit-Module: com.googlesource.gerrit.plugins.kinesis.Module",
        "Implementation-Title: Gerrit Apache Kinesis plugin",
        "Implementation-URL: https://GerritForge/kinesis-events",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "@amazon-auth//jar",
        "@amazon-aws-core//jar",
        "@amazon-cloudwatch//jar",
        "@amazon-dynamodb//jar",
        "@amazon-http-client-spi//jar",
        "@amazon-kinesis-client//jar",
        "@amazon-kinesis//jar",
        "@amazon-netty-nio-client//jar",
        "@amazon-profiles//jar",
        "@amazon-regions//jar",
        "@amazon-sdk-core//jar",
        "@amazon-utils//jar",
        "@apache-commons-lang3//jar",
        "@awssdk-cbor-protocol//jar",
        "@awssdk-json-protocol//jar",
        "@awssdk-metrics-spi//jar",
        "@awssdk-protocol-core//jar",
        "@awssdk-query-protocol//jar",
        "@commons-codec//jar",
        "@events-broker//jar",
        "@io-netty-all//jar",
        "@jackson-annotations//jar",
        "@jackson-core//jar",
        "@jackson-databind//jar",
        "@jackson-dataformat-cbor//jar",
        "@reactive-streams//jar",
        "@reactor-core//jar",
        "@rxjava//jar",
    ],
)

junit_tests(
    name = "kinesis_events_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["kinesis-events"],
    deps = [
        ":kinesis-events__plugin_test_deps",
        "//lib/testcontainers",
        "@amazon-http-client-spi//jar",
        "@amazon-kinesis-client//jar",
        "@amazon-kinesis//jar",
        "@events-broker//jar",
        "@testcontainer-localstack//jar",
    ],
)

java_library(
    name = "kinesis-events__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":kinesis-events__plugin",
        "//lib/jackson:jackson-annotations",
        "//lib/testcontainers",
        "//lib/testcontainers:docker-java-api",
        "//lib/testcontainers:docker-java-transport",
        "@amazon-regions//jar",
        "@amazon-auth//jar",
        "@amazon-kinesis//jar",
        "@amazon-aws-core//jar",
        "@amazon-sdk-core//jar",
        "@amazon-profiles//jar",
        "@aws-java-sdk-core//jar",
        "@awssdk-url-connection-client//jar",
        "@amazon-dynamodb//jar",
    ],
)

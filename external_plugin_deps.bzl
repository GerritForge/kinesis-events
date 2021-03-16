load("//tools/bzl:maven_jar.bzl", "maven_jar")

AWS_SDK_VER = "2.16.19"
AWS_KINESIS_VER = "2.3.4"
JACKSON_VER = "2.12.2"

def external_plugin_deps():
    maven_jar(
        name = "junit-platform",
        artifact = "org.junit.platform:junit-platform-commons:1.4.0",
        sha1 = "34d9983705c953b97abb01e1cd04647f47272fe5",
    )

    maven_jar(
        name = "amazon-kinesis-client",
        artifact = "software.amazon.kinesis:amazon-kinesis-client:" + AWS_KINESIS_VER,
        sha1 = "6bb6fcbc5a0f6fd6085f3b1589e738485b0b7867",
    )

    maven_jar(
        name = "amazon-kinesis",
        artifact = "software.amazon.awssdk:kinesis:" + AWS_SDK_VER,
        sha1 = "bec13fc5ef9225d1a10f13fbe1de8cb114448cf8",
    )

    maven_jar(
        name = "amazon-dynamodb",
        artifact = "software.amazon.awssdk:dynamodb:" + AWS_SDK_VER,
        sha1 = "33ec7d291973658779b5777db2a0214a5c469e81",
    )

    maven_jar(
        name = "amazon-cloudwatch",
        artifact = "software.amazon.awssdk:cloudwatch:" + AWS_SDK_VER,
        sha1 = "7585fbe349a92e0a9f040e4194ac89ca32e7983d",
    )

    maven_jar(
        name = "amazon-regions",
        artifact = "software.amazon.awssdk:regions:" + AWS_SDK_VER,
        sha1 = "089f4f3d3ef20b2486f09e71da638c03100eab64",
    )

    maven_jar(
        name = "amazon-netty-nio-client",
        artifact = "software.amazon.awssdk:netty-nio-client:" + AWS_SDK_VER,
        sha1 = "bb674feda8417513a647c7aa8cba9a537068d099",
    )

    maven_jar(
        name = "amazon-utils",
        artifact = "software.amazon.awssdk:utils:" + AWS_SDK_VER,
        sha1 = "53edaa1f884682ac3091293eff3eb024ed0e36bb",
    )

    maven_jar(
        name = "amazon-sdk-core",
        artifact = "software.amazon.awssdk:sdk-core:" + AWS_SDK_VER,
        sha1 = "02a60fd9c138048272ef8b6c80ae67491dd386a9",
    )

    maven_jar(
        name = "amazon-aws-core",
        artifact = "software.amazon.awssdk:aws-core:" + AWS_SDK_VER,
        sha1 = "0f50f5cf2698a0de7d2d77322cbf3fb13f76187f",
    )

    maven_jar(
        name = "amazon-http-client-spi",
        artifact = "software.amazon.awssdk:http-client-spi:" + AWS_SDK_VER,
        sha1 = "e4027e7e0cb064602100b34e19f131983f76f872",
    )

    maven_jar(
        name = "amazon-auth",
        artifact = "software.amazon.awssdk:auth:" + AWS_SDK_VER,
        sha1 = "4163754b2a0eadcb569a35f0666fd5d859e43ef8",
    )

    maven_jar(
        name = "reactive-streams",
        artifact = "org.reactivestreams:reactive-streams:1.0.2",
        sha1 = "323964c36556eb0e6209f65c1cef72b53b461ab8",
    )

    maven_jar(
        name = "reactor-core",
        artifact = "io.projectreactor:reactor-core:3.4.3",
        sha1 = "df23dbdf95f892f7a04292d040fd8b308bd66602",
    )

    maven_jar(
        name = "rxjava",
        artifact = "io.reactivex.rxjava2:rxjava:2.1.14",
        sha1 = "20dbf7496e417da474eda12717bf4653dbbd5a6b",
    )

    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VER,
        sha1 = "8b02908d53183fdf9758e7e20f2fdee87613a962",
    )

    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VER,
        sha1 = "f083c4ac0fb8b3c6b8d5b62cd54122228ef62cee",
    )

    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VER,
        sha1 = "5f9d79e09ebf5d54a46e9f4543924cf7ae7654e0",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4-alpha-20210205083200",
        sha1 = "3fec2bfee13b9b0a2889616e3c039ead686b931f",
    )

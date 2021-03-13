load("//tools/bzl:maven_jar.bzl", "maven_jar")

AWS_SDK_VER = "2.15.14"
AWS_KINESIS_VER = "2.2.9"
JACKSON_VER = "2.10.4"

def external_plugin_deps():
    maven_jar(
        name = "junit-platform",
        artifact = "org.junit.platform:junit-platform-commons:1.4.0",
        sha1 = "34d9983705c953b97abb01e1cd04647f47272fe5",
    )

    maven_jar(
        name = "amazon-kinesis-client",
        artifact = "software.amazon.kinesis:amazon-kinesis-client:" + AWS_KINESIS_VER,
        sha1 = "da8b162cf62c2a752db22ba55ff1934cc3b7a6b5",
    )

    maven_jar(
        name = "amazon-kinesis",
        artifact = "software.amazon.awssdk:kinesis:" + AWS_SDK_VER,
        sha1 = "95242b05989de81b5d0310b17fc81eed89b2d756",
    )

    maven_jar(
        name = "amazon-dynamodb",
        artifact = "software.amazon.awssdk:dynamodb:" + AWS_SDK_VER,
        sha1 = "2349672017eabc6deb0d5a526d964ec433cb38ef",
    )

    maven_jar(
        name = "amazon-cloudwatch",
        artifact = "software.amazon.awssdk:cloudwatch:" + AWS_SDK_VER,
        sha1 = "388467d5361709b20462b20fd4165d2b356e7110",
    )

    maven_jar(
        name = "amazon-regions",
        artifact = "software.amazon.awssdk:regions:" + AWS_SDK_VER,
        sha1 = "ea4c72b80f4cf2f5024e156cd291d9c4cd854bc7",
    )

    maven_jar(
        name = "amazon-netty-nio-client",
        artifact = "software.amazon.awssdk:netty-nio-client:" + AWS_SDK_VER,
        sha1 = "7c937604636d7cfe38d2c40c5544d81a1c50b97a",
    )

    maven_jar(
        name = "amazon-utils",
        artifact = "software.amazon.awssdk:utils:" + AWS_SDK_VER,
        sha1 = "aa8c6e723a8feb964dabd6eda7c3157e1f89a53d",
    )

    maven_jar(
        name = "amazon-sdk-core",
        artifact = "software.amazon.awssdk:sdk-core:" + AWS_SDK_VER,
        sha1 = "8b595e49b1e219a4125cf69ef5a8febf60888640",
    )

    maven_jar(
        name = "amazon-aws-core",
        artifact = "software.amazon.awssdk:aws-core:" + AWS_SDK_VER,
        sha1 = "cb3c121445a45b37e79b60de97c61fcf331fdeb8",
    )

    maven_jar(
        name = "amazon-http-client-spi",
        artifact = "software.amazon.awssdk:http-client-spi:" + AWS_SDK_VER,
        sha1 = "a662919f86cce54ca7e235476ab13893ef9d4d34",
    )

    maven_jar(
        name = "amazon-auth",
        artifact = "software.amazon.awssdk:auth:" + AWS_SDK_VER,
        sha1 = "649aa29b6588496efda6ca368f4e3a9f193ba6fb",
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
        sha1 = "76e9152e93d4cf052f93a64596f633ba5b1c8ed9",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4-alpha-20210205083200",
        sha1 = "3fec2bfee13b9b0a2889616e3c039ead686b931f",
    )

// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.kinesis;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;
import software.amazon.kinesis.retrieval.RetrievalConfig;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

import java.net.URI;
import java.util.Optional;

@Singleton
class KinesisConfiguration {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DEFAULT_NUMBER_OF_SUBSCRIBERS = "6";
  private static final String DEFAULT_STREAM_EVENTS_TOPIC = "gerrit";
  private static final String DEFAULT_INITIAL_POSITION = "latest";
  private static final Long DEFAULT_POLLING_INTERVAL_MS = 1000L;
  private static final Integer DEFAULT_MAX_RECORDS = 100;

  private final String applicationName;
  private final String streamEventsTopic;
  private final int numberOfSubscribers;
  private final KinesisAsyncClient kinesisClient;
  private final DynamoDbAsyncClient dynamoClient;
  private final CloudWatchAsyncClient cloudWatchClient;
  private final InitialPositionInStream initialPosition;
  private final Optional<Region> region;
  private final Optional<URI> endpoint;
  private final Long pollingIntervalMs;
  private final Integer maxRecords;

  @Inject
  public KinesisConfiguration(PluginConfigFactory configFactory, @PluginName String pluginName) {
    PluginConfig pluginConfig = configFactory.getFromGerritConfig(pluginName);

    this.region = Optional.ofNullable(getStringParam(pluginConfig, "region", null)).map(Region::of);
    this.endpoint =
        Optional.ofNullable(getStringParam(pluginConfig, "endpoint", null)).map(URI::create);
    this.streamEventsTopic = getStringParam(pluginConfig, "topic", DEFAULT_STREAM_EVENTS_TOPIC);
    this.numberOfSubscribers =
        Integer.parseInt(
            getStringParam(pluginConfig, "numberOfSubscribers", DEFAULT_NUMBER_OF_SUBSCRIBERS));
    this.applicationName = getStringParam(pluginConfig, "applicationName", pluginName);

    this.initialPosition =
        InitialPositionInStream.valueOf(
            getStringParam(pluginConfig, "initialPosition", DEFAULT_INITIAL_POSITION)
                .toUpperCase());

    this.pollingIntervalMs =
        Optional.ofNullable(getStringParam(pluginConfig, "pollingIntervalMs", null))
            .map(Long::parseLong)
            .orElse(DEFAULT_POLLING_INTERVAL_MS);

    this.maxRecords =
        Optional.ofNullable(getStringParam(pluginConfig, "maxRecords", null))
            .map(Integer::parseInt)
            .orElse(DEFAULT_MAX_RECORDS);

    this.kinesisClient =
        KinesisClientUtil.createKinesisAsyncClient(configureBuilder(KinesisAsyncClient.builder()));
    this.dynamoClient = configureBuilder(DynamoDbAsyncClient.builder()).build();
    this.cloudWatchClient = configureBuilder(CloudWatchAsyncClient.builder()).build();

    logger.atInfo().log(
        "Kinesis client. Application:'%s'|PollingInterval: %s|maxRecords: %s%s%s",
        applicationName,
        pollingIntervalMs,
        maxRecords,
        region.map(r -> String.format("|region: %s", r.id())).orElse(""),
        endpoint.map(e -> String.format("|endpoint: %s", e.toASCIIString())).orElse(""));
  }

  public String getStreamEventsTopic() {
    return streamEventsTopic;
  }

  public int getNumberOfSubscribers() {
    return numberOfSubscribers;
  }

  public KinesisAsyncClient getKinesisClient() {
    return kinesisClient;
  }

  public ConfigsBuilder createConfigBuilder(
      String streamName, ShardRecordProcessorFactory processorFactory) {
    return new ConfigsBuilder(
        streamName,
        cosumerLeaseName(applicationName, streamName),
        kinesisClient,
        dynamoClient,
        cloudWatchClient,
        String.format("klc-worker-%s-%s", applicationName, streamName),
        processorFactory);
  }

  public RetrievalConfig getRetrievalConfig(
      ConfigsBuilder configsBuilder, String streamName, boolean fromBeginning) {
    PollingConfig polling =
        new PollingConfig(streamName, kinesisClient)
            .idleTimeBetweenReadsInMillis(pollingIntervalMs)
            .maxRecords(maxRecords);
    RetrievalConfig retrievalConfig =
        configsBuilder.retrievalConfig().retrievalSpecificConfig(polling);
    retrievalConfig.initialPositionInStreamExtended(
        InitialPositionInStreamExtended.newInitialPosition(
            fromBeginning ? InitialPositionInStream.TRIM_HORIZON : initialPosition));
    return retrievalConfig;
  }

  private static String getStringParam(
      PluginConfig pluginConfig, String name, String defaultValue) {
    return Strings.isNullOrEmpty(System.getProperty(name))
        ? pluginConfig.getString(name, defaultValue)
        : System.getProperty(name);
  }

  public static String cosumerLeaseName(String applicationName, String streamName) {
    return String.format("%s-%s", applicationName, streamName);
  }

  private <T extends AwsClientBuilder<?, ?>> T configureBuilder(T builder) {
    region.ifPresent(builder::region);
    endpoint.ifPresent(builder::endpointOverride);
    return builder;
  }
}

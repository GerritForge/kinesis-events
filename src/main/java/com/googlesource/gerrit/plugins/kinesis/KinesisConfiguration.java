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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClientBuilder;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;
import software.amazon.kinesis.retrieval.RetrievalConfig;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

@Singleton
public class KinesisConfiguration {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DEFAULT_REGION = "us-east-2";
  private static final String DEFAULT_NUMBER_OF_SUBSCRIBERS = "6";

  private final String applicationName;
  private final String streamEventsTopic;
  private final int numberOfSubscribers;
  private final KinesisAsyncClient kinesisClient;
  private final DynamoDbAsyncClient dynamoClient;
  private final CloudWatchAsyncClient cloudWatchClient;

  @Inject
  public KinesisConfiguration(PluginConfigFactory configFactory, @PluginName String pluginName) {
    PluginConfig fromGerritConfig = configFactory.getFromGerritConfig(pluginName);

    Region region = Region.of(fromGerritConfig.getString("region", DEFAULT_REGION));
    Optional<URI> endpoint =
        Optional.ofNullable(fromGerritConfig.getString("endpoint")).map(URI::create);
    this.streamEventsTopic = fromGerritConfig.getString("topic", "gerrit");
    this.numberOfSubscribers =
        Integer.parseInt(
            fromGerritConfig.getString("numberOfSubscribers", DEFAULT_NUMBER_OF_SUBSCRIBERS));
    this.applicationName = fromGerritConfig.getString("applicationName", pluginName);

    KinesisAsyncClientBuilder kinesisBuilder = KinesisAsyncClient.builder().region(region);
    endpoint.ifPresent(kinesisBuilder::endpointOverride);

    this.kinesisClient = KinesisClientUtil.createKinesisAsyncClient(kinesisBuilder);

    DynamoDbAsyncClientBuilder dynamoDBBuilder = DynamoDbAsyncClient.builder().region(region);
    endpoint.ifPresent(dynamoDBBuilder::endpointOverride);
    this.dynamoClient = dynamoDBBuilder.build();

    final CloudWatchAsyncClientBuilder cloudWatchBuilder =
        CloudWatchAsyncClient.builder().region(region);
    endpoint.ifPresent(cloudWatchBuilder::endpointOverride);
    this.cloudWatchClient = cloudWatchBuilder.build();

    logger.atInfo().log(
        "Created Kinesis Client for application %s for region '%s'.%s",
        applicationName,
        region,
        endpoint.map(e -> String.format(" [endpoint: %s]", e.toASCIIString())).orElse(""));
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
        applicationName,
        kinesisClient,
        dynamoClient,
        cloudWatchClient,
        UUID.randomUUID().toString(),
        processorFactory);
  }

  public RetrievalConfig getRetrievalConfig(
      ConfigsBuilder configsBuilder, String streamName, boolean fromBeginning) {
    // TODO: These should be configured

    PollingConfig polling =
        new PollingConfig(streamName, kinesisClient)
            .idleTimeBetweenReadsInMillis(300)
            .maxRecords(100);
    RetrievalConfig retrievalConfig =
        configsBuilder.retrievalConfig().retrievalSpecificConfig(polling);
    if (fromBeginning) {
      InitialPositionInStreamExtended initialPositionInStreamExtended =
          InitialPositionInStreamExtended.newInitialPosition(InitialPositionInStream.TRIM_HORIZON);
      retrievalConfig.initialPositionInStreamExtended(initialPositionInStreamExtended);
    }
    return retrievalConfig;
  }
}

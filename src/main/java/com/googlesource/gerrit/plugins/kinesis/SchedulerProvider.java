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

import static com.googlesource.gerrit.plugins.kinesis.KinesisConfiguration.cosumerLeaseName;

import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.retrieval.RetrievalConfig;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

class SchedulerProvider implements Provider<Scheduler> {
  interface Factory {
    SchedulerProvider create(
        String streamName,
        boolean fromBeginning,
        java.util.function.Consumer<EventMessage> messageProcessor);
  }

  private final ConfigsBuilder configsBuilder;
  private final KinesisConfiguration kinesisConfiguration;
  private final KinesisAsyncClientProvider kinesisAsyncClientProvider;
  private final String streamName;
  private final boolean fromBeginning;

  @AssistedInject
  SchedulerProvider(
      KinesisConfiguration kinesisConfiguration,
      KinesisAsyncClientProvider kinesisAsyncClientProvider,
      DynamoDbAsyncClientProvider dynamoDbAsyncClientProvider,
      CloudWatchAsyncClientProvider cloudWatchAsyncClientProvider,
      KinesisRecordProcessorFactory.Factory kinesisRecordProcessorFactory,
      @Assisted String streamName,
      @Assisted boolean fromBeginning,
      @Assisted java.util.function.Consumer<EventMessage> messageProcessor) {
    this.kinesisConfiguration = kinesisConfiguration;
    this.kinesisAsyncClientProvider = kinesisAsyncClientProvider;
    this.streamName = streamName;
    this.fromBeginning = fromBeginning;
    this.configsBuilder =
        new ConfigsBuilder(
            streamName,
            cosumerLeaseName(kinesisConfiguration.getApplicationName(), streamName),
            kinesisAsyncClientProvider.get(),
            dynamoDbAsyncClientProvider.get(),
            cloudWatchAsyncClientProvider.get(),
            String.format(
                "klc-worker-%s-%s", kinesisConfiguration.getApplicationName(), streamName),
            kinesisRecordProcessorFactory.create(messageProcessor));
  }

  private RetrievalConfig getRetrievalConfig() {
    PollingConfig polling =
        new PollingConfig(streamName, kinesisAsyncClientProvider.get())
            .idleTimeBetweenReadsInMillis(kinesisConfiguration.getPollingIntervalMs())
            .maxRecords(kinesisConfiguration.getMaxRecords());
    RetrievalConfig retrievalConfig =
        configsBuilder.retrievalConfig().retrievalSpecificConfig(polling);
    retrievalConfig.initialPositionInStreamExtended(
        InitialPositionInStreamExtended.newInitialPosition(
            fromBeginning
                ? InitialPositionInStream.TRIM_HORIZON
                : kinesisConfiguration.getInitialPosition()));
    return retrievalConfig;
  }

  @Override
  public Scheduler get() {
    return new Scheduler(
        configsBuilder.checkpointConfig(),
        configsBuilder.coordinatorConfig(),
        configsBuilder.leaseManagementConfig(),
        configsBuilder.lifecycleConfig(),
        configsBuilder.metricsConfig(),
        configsBuilder.processorConfig(),
        getRetrievalConfig());
  }
}

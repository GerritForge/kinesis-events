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

import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.coordinator.Scheduler;

class KinesisConsumer {
  interface Factory {
    KinesisConsumer create(String topic, Consumer<EventMessage> messageProcessor);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final KinesisConfiguration kinesisConfiguration;
  private final KinesisRecordProcessorFactory.Factory kinesisRecordProcessorFactory;
  private final ExecutorService executor;
  private ConfigsBuilder configsBuilder;
  private Scheduler kinesisScheduler;

  private java.util.function.Consumer<EventMessage> messageProcessor;
  private String streamName;
  private AtomicBoolean resetOffset = new AtomicBoolean(false);

  @Inject
  public KinesisConsumer(
      KinesisConfiguration kinesisConfiguration,
      KinesisRecordProcessorFactory.Factory kinesisRecordProcessorFactory,
      @ConsumerExecutor ExecutorService executor) {
    this.kinesisConfiguration = kinesisConfiguration;
    this.kinesisRecordProcessorFactory = kinesisRecordProcessorFactory;
    this.executor = executor;
  }

  public void subscribe(
      String streamName, java.util.function.Consumer<EventMessage> messageProcessor) {
    this.streamName = streamName;
    this.messageProcessor = messageProcessor;

    configsBuilder =
        kinesisConfiguration.createConfigBuilder(
            streamName, kinesisRecordProcessorFactory.create(messageProcessor));

    logger.atInfo().log("Subscribe kinesis consumer to stream [%s]", streamName);
    runReceiver();
  }

  private void runReceiver() {
    this.kinesisScheduler =
        new Scheduler(
            configsBuilder.checkpointConfig(),
            configsBuilder.coordinatorConfig(),
            configsBuilder.leaseManagementConfig(),
            configsBuilder.lifecycleConfig(),
            configsBuilder.metricsConfig(),
            configsBuilder.processorConfig(),
            kinesisConfiguration.getRetrievalConfig(
                configsBuilder, streamName, resetOffset.getAndSet(false)));
    executor.execute(kinesisScheduler);
  }

  public void shutdown() {
    Future<Boolean> gracefulShutdownFuture = kinesisScheduler.startGracefulShutdown();
    logger.atInfo().log("Waiting up to %s seconds for shutdown to complete.");
    try {
      gracefulShutdownFuture.get(20, TimeUnit.SECONDS);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Error caught when shutting down kinesis consumer for stream %s", getStreamName());
    }
    logger.atInfo().log("Completed, shutting down now.");
  }

  public java.util.function.Consumer<EventMessage> getMessageProcessor() {
    return messageProcessor;
  }

  public String getStreamName() {
    return streamName;
  }

  public void resetOffset() {
    resetOffset.set(true);
  }
}

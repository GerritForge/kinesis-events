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
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.log4j.MDC;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;
import software.amazon.kinesis.retrieval.RetrievalConfig;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

public class KinesisConsumer {

  public interface Factory {
    KinesisConsumer create(String topic, Consumer<EventMessage> messageProcessor);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  protected final Gson gson;
  private final KinesisConfiguration kinesisConfiguration;
  private final OneOffRequestContext oneOffCtx;
  private final ExecutorService executor;
  private ConfigsBuilder configsBuilder;
  private Scheduler kinesisScheduler;

  private java.util.function.Consumer<EventMessage> messageProcessor;
  private String streamName;
  private AtomicBoolean resetOffset = new AtomicBoolean(false);

  @Inject
  public KinesisConsumer(
      Gson gson,
      KinesisConfiguration kinesisConfiguration,
      OneOffRequestContext oneOffCtx,
      @ConsumerExecutor ExecutorService executor) {
    this.gson = gson;
    this.kinesisConfiguration = kinesisConfiguration;
    this.oneOffCtx = oneOffCtx;
    this.executor = executor;
  }

  public void subscribe(
      String streamName, java.util.function.Consumer<EventMessage> messageProcessor) {
    this.streamName = streamName;
    this.messageProcessor = messageProcessor;

    configsBuilder =
        kinesisConfiguration.createConfigBuilder(
            streamName, new KinesisRecordProcessorFactory(messageProcessor, oneOffCtx, gson));

    logger.atInfo().log("Kinesis consumer subscribing to stream [%s]", streamName);
    runReceiver();
  }

  private void runReceiver() {
    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(KinesisConsumer.class.getClassLoader());
      this.kinesisScheduler =
          new Scheduler(
              configsBuilder.checkpointConfig(),
              configsBuilder.coordinatorConfig(),
              configsBuilder.leaseManagementConfig(),
              configsBuilder.lifecycleConfig(),
              configsBuilder.metricsConfig(),
              configsBuilder.processorConfig(),
              setRetrievalConfig());
      executor.execute(kinesisScheduler);
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
  }

  public void shutdown() {
    Future<Boolean> gracefulShutdownFuture = kinesisScheduler.startGracefulShutdown();
    logger.atInfo().log("Waiting up to %s seconds for shutdown to complete.");
    try {
      gracefulShutdownFuture.get(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.atWarning().log("Interrupted while waiting for graceful shutdown. Continuing.");
    } catch (ExecutionException e) {
      logger.atWarning().log("Exception while executing graceful shutdown.", e);
    } catch (TimeoutException e) {
      logger.atSevere().log("Timeout while waiting for shutdown. Scheduler may not have exited.");
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

  private RetrievalConfig setRetrievalConfig() {
    RetrievalConfig retrievalConfig =
        configsBuilder
            .retrievalConfig()
            .retrievalSpecificConfig(
                new PollingConfig(streamName, kinesisConfiguration.getKinesisClient()));
    if (resetOffset.getAndSet(false)) {
      InitialPositionInStreamExtended initialPositionInStreamExtended =
          InitialPositionInStreamExtended.newInitialPosition(InitialPositionInStream.TRIM_HORIZON);
      retrievalConfig.initialPositionInStreamExtended(initialPositionInStreamExtended);
    }
    return retrievalConfig;
  }

  private static class KinesisRecordProcessorFactory implements ShardRecordProcessorFactory {

    private final Consumer<EventMessage> recordProcessor;
    private final OneOffRequestContext oneOffCtx;
    private final Gson gson;

    public KinesisRecordProcessorFactory(
        Consumer<EventMessage> recordProcessor, OneOffRequestContext oneOffCtx, Gson gson) {
      this.recordProcessor = recordProcessor;
      this.oneOffCtx = oneOffCtx;
      this.gson = gson;
    }

    public ShardRecordProcessor shardRecordProcessor() {
      return new KinesisRecordProcessor(recordProcessor, oneOffCtx, gson);
    }
  }

  private static class KinesisRecordProcessor implements ShardRecordProcessor {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private static final String SHARD_ID_MDC_KEY = "ShardId";
    private final Consumer<EventMessage> recordProcessor;
    private final OneOffRequestContext oneOffCtx;
    private final Gson gson;
    private String shardId;

    public KinesisRecordProcessor(
        Consumer<EventMessage> recordProcessor, OneOffRequestContext oneOffCtx, Gson gson) {
      this.recordProcessor = recordProcessor;
      this.oneOffCtx = oneOffCtx;
      this.gson = gson;
    }

    @Override
    public void initialize(InitializationInput initializationInput) {
      shardId = initializationInput.shardId();
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        logger.atInfo().log(
            "Initializing @ Sequence: %s", initializationInput.extendedSequenceNumber());
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    @Override
    public void processRecords(ProcessRecordsInput processRecordsInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        logger.atInfo().log("Processing %s record(s)", processRecordsInput.records().size());
        processRecordsInput
            .records()
            .forEach(
                consumerRecord -> {
                  try (ManualRequestContext ctx = oneOffCtx.open()) {
                    EventMessage eventMessage =
                        gson.fromJson(
                            new String(consumerRecord.data().array()), EventMessage.class);
                    recordProcessor.accept(eventMessage);
                  } catch (Exception e) {
                    logger.atSevere().withCause(e).log(
                        "Malformed event '%s'", new String(consumerRecord.data().array()));
                  }
                });
      } catch (Throwable t) {
        logger.atSevere().withCause(t).log("Caught throwable while processing records. Aborting.");
        Runtime.getRuntime().halt(1);
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    @Override
    public void leaseLost(LeaseLostInput leaseLostInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        logger.atInfo().log("Lost lease, so terminating.");
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    @Override
    public void shardEnded(ShardEndedInput shardEndedInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        logger.atInfo().log("Reached shard end checkpointing.");
        shardEndedInput.checkpointer().checkpoint();
      } catch (ShutdownException | InvalidStateException e) {
        logger.atSevere().withCause(e).log(
            "Exception while checkpointing at shard end. Giving up.");
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }

    @Override
    public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
      MDC.put(SHARD_ID_MDC_KEY, shardId);
      try {
        logger.atInfo().log("Scheduler is shutting down, checkpointing.");
        shutdownRequestedInput.checkpointer().checkpoint();
      } catch (ShutdownException | InvalidStateException e) {
        logger.atSevere().withCause(e).log(
            "Exception while checkpointing at requested shutdown. Giving up.");
      } finally {
        MDC.remove(SHARD_ID_MDC_KEY);
      }
    }
  }
}

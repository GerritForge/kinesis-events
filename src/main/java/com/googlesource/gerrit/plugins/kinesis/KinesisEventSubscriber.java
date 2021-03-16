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
import org.apache.log4j.MDC;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.MoreObjects.firstNonNull;

public class KinesisEventSubscriber {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final KinesisAsyncClient kinesisClient;
  private final Region region;
  private ConfigsBuilder configsBuilder;

  private java.util.function.Consumer<EventMessage> messageProcessor;
  private String streamName;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private AtomicBoolean resetOffset = new AtomicBoolean(false);

  @Inject
  public KinesisEventSubscriber(KinesisSubscriberConfiguration configuration) {
    this.region = Region.of(firstNonNull("TODO config region", "us-east-2"));
    this.kinesisClient =
        KinesisClientUtil.createKinesisAsyncClient(
            KinesisAsyncClient.builder().region(this.region));
  }

  public void subscribe(
      String streamName, java.util.function.Consumer<EventMessage> messageProcessor) {
    this.streamName = streamName;
    this.messageProcessor = messageProcessor;
    DynamoDbAsyncClient dynamoClient = DynamoDbAsyncClient.builder().region(region).build();
    CloudWatchAsyncClient cloudWatchClient = CloudWatchAsyncClient.builder().region(region).build();
    configsBuilder =
        new ConfigsBuilder(
            streamName,
            streamName,
            kinesisClient,
            dynamoClient,
            cloudWatchClient,
            UUID.randomUUID().toString(),
            new KinesisRecordProcessorFactory());
    logger.atInfo().log(
        "Kinesis consumer subscribing to streamName alias [%s] for event streamName [%s]",
        streamName, streamName);
    runReceiver();
  }

  private void runReceiver() {
    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(KinesisEventSubscriber.class.getClassLoader());
      //      new Scheduler()
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
  }

  public void shutdown() {
    closed.set(true);
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

  private static class KinesisRecordProcessorFactory implements ShardRecordProcessorFactory {
    public ShardRecordProcessor shardRecordProcessor() {
      return new KinesisRecordProcessor();
    }
  }

  private static class KinesisRecordProcessor implements ShardRecordProcessor {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private static final String SHARD_ID_MDC_KEY = "ShardId";
    private String shardId;

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
                r ->
                    logger.atInfo().log(
                        "Processing record pk: %s -- Seq: %s",
                        r.partitionKey(), r.sequenceNumber()));
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

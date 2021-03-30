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

import com.amazonaws.services.kinesis.producer.Attempt;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
class KinesisPublisher implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KinesisProducer kinesisProducer;
  private final KinesisConfiguration kinesisConfiguration;

  private final Gson gson;

  @Inject
  public KinesisPublisher(
      Gson gson, KinesisProducer kinesisProducer, KinesisConfiguration kinesisConfiguration) {
    this.gson = gson;
    this.kinesisProducer = kinesisProducer;
    this.kinesisConfiguration = kinesisConfiguration;
  }

  @Override
  public void onEvent(Event event) {
    publish(kinesisConfiguration.getStreamEventsTopic(), gson.toJson(event), event.getType());
  }

  public PublishResult publish(String streamName, String stringEvent, String partitionKey) {
    logger.atFiner().log(
        "KINESIS PRODUCER - Attempt to publish event %s to stream %s [PK: %s]",
        stringEvent, streamName, partitionKey);

    UserRecordResult result = null;
    try {
      result =
          kinesisProducer
              .addUserRecord(streamName, partitionKey, ByteBuffer.wrap(stringEvent.getBytes()))
              .get(kinesisConfiguration.getPublishTimeoutMs(), TimeUnit.MILLISECONDS);

      List<Attempt> attemptsDetails = result.getAttempts();
      int numberOfAttempts = attemptsDetails.size();
      if (result.isSuccessful()) {
        logger.atFine().log(
            "KINESIS PRODUCER - Successfully published event '%s' to shardId '%s' [PK: %s] [Sequence: %s] after %s attempt(s)",
            stringEvent,
            result.getShardId(),
            partitionKey,
            result.getSequenceNumber(),
            numberOfAttempts);
        return PublishResult.success(numberOfAttempts);
      } else {
        int currentIdx = numberOfAttempts - 1;
        int previousIdx = currentIdx - 1;
        Attempt current = attemptsDetails.get(currentIdx);
        if (previousIdx >= 0) {
          Attempt previous = attemptsDetails.get(previousIdx);
          logger.atSevere().log(
              String.format(
                  "KINESIS PRODUCER - Failed publishing event '%s' [PK: %s] - %s : %s. Previous failure - %s : %s",
                  stringEvent,
                  partitionKey,
                  current.getErrorCode(),
                  current.getErrorMessage(),
                  previous.getErrorCode(),
                  previous.getErrorMessage()));
        } else {
          logger.atSevere().log(
              String.format(
                  "KINESIS PRODUCER - Failed publishing event '%s' [PK: %s] - %s : %s.",
                  stringEvent, partitionKey, current.getErrorCode(), current.getErrorMessage()));
        }
      }
    } catch (InterruptedException e) {
      logger.atSevere().withCause(e).log(
          String.format(
              "KINESIS PRODUCER - Interrupted publishing event '%s' [PK: %s]",
              stringEvent, partitionKey));
    } catch (ExecutionException e) {
      logger.atSevere().withCause(e).log(
          String.format(
              "KINESIS PRODUCER - Error when publishing event '%s' [PK: %s]",
              stringEvent, partitionKey));
    } catch (TimeoutException e) {
      logger.atSevere().withCause(e).log(
          String.format(
              "KINESIS PRODUCER - Timeout when publishing event '%s' [PK: %s]",
              stringEvent, partitionKey));
    }

    return PublishResult.failure(
        Optional.ofNullable(result).map(r -> r.getAttempts().size()).orElse(0));
  }
}

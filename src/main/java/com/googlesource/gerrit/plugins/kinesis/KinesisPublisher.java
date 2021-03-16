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
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import org.apache.commons.codec.binary.Base64;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

@Singleton
class KinesisPublisher implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KinesisConfiguration kinesisConfiguration;

  private final Gson gson;

  @Inject
  public KinesisPublisher(Gson gson, KinesisConfiguration kinesisConfiguration) {
    this.gson = gson;
    this.kinesisConfiguration = kinesisConfiguration;
  }

  @Override
  public void onEvent(Event event) {
    publish(kinesisConfiguration.getStreamEventsTopic(), gson.toJson(event), event.getType());
  }

  public boolean publish(String streamName, String stringEvent, String partitionKey) {
    PutRecordRequest request =
        PutRecordRequest.builder()
            .partitionKey(partitionKey)
            .streamName(streamName)
            .data(SdkBytes.fromByteArray(Base64.encodeBase64(stringEvent.getBytes())))
            .build();
    try {
      logger.atInfo().log(
          "KINESIS PUBLISH event %s to stream %s [PK: %s]", stringEvent, streamName, partitionKey);
      kinesisConfiguration.getKinesisClient().putRecord(request).get();
      return true;
    } catch (InterruptedException e) {
      logger.atInfo().log(
          String.format(
              "Interrupted while publishing event %s to stream %s. Assuming shutdown.",
              stringEvent, streamName));
    } catch (ExecutionException e) {
      logger.atSevere().withCause(e).log(
          String.format(
              "Execution exception when publishing event %s to stream %s",
              stringEvent, streamName));
    }
    return false;
  }
}

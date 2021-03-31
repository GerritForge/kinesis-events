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

import static com.google.common.truth.Truth.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.CLOUDWATCH;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.KINESIS;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.WaitUtil;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.StreamStatus;

@TestPlugin(name = "kinesis-events", sysModule = "com.googlesource.gerrit.plugins.kinesis.Module")
public class KinesisEventsIT extends LightweightPluginDaemonTest {
  // This timeout is quite high to allow the kinesis coordinator to acquire a
  // lease on the newly created stream
  private static final Duration WAIT_FOR_CONSUMPTION = Duration.ofSeconds(120);
  private static final Duration STREAM_CREATION_TIMEOUT = Duration.ofSeconds(10);

  private static final int LOCALSTACK_PORT = 4566;
  private LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.12.8"))
          .withServices(DYNAMODB, KINESIS, CLOUDWATCH)
          .withExposedPorts(LOCALSTACK_PORT);

  private KinesisClient kinesisClient;

  @Before
  public void setUpTestPlugin() throws Exception {
    localstack.start();

    kinesisClient =
        KinesisClient.builder()
            .endpointOverride(localstack.getEndpointOverride(KINESIS))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .build();

    System.setProperty("endpoint", localstack.getEndpointOverride(KINESIS).toASCIIString());
    System.setProperty("region", localstack.getRegion());
    System.setProperty("aws.accessKeyId", localstack.getAccessKey());
    System.setProperty("aws.secretAccessKey", localstack.getSecretKey());

    super.setUpTestPlugin();
  }

  @Override
  public void tearDownTestPlugin() {
    localstack.close();

    super.tearDownTestPlugin();
  }

  @Test
  @GerritConfig(name = "plugin.kinesis-events.applicationName", value = "test-consumer")
  @GerritConfig(name = "plugin.kinesis-events.initialPosition", value = "trim_horizon")
  public void shouldConsumeAnEventPublishedToATopic() throws Exception {
    String streamName = UUID.randomUUID().toString();
    createKinesisStream(streamName, STREAM_CREATION_TIMEOUT);

    List<EventMessage> consumedMessages = new ArrayList<>();
    kinesisBroker().receiveAsync(streamName, consumedMessages::add);

    kinesisBroker().send(streamName, eventMessage());
    WaitUtil.waitUntil(() -> consumedMessages.size() > 0, WAIT_FOR_CONSUMPTION);
  }

  @Test
  @GerritConfig(name = "plugin.kinesis-events.applicationName", value = "test-consumer")
  @GerritConfig(name = "plugin.kinesis-events.initialPosition", value = "trim_horizon")
  public void shouldReplayMessages() throws Exception {
    String streamName = UUID.randomUUID().toString();
    createKinesisStream(streamName, STREAM_CREATION_TIMEOUT);

    EventConsumerCounter eventConsumerCounter = new EventConsumerCounter();
    kinesisBroker().receiveAsync(streamName, eventConsumerCounter);

    EventMessage event = eventMessage();
    kinesisBroker().send(streamName, event);

    WaitUtil.waitUntil(
        () -> eventConsumerCounter.getConsumedMessages().size() == 1, WAIT_FOR_CONSUMPTION);
    assertThat(eventConsumerCounter.getConsumedMessages().get(0).getHeader().eventId)
        .isEqualTo(event.getHeader().eventId);

    eventConsumerCounter.clear();
    kinesisBroker().disconnect();
    kinesisBroker().receiveAsync(streamName, eventConsumerCounter);
    kinesisBroker().replayAllEvents(streamName);

    WaitUtil.waitUntil(
        () -> eventConsumerCounter.getConsumedMessages().size() == 1, WAIT_FOR_CONSUMPTION);
    assertThat(eventConsumerCounter.getConsumedMessages().get(0).getHeader().eventId)
        .isEqualTo(event.getHeader().eventId);
  }

  public KinesisBrokerApi kinesisBroker() {
    return (KinesisBrokerApi) plugin.getSysInjector().getInstance(BrokerApi.class);
  }

  private void createKinesisStream(String streamName, Duration timeout)
      throws InterruptedException {
    kinesisClient.createStream(
        CreateStreamRequest.builder().streamName(streamName).shardCount(1).build());

    WaitUtil.waitUntil(
        () ->
            kinesisClient
                .describeStream(DescribeStreamRequest.builder().streamName(streamName).build())
                .streamDescription()
                .streamStatus()
                .equals(StreamStatus.ACTIVE),
        timeout);
  }

  private EventMessage eventMessage() {
    return new EventMessage(
        new EventMessage.Header(UUID.randomUUID(), UUID.randomUUID()), new ProjectCreatedEvent());
  }

  private class EventConsumerCounter implements Consumer<EventMessage> {
    List<EventMessage> consumedMessages = new ArrayList<>();

    @Override
    public void accept(EventMessage eventMessage) {
      consumedMessages.add(eventMessage);
    }

    public List<EventMessage> getConsumedMessages() {
      return consumedMessages;
    }

    public void clear() {
      consumedMessages.clear();
    }
  }
}

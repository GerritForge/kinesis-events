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

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KinesisBrokerApi implements BrokerApi {

  private final KinesisConsumer.Factory consumerFactory;

  private final List<KinesisConsumer> consumers;
  private final Gson gson;
  private final KinesisPublisher kinesisPublisher;

  @Inject
  public KinesisBrokerApi(
      Gson gson, KinesisPublisher kinesisPublisher, KinesisConsumer.Factory consumerFactory) {
    this.gson = gson;
    this.kinesisPublisher = kinesisPublisher;
    this.consumerFactory = consumerFactory;
    this.consumers = new ArrayList<>();
  }

  @Override
  public boolean send(String streamName, EventMessage event) {
    return kinesisPublisher.publish(
        streamName, gson.toJson(event), event.getHeader().sourceInstanceId.toString());
  }

  @Override
  public void receiveAsync(String topic, Consumer<EventMessage> eventConsumer) {
    KinesisConsumer consumer = consumerFactory.create(topic, eventConsumer);
    synchronized (consumers) {
      consumers.add(consumer);
    }
    consumer.subscribe(topic, eventConsumer);
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return consumers.stream()
        .map(s -> TopicSubscriber.topicSubscriber(s.getStreamName(), s.getMessageProcessor()))
        .collect(Collectors.toSet());
  }

  @Override
  public void disconnect() {
    consumers.forEach(KinesisConsumer::shutdown);
    consumers.clear();
  }

  @Override
  public void replayAllEvents(String topic) {
    consumers.stream()
        .filter(subscriber -> topic.equals(subscriber.getStreamName()))
        .forEach(KinesisConsumer::resetOffset);
  }
}

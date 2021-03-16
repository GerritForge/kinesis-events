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
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KinesisBrokerApi implements BrokerApi {

  private final List<KinesisEventSubscriber> subscribers;
  private final Provider<KinesisEventSubscriber> subscriberProvider;
  private final KinesisPublisher publisher;

  @Inject
  public KinesisBrokerApi(
      KinesisPublisher publisher, Provider<KinesisEventSubscriber> subscriberProvider) {
    this.publisher = publisher;
    this.subscriberProvider = subscriberProvider;
    this.subscribers = new ArrayList<>();
  }

  @Override
  public boolean send(String topic, EventMessage event) {
      return publisher.publish(topic, event);
  }

  @Override
  public void receiveAsync(String topic, Consumer<EventMessage> eventConsumer) {
    KinesisEventSubscriber subscriber = subscriberProvider.get();
    synchronized (subscribers) {
      subscribers.add(subscriber);
    }
    subscriber.subscribe(topic, eventConsumer);
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return subscribers.stream()
        .map(s -> TopicSubscriber.topicSubscriber(s.getStreamName(), s.getMessageProcessor()))
        .collect(Collectors.toSet());
  }

  @Override
  public void disconnect() {
    subscribers.forEach(KinesisEventSubscriber::shutdown);
    subscribers.clear();
  }

  @Override
  public void replayAllEvents(String topic) {
    subscribers.stream()
        .filter(subscriber -> topic.equals(subscriber.getStreamName()))
        .forEach(KinesisEventSubscriber::resetOffset);
  }
}

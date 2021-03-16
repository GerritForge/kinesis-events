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
import com.gerritforge.gerrit.eventbroker.EventGsonProvider;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class Module extends LifecycleModule {

  private final WorkQueue workQueue;
  private final KinesisConfiguration configuration;

  private Set<TopicSubscriber> activeConsumers = Sets.newHashSet();

  @Inject
  public Module(WorkQueue workQueue, KinesisConfiguration configuration) {
    this.workQueue = workQueue;
    this.configuration = configuration;
  }

  // TODO: Is this needed? By whom?
  @Inject(optional = true)
  public void setPreviousBrokerApi(DynamicItem<BrokerApi> previousBrokerApi) {
    if (previousBrokerApi != null && previousBrokerApi.get() != null) {
      this.activeConsumers = previousBrokerApi.get().topicSubscribers();
    }
  }

  @Override
  protected void configure() {
    factory(KinesisRecordProcessor.Factory.class);
    factory(KinesisRecordProcessorFactory.Factory.class);
    bind(ExecutorService.class)
        .annotatedWith(ConsumerExecutor.class)
        .toInstance(
            workQueue.createQueue(configuration.getNumberOfSubscribers(), "kinesis-consumers"));

    bind(new TypeLiteral<Set<TopicSubscriber>>() {}).toInstance(activeConsumers);
    DynamicItem.bind(binder(), BrokerApi.class).to(KinesisBrokerApi.class).in(Scopes.SINGLETON);
    bind(Gson.class).toProvider(EventGsonProvider.class).in(Singleton.class);
    DynamicSet.bind(binder(), LifecycleListener.class).to(Manager.class);
    factory(KinesisConsumer.Factory.class);
    DynamicSet.bind(binder(), EventListener.class).to(KinesisPublisher.class);
  }
}

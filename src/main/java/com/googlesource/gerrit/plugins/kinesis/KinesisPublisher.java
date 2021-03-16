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
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class KinesisPublisher implements EventListener {

  private final Gson gson;

  @Inject
  public KinesisPublisher(Gson gson) {
    this.gson = gson;
  }

  @Override
  public void onEvent(Event event) {
    // session.publish(gson.toJson(event));
  }

  public boolean publish(String topic, EventMessage event) {
    //
    return false;
  }

  private String getPayload(EventMessage event) {
    return gson.toJson(event);
  }
}

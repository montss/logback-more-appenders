/*
 * Copyright (c) 2018 sndyuk <sanada@sndyuk.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.qos.logback.more.appenders;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Collects events and sends them in batches, at most once per interval.
 *
 * @param <E> the type of the logged events
 * @param <R> the type of the records sent
 */
public class IntervalEmitter<E, R> {
  private volatile long lastEmit = -1;
  private long maxInterval;
  private Timer timer;
  private volatile List<R> events;
  private final EventMapper<E, R> eventMapper;
  private final IntervalAppender<R> appender;

  IntervalEmitter(long maxInterval, EventMapper<E, R> eventMapper, IntervalAppender<R> appender) {
    this.events = new ArrayList<R>();
    this.maxInterval = maxInterval;
    this.eventMapper = eventMapper;
    this.appender = appender;
    this.timer = new Timer();
    this.timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            IntervalEmitter.this.append(null);
          }
        },
        this.maxInterval,
        this.maxInterval);
  }

  void append(E event) {
    long now = System.currentTimeMillis();
    if (now > lastEmit + maxInterval) {
      synchronized (this) {
        List<R> copiedEvents = new ArrayList<>(this.events);
        this.events = new ArrayList<>(copiedEvents.size() + 3);
        if (event != null) {
          copiedEvents.add(this.eventMapper.map(event));
        }
        if (emit(copiedEvents)) {
          lastEmit = now;
        }
      }
    } else {
      if (event != null) {
        events.add(eventMapper.map(event));
      }
    }
  }

  /**
   * Sends the events. If sending fails, they are put back to be sent with the next batch.
   *
   * @param copiedEvents the events to send
   * @return true if the events were sent or there were none, false otherwise
   */
  public boolean emit(List<R> copiedEvents) {
    if (!copiedEvents.isEmpty()) {
      if (appender.append(copiedEvents)) {
        return true;
      }
      events.addAll(0, copiedEvents);
      return false;
    }
    return true;
  }

  /**
   * Sends the remaining events, trying again when sending fails.
   *
   * @param waitMillis how long to wait between tries, in milliseconds
   * @param retry how many times to try
   */
  public void emitForShutdown(long waitMillis, int retry) {
    try {
      for (int i = 0; i < retry; i++) {
        if (emit(events)) {
          return;
        }
        Thread.sleep(waitMillis);
      }
    } catch (InterruptedException e) {
      // Ignore
    }
    System.err.println("Could not write the log: " + events);
  }

  /**
   * Turns a logged event into the record that is sent.
   *
   * @param <E> the type of the logged events
   * @param <R> the type of the records sent
   */
  public interface EventMapper<E, R> {
    R map(E event);
  }

  /**
   * Sends a batch of records.
   *
   * @param <R> the type of the records sent
   */
  public interface IntervalAppender<R> {
    // Threadsafe
    boolean append(List<R> events);
  }
}

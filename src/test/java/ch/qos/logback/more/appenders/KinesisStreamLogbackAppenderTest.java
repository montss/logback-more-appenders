/*
 * Copyright (c) 2026 montss <montss.dev@gmail.com>
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

import static ch.qos.logback.more.appenders.AwsTestSupport.NEVER;
import static ch.qos.logback.more.appenders.AwsTestSupport.event;
import static ch.qos.logback.more.appenders.AwsTestSupport.messageEncoder;
import static ch.qos.logback.more.appenders.AwsTestSupport.statusMessages;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.more.appenders.AwsAppender.AwsConfig;
import ch.qos.logback.more.appenders.KinesisStreamLogbackAppender.RandomPartitionKey;
import ch.qos.logback.more.appenders.KinesisStreamLogbackAppender.SinglePartitionKey;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link KinesisStreamLogbackAppender} (AWS SDK v1) against an in-memory Kinesis.
 *
 * <p>The emit interval is an hour, so the first event is sent at once and the rest when the
 * appender stops.
 */
public class KinesisStreamLogbackAppenderTest {

  private static final String STREAM = "app-stream";

  private LoggerContext context;
  private FakeAmazonKinesis kinesis;

  /** Creates a fresh Logback context and Kinesis for each test. */
  @Before
  public void setUp() {
    context = AwsTestSupport.newContext();
    kinesis = new FakeAmazonKinesis();
  }

  private KinesisStreamLogbackAppender appender() {
    KinesisStreamLogbackAppender appender = new KinesisStreamLogbackAppender();
    appender.setContext(context);
    AwsConfig config = new AwsConfig();
    config.setRegion("us-east-1");
    appender.setAwsConfig(config);
    appender.setStreamName(STREAM);
    appender.setEmitInterval(NEVER);
    appender.setEncoder(messageEncoder(context));
    return appender;
  }

  private void start(KinesisStreamLogbackAppender appender) {
    appender.start();
    appender.kinesis = kinesis;
  }

  private void log(KinesisStreamLogbackAppender appender, String... messages) {
    for (String message : messages) {
      appender.doAppend(event(context, message));
    }
  }

  @Test
  public void sendsRecordsToTheStreamWithThePartitionKey() {
    kinesis.withStream(STREAM);
    KinesisStreamLogbackAppender appender = appender();
    appender.setPartitionKey(new SinglePartitionKey());
    start(appender);

    log(appender, "first", "second", "third");
    appender.stop();

    assertEquals(asList("first", "second", "third"), kinesis.data());
    assertEquals(asList(asList("first"), asList("second", "third")), kinesis.putCalls);
    for (FakeAmazonKinesis.Record record : kinesis.records) {
      assertEquals(STREAM, record.stream);
      assertEquals("0", record.partitionKey);
    }
    assertTrue(kinesis.shutdown);
    assertEquals(Collections.emptyList(), statusMessages(context, Status.WARN));
  }

  @Test
  public void createsTheStreamWhenMissingAndWaitsUntilItIsActive() throws Exception {
    KinesisStreamLogbackAppender appender = appender();
    appender.setShardCount(2);
    start(appender);

    log(appender, "first");

    // The appender polls the new stream every 5 seconds until it is ACTIVE.
    long deadline = System.currentTimeMillis() + 15_000;
    while (!appender.active && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    appender.stop();

    assertEquals(asList(STREAM), kinesis.createdStreams);
    assertEquals(Integer.valueOf(2), kinesis.streams.get(STREAM));
    assertTrue("the stream should become active", appender.active);
  }

  @Test
  public void keepsTheRecordsAndSendsThemLaterWhenEveryRecordFails() {
    kinesis.withStream(STREAM);
    kinesis.failAllPuts = 1;
    KinesisStreamLogbackAppender appender = appender();
    start(appender);

    log(appender, "first", "second");
    appender.stop();

    assertEquals(asList(asList("first"), asList("first", "second")), kinesis.putCalls);
    assertEquals(asList("first", "second"), kinesis.data());
  }

  @Test
  public void reportsAnErrorAndDropsTheRecordsWhenPutRecordsThrows() {
    kinesis.withStream(STREAM);
    kinesis.throwPuts = 1;
    KinesisStreamLogbackAppender appender = appender();
    start(appender);

    log(appender, "lost", "second");
    appender.stop();

    assertEquals(asList("second"), kinesis.data());
    assertTrue(
        statusMessages(context, Status.ERROR)
            .toString()
            .contains("Unexpected runtime error while appending kinesis entries."));
  }

  @Test
  public void randomPartitionKeyIsBetween0And9999() {
    RandomPartitionKey key = new RandomPartitionKey();
    for (int i = 0; i < 1000; i++) {
      int value = Integer.parseInt(key.get(event(context, "x")));
      assertTrue(String.valueOf(value), value >= 0 && value < 10000);
    }
  }

  @Test
  public void singlePartitionKeyIsAlways0() {
    assertEquals("0", new SinglePartitionKey().get(event(context, "x")));
  }

  @Test
  public void requiresStreamName() {
    KinesisStreamLogbackAppender appender = new KinesisStreamLogbackAppender();
    appender.setContext(context);
    try {
      appender.start();
      fail("start() should fail without a stream name");
    } catch (IllegalArgumentException e) {
      assertEquals("streamName must be defined.", e.getMessage());
    }
  }
}

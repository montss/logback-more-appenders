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
import static ch.qos.logback.more.appenders.AwsTestSupport.repeat;
import static ch.qos.logback.more.appenders.AwsTestSupport.statusMessages;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.more.appenders.AwsAppender.AwsConfig;
import ch.qos.logback.more.appenders.CloudWatchLogbackAppender.CountBasedStreamName;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link CloudWatchLogbackAppender} (AWS SDK v1) against an in-memory CloudWatch Logs.
 *
 * <p>The emit interval is an hour, so the first event is sent at once and the rest when the
 * appender stops.
 */
public class CloudWatchLogbackAppenderTest {

  private static final String GROUP = "app-logs";
  private static final String STREAM = "app";

  private LoggerContext context;
  private FakeAwsLogs cloudWatch;

  /** Creates a fresh Logback context and CloudWatch Logs for each test. */
  @Before
  public void setUp() {
    context = AwsTestSupport.newContext();
    cloudWatch = new FakeAwsLogs();
  }

  private CloudWatchLogbackAppender<ILoggingEvent> appender() {
    CloudWatchLogbackAppender<ILoggingEvent> appender =
        new CloudWatchLogbackAppender<ILoggingEvent>();
    appender.setContext(context);
    AwsConfig config = new AwsConfig();
    config.setRegion("us-east-1");
    appender.setAwsConfig(config);
    appender.setLogGroupName(GROUP);
    appender.setLogStreamName(STREAM);
    appender.setEmitInterval(NEVER);
    appender.setEncoder(messageEncoder(context));
    return appender;
  }

  private void start(CloudWatchLogbackAppender<ILoggingEvent> appender) throws Exception {
    appender.start();
    AwsTestSupport.setField(appender, "awsLogs", cloudWatch);
  }

  private void log(CloudWatchLogbackAppender<ILoggingEvent> appender, String... messages) {
    for (String message : messages) {
      appender.doAppend(event(context, message));
    }
  }

  @Test
  public void sendsEventsToTheConfiguredGroupAndStream() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);

    log(appender, "first", "second", "third");
    appender.stop();

    assertEquals(asList("first", "second", "third"), cloudWatch.messages());
    assertEquals(2, cloudWatch.puts.size());
    for (FakeAwsLogs.Put put : cloudWatch.puts) {
      assertEquals(GROUP, put.group);
      assertEquals(STREAM, put.stream);
    }
    assertTrue(cloudWatch.shutdown);
    assertEquals(Collections.emptyList(), AwsTestSupport.problems(context, Status.WARN));
  }

  @Test
  public void passesTheSequenceTokenFromEachPutToTheNext() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);

    log(appender, "first", "second");
    appender.stop();

    assertNull(cloudWatch.puts.get(0).sequenceToken);
    assertEquals("token-1", cloudWatch.puts.get(1).sequenceToken);
  }

  @Test
  public void createsTheLogGroupWhenMissingAndCreationIsOn() throws Exception {
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    appender.setCreateLogDestination(true);
    appender.setLogStreamRolling(countBased("app-", 1000));
    start(appender);

    log(appender, "first");
    appender.stop();

    assertEquals(asList(GROUP), cloudWatch.createdGroups);
    assertEquals(asList("first"), cloudWatch.messages());
  }

  @Test
  public void reportsAnErrorAndSendsNothingWhenTheLogGroupIsMissing() throws Exception {
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);

    log(appender, "first");
    appender.stop();

    assertEquals(Collections.emptyList(), cloudWatch.puts);
    assertEquals(Collections.emptyList(), cloudWatch.createdGroups);
    List<String> errors = statusMessages(context, Status.ERROR);
    assertTrue(
        errors.toString(),
        errors.toString().contains("The specified log group does not exist: " + GROUP));
  }

  @Test
  public void splitsBatchesAtMaxEventCount() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    appender.setMaxEventCount(2);
    start(appender);

    log(appender, "1", "2", "3", "4", "5", "6");
    appender.stop();

    assertEquals(asList("1", "2", "3", "4", "5", "6"), cloudWatch.messages());
    assertEquals(4, cloudWatch.puts.size());
    assertEquals(asList("1"), cloudWatch.puts.get(0).messages);
    assertEquals(asList("2", "3"), cloudWatch.puts.get(1).messages);
    assertEquals(asList("4", "5"), cloudWatch.puts.get(2).messages);
    assertEquals(asList("6"), cloudWatch.puts.get(3).messages);
  }

  @Test
  public void splitsBatchesAtTheOneMegabyteLimit() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);
    // The appender counts 4 bytes per character, so two of these are over 1,048,576 bytes.
    String big1 = repeat('a', 200_000);
    String big2 = repeat('b', 200_000);

    log(appender, "first", big1, big2);
    appender.stop();

    assertEquals(3, cloudWatch.puts.size());
    assertEquals(asList(big1), cloudWatch.puts.get(1).messages);
    assertEquals(asList(big2), cloudWatch.puts.get(2).messages);
  }

  @Test
  public void truncatesMessageOverTheBatchLimitAndWarns() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);
    String huge = repeat('x', 1_048_576);

    log(appender, huge);
    appender.stop();

    assertEquals(asList(huge.substring(0, 512) + "...(Omitted)"), cloudWatch.messages());
    assertTrue(
        statusMessages(context, Status.WARN)
            .toString()
            .contains("Could not send all message to CloudWatch"));
  }

  @Test
  public void rollsToNewLogStreamAndCreatesIt() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    appender.setCreateLogDestination(true);
    appender.setLogStreamRolling(countBased("app-", 2));
    start(appender);

    log(appender, "1", "2", "3", "4", "5");
    appender.stop();

    assertEquals(asList("1", "2", "3", "4", "5"), cloudWatch.messages());
    assertEquals(2, cloudWatch.createdStreams.size());
    assertNotEquals(cloudWatch.createdStreams.get(0), cloudWatch.createdStreams.get(1));
    for (int i = 0; i < cloudWatch.puts.size(); i++) {
      String stream = cloudWatch.puts.get(i).stream;
      assertTrue(stream, stream.startsWith("app-"));
      assertEquals(cloudWatch.createdStreams.get(i), stream);
    }
  }

  @Test
  public void warnsAndKeepsSendingWhenPutFails() throws Exception {
    cloudWatch.withStream(GROUP, STREAM);
    cloudWatch.failPuts = 1;
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);

    log(appender, "lost", "second");
    appender.stop();

    assertEquals(asList("second"), cloudWatch.messages());
    assertNull(cloudWatch.puts.get(0).sequenceToken);
    assertTrue(
        statusMessages(context, Status.WARN)
            .toString()
            .contains("Skip sending logs to CloudWatch"));
  }

  @Test
  public void createsTheFixedLogStreamWhenMissingAndCreationIsOn() throws Exception {
    cloudWatch.withStream(GROUP, "other");
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    appender.setCreateLogDestination(true);
    start(appender);

    log(appender, "first", "second");
    appender.stop();

    assertEquals(asList(STREAM), cloudWatch.createdStreams);
    assertEquals(asList("first", "second"), cloudWatch.messages());
    assertEquals(Collections.emptyList(), AwsTestSupport.problems(context, Status.WARN));
  }

  @Test
  public void reportsMissingFixedLogStreamAndSendsOnceItExists() throws Exception {
    cloudWatch.withStream(GROUP, "other");
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);

    log(appender, "lost");
    cloudWatch.withStream(GROUP, STREAM);
    log(appender, "second");
    appender.stop();

    assertEquals(Collections.emptyList(), cloudWatch.createdStreams);
    assertEquals(asList("second"), cloudWatch.messages());
    assertTrue(
        statusMessages(context, Status.ERROR)
            .toString()
            .contains("The specified log stream does not exist"));
  }

  @Test
  public void warnsThatItUsesTheDeprecatedSdkV1() throws Exception {
    CloudWatchLogbackAppender<ILoggingEvent> appender = appender();
    start(appender);
    appender.stop();

    String warnings = statusMessages(context, Status.WARN).toString();
    assertTrue(warnings, warnings.contains(AwsTestSupport.SDK_V1_DEPRECATION));
    assertTrue(warnings, warnings.contains(CloudWatchLogbackAppenderV2.class.getName()));
  }

  @Test
  public void requiresLogGroupAndStream() {
    CloudWatchLogbackAppender<ILoggingEvent> appender =
        new CloudWatchLogbackAppender<ILoggingEvent>();
    appender.setContext(context);
    appender.setLogStreamName(STREAM);
    try {
      appender.start();
      fail("start() should fail without a log group");
    } catch (IllegalArgumentException e) {
      assertEquals("logGroupName and logStreamName must be defined.", e.getMessage());
    }
  }

  private static CountBasedStreamName countBased(String baseName, long limit) {
    CountBasedStreamName name = new CountBasedStreamName();
    name.setBaseName(baseName);
    name.setLimit(limit);
    return name;
  }
}

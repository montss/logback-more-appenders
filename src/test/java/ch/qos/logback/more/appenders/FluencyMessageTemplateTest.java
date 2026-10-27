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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** Tests the message template option of {@link FluencyLogbackAppender}. */
public class FluencyMessageTemplateTest {

  private static final String TEMPLATE = "User {} logged in from {}";

  private LoggerContext context;
  private FluencyLogbackAppender<ILoggingEvent> appender;

  /** Creates an appender that is configured but never started, so nothing is sent. */
  @Before
  public void setUp() {
    context = new LoggerContext();
    appender = new FluencyLogbackAppender<ILoggingEvent>();
    appender.setContext(context);
  }

  private ILoggingEvent event(String message, Object... arguments) {
    return new LoggingEvent(
        FluencyMessageTemplateTest.class.getName(),
        context.getLogger(FluencyMessageTemplateTest.class),
        Level.INFO,
        message,
        null,
        arguments);
  }

  @Test
  public void isOffByDefault() {
    Map<String, Object> data = appender.createData(event(TEMPLATE, "alice", "10.0.0.1"));

    assertFalse(appender.isCaptureMessageTemplate());
    assertFalse(data.containsKey("message_template"));
    assertEquals("User alice logged in from 10.0.0.1", data.get("message"));
  }

  @Test
  public void sendsTheTemplateNextToTheFormattedMessage() {
    appender.setCaptureMessageTemplate(true);

    Map<String, Object> data = appender.createData(event(TEMPLATE, "alice", "10.0.0.1"));

    assertEquals(TEMPLATE, data.get("message_template"));
    assertEquals("User alice logged in from 10.0.0.1", data.get("message"));
  }

  @Test
  public void leavesTheTemplateOutWhenItIsTheSameAsTheMessage() {
    appender.setCaptureMessageTemplate(true);

    assertFalse(appender.createData(event("Started")).containsKey("message_template"));
    assertFalse(appender.createData(event(TEMPLATE)).containsKey("message_template"));
  }

  @Test
  public void usesTheConfiguredFieldName() {
    appender.setCaptureMessageTemplate(true);
    appender.setMessageTemplateFieldName("template");

    Map<String, Object> data = appender.createData(event(TEMPLATE, "alice", "10.0.0.1"));

    assertEquals("template", appender.getMessageTemplateFieldName());
    assertEquals(TEMPLATE, data.get("template"));
    assertFalse(data.containsKey("message_template"));
  }

  @Test
  public void fallsBackToTheDefaultFieldNameWhenEmpty() {
    appender.setCaptureMessageTemplate(true);
    appender.setMessageTemplateFieldName("");

    Map<String, Object> data = appender.createData(event(TEMPLATE, "alice", "10.0.0.1"));

    assertEquals(TEMPLATE, data.get("message_template"));
  }

  @Test
  public void ignoresEventsThatAreNotLoggingEvents() {
    FluencyLogbackAppender<String> stringAppender = new FluencyLogbackAppender<String>();
    stringAppender.setContext(context);
    stringAppender.setCaptureMessageTemplate(true);

    Map<String, Object> data = stringAppender.createData("plain {} event");

    assertEquals("plain {} event", data.get("message"));
    assertEquals(1, data.size());
  }
}

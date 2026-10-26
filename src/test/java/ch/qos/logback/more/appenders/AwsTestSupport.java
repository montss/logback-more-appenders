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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.status.Status;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the CloudWatch and Kinesis appender tests. */
final class AwsTestSupport {

  private AwsTestSupport() {}

  /** An emit interval long enough that only the first event and stop() send anything. */
  static final long NEVER = 3_600_000L;

  static LoggerContext newContext() {
    return new LoggerContext();
  }

  /** An encoder that writes only the message, so tests can compare what was sent. */
  static PatternLayoutEncoder messageEncoder(LoggerContext context) {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("%msg");
    encoder.start();
    return encoder;
  }

  static ILoggingEvent event(LoggerContext context, String message) {
    return new LoggingEvent(
        AwsTestSupport.class.getName(),
        context.getLogger("aws-test"),
        Level.INFO,
        message,
        null,
        null);
  }

  /** The messages of the status entries at this level or above that the appender reported. */
  static List<String> statusMessages(LoggerContext context, int level) {
    List<String> messages = new ArrayList<String>();
    for (Status status : context.getStatusManager().getCopyOfStatusList()) {
      if (status.getLevel() >= level) {
        Throwable cause = status.getThrowable();
        messages.add(status.getMessage() + (cause == null ? "" : " / " + cause.getMessage()));
      }
    }
    return messages;
  }

  /** The start-up warning of the deprecated AWS SDK v1 appenders. */
  static final String SDK_V1_DEPRECATION = "uses the AWS SDK for Java v1";

  /** Like {@link #statusMessages}, without the expected SDK v1 deprecation warning. */
  static List<String> problems(LoggerContext context, int level) {
    List<String> messages = statusMessages(context, level);
    for (java.util.Iterator<String> it = messages.iterator(); it.hasNext(); ) {
      if (it.next().contains(SDK_V1_DEPRECATION)) {
        it.remove();
      }
    }
    return messages;
  }

  /** Sets a private field, for clients the appenders otherwise build themselves. */
  static void setField(Object target, String name, Object value) throws Exception {
    Class<?> type = target.getClass();
    while (type != null) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
        return;
      } catch (NoSuchFieldException e) {
        type = type.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  static String repeat(char c, int count) {
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      sb.append(c);
    }
    return sb.toString();
  }
}

/**
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

import static org.junit.Assert.assertTrue;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Loads the sample configs that the README links to and fails on any Logback warning or error, so
 * every documented option really exists and the samples can be copied as they are.
 */
public class SampleConfigurationsTest {

  @Test
  public void logbackXml() throws Exception {
    // includes logback-appenders-aws.xml, -aws-v2.xml, -fluentd.xml and -std.xml
    assertLoadsCleanly("/logback.xml");
  }

  private static void assertLoadsCleanly(String resource) throws Exception {
    LoggerContext context = new LoggerContext();
    try {
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(context);
      configurator.doConfigure(SampleConfigurationsTest.class.getResource(resource));
      List<Status> problems = new ArrayList<Status>();
      for (Status status : context.getStatusManager().getCopyOfStatusList()) {
        if (status.getEffectiveLevel() >= Status.WARN) {
          problems.add(status);
        }
      }
      assertTrue(resource + " has problems: " + problems, problems.isEmpty());
    } finally {
      context.stop();
    }
  }
}

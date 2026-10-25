/**
 * Copyright (c) 2012 sndyuk <sanada@sndyuk.com>
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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.more.appenders.FakeFluentdServer.Event;
import ch.qos.logback.more.appenders.marker.MapMarker;

/**
 * Logs through FluencyLogbackAppender (configured from logback-appender-test.xml) to an in-process Fluentd and
 * checks what arrives, plus what the AppendersMarkerFilter lets through. Fails on any Logback warning or error.
 */
public class LogbackAppenderTest {

    private static final long TIMEOUT_MILLIS = 10000;

    private static FakeFluentdServer fluentd;
    private static LoggerContext context;
    private static Logger log;
    private static ListAppender<ILoggingEvent> securityAlerts;

    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void before() throws Exception {
        fluentd = new FakeFluentdServer();
        context = new LoggerContext();
        context.putProperty("fluentd.port", String.valueOf(fluentd.port()));
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(LogbackAppenderTest.class.getResource("/logback-appender-test.xml"));
        log = context.getLogger(LogbackAppenderTest.class);
        securityAlerts = (ListAppender<ILoggingEvent>) context.getLogger(Logger.ROOT_LOGGER_NAME)
                .getAppender("SECURITY_ALERT");
        assertNoLogbackProblems();
    }

    @AfterClass
    public static void after() throws IOException {
        try {
            context.stop();
            assertNoLogbackProblems();
        } finally {
            fluentd.close();
        }
    }

    @After
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void logSimple() throws Exception {
        log.debug("logSimple 1");
        log.debug("logSimple 2");

        Event first = fluentd.awaitMessage("message", "logSimple 1", TIMEOUT_MILLIS);
        assertEquals("test.logback", first.tag);
        assertEquals("DEBUG", first.record.get("level"));
        assertEquals(LogbackAppenderTest.class.getName(), first.record.get("logger"));
        assertEquals(Thread.currentThread().getName(), first.record.get("thread"));
        assertEquals("additional field", "bar", first.record.get("foo"));
        assertTrue("time is sent as EventTime", first.eventTime);
        fluentd.awaitMessage("message", "logSimple 2", TIMEOUT_MILLIS);
    }

    @Test
    public void logMdc() throws Exception {
        MDC.put("req.requestURI", "/hello/world.js");
        log.debug("logMdc");

        Event event = fluentd.awaitMessage("message", "logMdc", TIMEOUT_MILLIS);
        assertEquals("/hello/world.js", event.record.get("req.requestURI"));
    }

    @Test
    public void logMarker() throws Exception {
        log.debug(MarkerFactory.getDetachedMarker("SEND_EMAIL"), "logMarker");

        Event event = fluentd.awaitMessage("message", "logMarker", TIMEOUT_MILLIS);
        assertEquals("SEND_EMAIL", event.record.get("marker"));
    }

    @Test
    public void logNestedMarker() throws Exception {
        Marker sendEmailMarker = MarkerFactory.getDetachedMarker("SEND_EMAIL");
        sendEmailMarker.add(MarkerFactory.getDetachedMarker("NOTIFY"));
        log.debug(sendEmailMarker, "logNestedMarker");

        Event event = fluentd.awaitMessage("message", "logNestedMarker", TIMEOUT_MILLIS);
        assertEquals(sendEmailMarker.toString(), event.record.get("marker"));
        assertTrue("nested marker is included", sendEmailMarker.toString().contains("NOTIFY"));
    }

    @Test
    public void logThrowable() throws Exception {
        log.error("logThrowable checked", new IOException("Connection something"));
        log.warn("logThrowable unchecked", new IllegalStateException("Oh your state"));

        Event checked = fluentd.awaitMessage("message", "logThrowable checked", TIMEOUT_MILLIS);
        assertEquals("ERROR", checked.record.get("level"));
        assertTrue(String.valueOf(checked.record.get("throwable"))
                .contains("java.io.IOException: Connection something"));
        Event unchecked = fluentd.awaitMessage("message", "logThrowable unchecked", TIMEOUT_MILLIS);
        assertEquals("WARN", unchecked.record.get("level"));
        assertTrue(String.valueOf(unchecked.record.get("throwable"))
                .contains("java.lang.IllegalStateException: Oh your state"));
    }

    @Test
    public void logMapMarker() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        log.debug(new MapMarker("MAP_MARKER", map), "logMapMarker");

        Event event = fluentd.awaitMessage("message", "logMapMarker", TIMEOUT_MILLIS);
        assertEquals(map, event.record.get("marker.MAP_MARKER"));
    }

    @Test
    public void logNestedMapMarker() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        Marker notifyMarker = MarkerFactory.getDetachedMarker("NOTIFY");
        notifyMarker.add(new MapMarker("MAP_MARKER", map));
        log.debug(notifyMarker, "logNestedMapMarker");

        Event event = fluentd.awaitMessage("message", "logNestedMapMarker", TIMEOUT_MILLIS);
        assertEquals(notifyMarker.toString(), event.record.get("marker"));
        assertEquals(map, event.record.get("marker.MAP_MARKER"));
    }

    @Test
    public void logDecidedByAppendersMarkerFilter() throws Exception {
        log.debug(MarkerFactory.getDetachedMarker("SECURITY_ALERT"), "logDecidedByAppendersMarkerFilter alert");
        log.debug("logDecidedByAppendersMarkerFilter plain");

        List<String> alerts = new ArrayList<String>();
        for (ILoggingEvent event : securityAlerts.list) {
            alerts.add(event.getFormattedMessage());
        }
        assertTrue("alert kept: " + alerts, alerts.contains("logDecidedByAppendersMarkerFilter alert"));
        assertTrue("plain event filtered out: " + alerts, !alerts.contains("logDecidedByAppendersMarkerFilter plain"));
        // the marker filter is on SECURITY_ALERT only, so Fluentd still gets both
        fluentd.awaitMessage("message", "logDecidedByAppendersMarkerFilter alert", TIMEOUT_MILLIS);
        fluentd.awaitMessage("message", "logDecidedByAppendersMarkerFilter plain", TIMEOUT_MILLIS);
    }

    private static void assertNoLogbackProblems() {
        List<Status> problems = new ArrayList<Status>();
        for (Status status : context.getStatusManager().getCopyOfStatusList()) {
            if (status.getEffectiveLevel() >= Status.WARN) {
                problems.add(status);
            }
        }
        assertTrue("Logback reported problems: " + problems, problems.isEmpty());
    }
}

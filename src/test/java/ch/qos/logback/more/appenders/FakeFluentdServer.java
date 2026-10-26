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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.MapValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

/**
 * Minimal in-process Fluentd for tests. It listens on a free local port, decodes the Fluentd
 * forward protocol (Message, Forward and PackedForward modes), answers acknowledgements like
 * Fluentd's in_forward does, and keeps every received event.
 */
final class FakeFluentdServer implements AutoCloseable {

  /** One event as Fluentd received it. */
  static final class Event {
    final String tag;
    /**
     * True when the time was sent as EventTime (sub-second precision), false for integer seconds.
     */
    final boolean eventTime;

    final Map<String, Object> record;

    Event(String tag, boolean eventTime, Map<String, Object> record) {
      this.tag = tag;
      this.eventTime = eventTime;
      this.record = record;
    }

    @Override
    public String toString() {
      return tag + " " + record;
    }
  }

  private final ServerSocket serverSocket;
  private final List<Event> events = new CopyOnWriteArrayList<Event>();
  private final List<Socket> clients = new CopyOnWriteArrayList<Socket>();
  private volatile boolean running = true;

  FakeFluentdServer() throws IOException {
    serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    Thread acceptor = new Thread(this::acceptLoop, "fake-fluentd-acceptor");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  int port() {
    return serverSocket.getLocalPort();
  }

  List<Event> events() {
    return new ArrayList<Event>(events);
  }

  /** Waits until an event matches, failing with the received events after the timeout. */
  Event awaitEvent(Predicate<Event> matcher, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      for (Event event : events) {
        if (matcher.test(event)) {
          return event;
        }
      }
      Thread.sleep(50);
    }
    throw new AssertionError(
        "No matching event received within " + timeoutMillis + " ms. Received: " + events);
  }

  /** Waits until an event with this message field value arrives. */
  Event awaitMessage(String messageField, String message, long timeoutMillis)
      throws InterruptedException {
    return awaitEvent(e -> message.equals(e.record.get(messageField)), timeoutMillis);
  }

  @Override
  public void close() throws IOException {
    running = false;
    serverSocket.close();
    for (Socket client : clients) {
      client.close();
    }
  }

  private void acceptLoop() {
    while (running) {
      try {
        Socket client = serverSocket.accept();
        clients.add(client);
        Thread reader = new Thread(() -> readLoop(client), "fake-fluentd-reader");
        reader.setDaemon(true);
        reader.start();
      } catch (IOException e) {
        if (running) {
          e.printStackTrace();
        }
      }
    }
  }

  private void readLoop(Socket client) {
    try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(client.getInputStream())) {
      OutputStream out = client.getOutputStream();
      while (running && unpacker.hasNext()) {
        ArrayValue message = unpacker.unpackValue().asArrayValue();
        String tag = message.get(0).asStringValue().asString();
        Value entries = message.get(1);
        Value option = null;
        if (entries.isBinaryValue() || entries.isStringValue()) {
          // PackedForward: a binary stream of [time, record] pairs
          byte[] packed =
              entries.isBinaryValue()
                  ? entries.asBinaryValue().asByteArray()
                  : entries.asStringValue().asByteArray();
          try (MessageUnpacker inner = MessagePack.newDefaultUnpacker(packed)) {
            while (inner.hasNext()) {
              addEntry(tag, inner.unpackValue().asArrayValue());
            }
          }
          option = message.size() > 2 ? message.get(2) : null;
        } else if (entries.isArrayValue()) {
          // Forward: an array of [time, record] pairs
          for (Value entry : entries.asArrayValue()) {
            addEntry(tag, entry.asArrayValue());
          }
          option = message.size() > 2 ? message.get(2) : null;
        } else {
          // Message: [tag, time, record, option?]
          events.add(
              new Event(tag, entries.isExtensionValue(), toMap(message.get(2).asMapValue())));
          option = message.size() > 3 ? message.get(3) : null;
        }
        acknowledge(option, out);
      }
    } catch (IOException e) {
      if (running && !client.isClosed()) {
        e.printStackTrace();
      }
    }
  }

  private void addEntry(String tag, ArrayValue entry) {
    Value time = entry.get(0);
    events.add(new Event(tag, time.isExtensionValue(), toMap(entry.get(1).asMapValue())));
  }

  private static void acknowledge(Value option, OutputStream out) throws IOException {
    if (option == null || !option.isMapValue()) {
      return;
    }
    Value chunk = option.asMapValue().map().get(ValueFactory.newString("chunk"));
    if (chunk == null) {
      return;
    }
    try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
      packer.packMapHeader(1);
      packer.packString("ack");
      packer.packString(chunk.asStringValue().asString());
      out.write(packer.toByteArray());
      out.flush();
    }
  }

  private static Map<String, Object> toMap(MapValue map) {
    Map<String, Object> result = new HashMap<String, Object>();
    for (Map.Entry<Value, Value> entry : map.entrySet()) {
      result.put(entry.getKey().asStringValue().asString(), toJava(entry.getValue()));
    }
    return result;
  }

  private static Object toJava(Value value) {
    if (value.isNilValue()) {
      return null;
    }
    if (value.isStringValue()) {
      return value.asStringValue().asString();
    }
    if (value.isMapValue()) {
      return toMap(value.asMapValue());
    }
    if (value.isArrayValue()) {
      List<Object> list = new ArrayList<Object>();
      for (Value item : value.asArrayValue()) {
        list.add(toJava(item));
      }
      return list;
    }
    return value.toString();
  }
}

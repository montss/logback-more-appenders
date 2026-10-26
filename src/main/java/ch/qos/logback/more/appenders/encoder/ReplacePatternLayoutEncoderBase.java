/*
 * Copyright (c) 2019 sndyuk <sanada@sndyuk.com>
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

package ch.qos.logback.more.appenders.encoder;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import ch.qos.logback.core.pattern.PatternLayoutEncoderBase;
import java.lang.reflect.Field;

/**
 * Pattern layout encoder that replaces character sequences (for example characters to escape) in
 * the output of each converter.
 */
public abstract class ReplacePatternLayoutEncoderBase
    extends PatternLayoutEncoderBase<ILoggingEvent> {

  /** Writes the output of one converter into the buffer. */
  public interface Writer {
    void write(Converter<ILoggingEvent> converter, ILoggingEvent event, StringBuilder buf);
  }

  /** The character sequences to replace, and their replacements at the same index. */
  protected static class ReplacePattern {

    private final char[][] from;
    private final char[][] to;

    public ReplacePattern(char[][] from, char[][] to) {
      this.from = from;
      this.to = to;
    }
  }

  protected abstract ReplacePattern getReplacePattern();

  protected abstract Writer getWriter();

  private char[][] from;
  private char[][] to;

  @Override
  public void start() {
    ReplacePattern replacePattern = getReplacePattern();
    this.from = replacePattern.from;
    this.to = replacePattern.to;
    ReplacePatternLayout patternLayout = new ReplacePatternLayout(getWriter());
    patternLayout.setContext(context);
    patternLayout.setPattern(getPattern());
    patternLayout.setOutputPatternAsHeader(outputPatternAsHeader);
    patternLayout.start();
    this.layout = patternLayout;
    super.start();
  }

  @Override
  public byte[] encode(ILoggingEvent event) {
    return super.encode(event);
  }

  /**
   * Replaces every configured character sequence in a part of the buffer.
   *
   * @param buff the buffer to change
   * @param start the index the part starts at
   * @param end the index the part ends at, before any replacement
   */
  public void replace(StringBuilder buff, int start, int end) {
    int modified = 0;
    CHARS:
    for (int i = start; i < end + modified; i++) {
      FROM:
      for (int j = 0; j < from.length; j++) {
        char[] f = from[j];
        if (i + f.length > buff.length()) {
          continue FROM;
        }
        for (int k = 0; k < f.length; k++) {
          if (f[k] != buff.charAt(i + k)) {
            continue FROM;
          }
        }
        // Found
        buff.delete(i, i + f.length);
        buff.insert(i, to[j]);
        int d = (to[j].length - f.length);
        i = i + d;
        modified = modified + d;
        continue CHARS;
      }
    }
  }

  /** Pattern layout that passes each converter's output through the {@link Writer}. */
  protected static class ReplacePatternLayout extends PatternLayout {
    Converter<ILoggingEvent> head;
    private Writer writer;

    public ReplacePatternLayout(Writer writer) {
      this.writer = writer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start() {
      super.start();
      try {
        Field headField = PatternLayoutBase.class.getDeclaredField("head");
        headField.setAccessible(true);
        head = (Converter<ILoggingEvent>) headField.get(this);
        headField.setAccessible(false);
      } catch (Exception e) {
        throw new RuntimeException("Please raise the issue.", e);
      }
    }

    @Override
    protected String writeLoopOnConverters(ILoggingEvent event) {
      super.writeLoopOnConverters(event);
      StringBuilder buf = new StringBuilder(256);
      Converter<ILoggingEvent> c = head;
      while (c != null) {
        writer.write(c, event, buf);
        c = c.getNext();
      }
      return buf.toString();
    }
  }
}

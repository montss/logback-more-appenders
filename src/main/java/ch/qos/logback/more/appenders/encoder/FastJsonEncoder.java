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

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.pattern.DynamicConverter;
import ch.qos.logback.core.pattern.LiteralConverter;

/**
 * JSON encoder. It just escapes meta characters of JSON not to lose performance. It doesn't handle
 * null value.
 *
 * @author sndyuk
 */
public class FastJsonEncoder extends ReplacePatternLayoutEncoderBase {

  private static final char[][] FROM = {{'\b'}, {'\f'}, {'\n'}, {'\r'}, {'\t'}, {'\"'}, {'\\'}};
  private static final char[][] TO = {
    {'\\', 'b'}, {'\\', 'f'}, {'\\', 'n'}, {'\\', 'r'}, {'\\', 't'}, {'\\', '"'}, {'\\', '\\'}
  };

  private static final ReplacePattern JSON_REPLACE_PATTERN = new ReplacePattern(FROM, TO);

  private boolean compressSpace;

  public void setCompressSpace(boolean compressSpace) {
    this.compressSpace = compressSpace;
  }

  private final class JsonWriter implements Writer {
    @Override
    public void write(Converter<ILoggingEvent> converter, ILoggingEvent event, StringBuilder buf) {
      try {
        int start;
        int end;
        if (converter instanceof ThrowableProxyConverter) {
          if (event.getThrowableProxy() == null) {
            return;
          }
          int closing = buf.lastIndexOf("}");
          final String closingStr = buf.substring(closing);
          buf.delete(closing, buf.length()).append(",\"stacktrace\":\"");
          start = buf.length();
          converter.write(buf, event);
          end = buf.length();
          buf.append("\"");
          buf.append(closingStr);
        } else {
          start = buf.length();
          converter.write(buf, event);
          end = buf.length();
        }

        if (end == start) {
          return;
        }
        if (compressSpace) {
          if (converter instanceof LiteralConverter) {
            int spaceStart = -1;
            int spaceEnd = -1;
            int spaceRemoved = 0;
            boolean withinValue = false;
            for (int i = start + 1; i < end - spaceRemoved; i++) {
              char c = buf.charAt(i);
              if (!withinValue) {
                boolean space = c == '\r' || c == '\n' || c == ' ' || c == '\t';
                if (spaceStart == -1 && space) {
                  spaceStart = i;
                } else if (spaceStart >= 0 && !space) {
                  spaceEnd = i;
                }
                if (spaceEnd > spaceStart) {
                  buf.delete(spaceStart, spaceEnd);
                  spaceRemoved += spaceEnd - spaceStart;
                  spaceStart = -1;
                  spaceEnd = -1;
                }
              }
              if (c == '"' && buf.charAt(i - 1) != '\\') {
                withinValue = !withinValue;
                if (withinValue) {
                  spaceStart = -1;
                  spaceEnd = -1;
                }
              }
            }
          }
        }
        if (converter instanceof DynamicConverter) {
          replace(buf, start, end);
        }
      } catch (RuntimeException e) {
        e.printStackTrace();
        throw e;
      }
    }
  }

  private final JsonWriter writer = new JsonWriter();

  @Override
  protected ReplacePattern getReplacePattern() {
    return JSON_REPLACE_PATTERN;
  }

  @Override
  protected Writer getWriter() {
    return writer;
  }
}

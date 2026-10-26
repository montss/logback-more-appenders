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

import com.amazonaws.services.logs.AbstractAWSLogs;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.CreateLogGroupResult;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.CreateLogStreamResult;
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest;
import com.amazonaws.services.logs.model.DescribeLogStreamsResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.LogGroup;
import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;
import com.amazonaws.services.logs.model.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory CloudWatch Logs for the AWS SDK v1 appender. Like the real service, it rejects events
 * for a log group or stream that does not exist.
 */
final class FakeAwsLogs extends AbstractAWSLogs {

  /** One PutLogEvents call as the service received it. */
  static final class Put {
    final String group;
    final String stream;
    final String sequenceToken;
    final List<String> messages;

    Put(String group, String stream, String sequenceToken, List<String> messages) {
      this.group = group;
      this.stream = stream;
      this.sequenceToken = sequenceToken;
      this.messages = messages;
    }

    @Override
    public String toString() {
      return group + "/" + stream + " " + messages;
    }
  }

  final Set<String> groups = new TreeSet<String>();
  /** Log streams per log group. */
  final Map<String, Set<String>> streams = new TreeMap<String, Set<String>>();

  final List<String> createdGroups = new CopyOnWriteArrayList<String>();
  final List<String> createdStreams = new CopyOnWriteArrayList<String>();
  final List<Put> puts = new CopyOnWriteArrayList<Put>();
  /** Number of PutLogEvents calls still to fail. */
  int failPuts;

  boolean shutdown;
  private int token;

  FakeAwsLogs withStream(String group, String stream) {
    groups.add(group);
    streamsOf(group).add(stream);
    return this;
  }

  private Set<String> streamsOf(String group) {
    Set<String> set = streams.get(group);
    if (set == null) {
      set = new TreeSet<String>();
      streams.put(group, set);
    }
    return set;
  }

  /** All messages received, in order. */
  List<String> messages() {
    List<String> all = new ArrayList<String>();
    for (Put put : puts) {
      all.addAll(put.messages);
    }
    return all;
  }

  @Override
  public synchronized DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request) {
    List<LogGroup> found = new ArrayList<LogGroup>();
    for (String group : groups) {
      if (group.startsWith(request.getLogGroupNamePrefix())) {
        found.add(new LogGroup().withLogGroupName(group));
      }
    }
    return new DescribeLogGroupsResult().withLogGroups(limit(found, request.getLimit()));
  }

  @Override
  public synchronized CreateLogGroupResult createLogGroup(CreateLogGroupRequest request) {
    groups.add(request.getLogGroupName());
    createdGroups.add(request.getLogGroupName());
    return new CreateLogGroupResult();
  }

  @Override
  public synchronized DescribeLogStreamsResult describeLogStreams(
      DescribeLogStreamsRequest request) {
    requireGroup(request.getLogGroupName());
    List<LogStream> found = new ArrayList<LogStream>();
    for (String stream : streamsOf(request.getLogGroupName())) {
      if (stream.startsWith(request.getLogStreamNamePrefix())) {
        found.add(new LogStream().withLogStreamName(stream));
      }
    }
    return new DescribeLogStreamsResult().withLogStreams(limit(found, request.getLimit()));
  }

  @Override
  public synchronized CreateLogStreamResult createLogStream(CreateLogStreamRequest request) {
    requireGroup(request.getLogGroupName());
    streamsOf(request.getLogGroupName()).add(request.getLogStreamName());
    createdStreams.add(request.getLogStreamName());
    return new CreateLogStreamResult();
  }

  @Override
  public synchronized PutLogEventsResult putLogEvents(PutLogEventsRequest request) {
    if (failPuts > 0) {
      failPuts--;
      throw new IllegalStateException("simulated PutLogEvents failure");
    }
    requireGroup(request.getLogGroupName());
    if (!streamsOf(request.getLogGroupName()).contains(request.getLogStreamName())) {
      throw new ResourceNotFoundException(
          "The specified log stream does not exist: " + request.getLogStreamName());
    }
    List<String> messages = new ArrayList<String>();
    for (InputLogEvent event : request.getLogEvents()) {
      messages.add(event.getMessage());
    }
    puts.add(
        new Put(
            request.getLogGroupName(),
            request.getLogStreamName(),
            request.getSequenceToken(),
            messages));
    return new PutLogEventsResult().withNextSequenceToken("token-" + (++token));
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }

  private void requireGroup(String group) {
    if (!groups.contains(group)) {
      throw new ResourceNotFoundException("The specified log group does not exist: " + group);
    }
  }

  private static <T> List<T> limit(List<T> list, Integer limit) {
    return limit == null || list.size() <= limit ? list : list.subList(0, limit);
  }
}

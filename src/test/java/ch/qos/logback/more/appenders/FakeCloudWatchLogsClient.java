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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException;

/**
 * In-memory CloudWatch Logs for the AWS SDK v2 appender. Like the real service, it rejects events
 * for a log group or stream that does not exist.
 */
final class FakeCloudWatchLogsClient implements CloudWatchLogsClient {

  final Set<String> groups = new TreeSet<String>();
  /** Log streams per log group. */
  final Map<String, Set<String>> streams = new TreeMap<String, Set<String>>();

  final List<String> createdGroups = new CopyOnWriteArrayList<String>();
  final List<String> createdStreams = new CopyOnWriteArrayList<String>();
  final List<FakeAwsLogs.Put> puts = new CopyOnWriteArrayList<FakeAwsLogs.Put>();
  /** Number of PutLogEvents calls still to fail. */
  int failPuts;

  boolean closed;
  private int token;

  FakeCloudWatchLogsClient withStream(String group, String stream) {
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
    for (FakeAwsLogs.Put put : puts) {
      all.addAll(put.messages);
    }
    return all;
  }

  @Override
  public synchronized DescribeLogGroupsResponse describeLogGroups(
      DescribeLogGroupsRequest request) {
    List<LogGroup> found = new ArrayList<LogGroup>();
    for (String group : groups) {
      if (group.startsWith(request.logGroupNamePrefix())) {
        found.add(LogGroup.builder().logGroupName(group).build());
      }
    }
    return DescribeLogGroupsResponse.builder().logGroups(limit(found, request.limit())).build();
  }

  @Override
  public synchronized CreateLogGroupResponse createLogGroup(CreateLogGroupRequest request) {
    groups.add(request.logGroupName());
    createdGroups.add(request.logGroupName());
    return CreateLogGroupResponse.builder().build();
  }

  @Override
  public synchronized DescribeLogStreamsResponse describeLogStreams(
      DescribeLogStreamsRequest request) {
    requireGroup(request.logGroupName());
    List<LogStream> found = new ArrayList<LogStream>();
    for (String stream : streamsOf(request.logGroupName())) {
      if (stream.startsWith(request.logStreamNamePrefix())) {
        found.add(LogStream.builder().logStreamName(stream).build());
      }
    }
    return DescribeLogStreamsResponse.builder().logStreams(limit(found, request.limit())).build();
  }

  @Override
  public synchronized CreateLogStreamResponse createLogStream(CreateLogStreamRequest request) {
    requireGroup(request.logGroupName());
    streamsOf(request.logGroupName()).add(request.logStreamName());
    createdStreams.add(request.logStreamName());
    return CreateLogStreamResponse.builder().build();
  }

  @Override
  public synchronized PutLogEventsResponse putLogEvents(PutLogEventsRequest request) {
    if (failPuts > 0) {
      failPuts--;
      throw new IllegalStateException("simulated PutLogEvents failure");
    }
    requireGroup(request.logGroupName());
    if (!streamsOf(request.logGroupName()).contains(request.logStreamName())) {
      throw ResourceNotFoundException.builder()
          .message("The specified log stream does not exist: " + request.logStreamName())
          .build();
    }
    List<String> messages = new ArrayList<String>();
    for (InputLogEvent event : request.logEvents()) {
      messages.add(event.message());
    }
    puts.add(
        new FakeAwsLogs.Put(
            request.logGroupName(), request.logStreamName(), request.sequenceToken(), messages));
    return PutLogEventsResponse.builder().nextSequenceToken("token-" + (++token)).build();
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void close() {
    closed = true;
  }

  private void requireGroup(String group) {
    if (!groups.contains(group)) {
      throw ResourceNotFoundException.builder()
          .message("The specified log group does not exist: " + group)
          .build();
    }
  }

  private static <T> List<T> limit(List<T> list, Integer limit) {
    return limit == null || list.size() <= limit ? list : list.subList(0, limit);
  }
}

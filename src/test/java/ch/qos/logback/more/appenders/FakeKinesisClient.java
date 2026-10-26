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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.CreateStreamResponse;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.StreamDescription;
import software.amazon.awssdk.services.kinesis.model.StreamStatus;

/**
 * In-memory Kinesis Data Streams for the AWS SDK v2 appender. Created streams are ACTIVE at once.
 */
final class FakeKinesisClient implements KinesisClient {

  /** Shard count per stream. */
  final Map<String, Integer> streams = new ConcurrentHashMap<String, Integer>();

  final List<String> createdStreams = new CopyOnWriteArrayList<String>();
  /** The records of each PutRecords call, accepted or not. */
  final List<List<String>> putCalls = new CopyOnWriteArrayList<List<String>>();

  final List<FakeAmazonKinesis.Record> records =
      new CopyOnWriteArrayList<FakeAmazonKinesis.Record>();

  /** Number of PutRecords calls still to reject every record of. */
  volatile int failAllPuts;
  /** Number of PutRecords calls still to throw on. */
  volatile int throwPuts;
  /** Data of records to reject once, each. */
  final Set<String> failOnce = Collections.synchronizedSet(new HashSet<String>());

  volatile boolean closed;

  FakeKinesisClient withStream(String name) {
    streams.put(name, 1);
    return this;
  }

  /** The data of all accepted records, in order. */
  List<String> data() {
    List<String> all = new ArrayList<String>();
    for (FakeAmazonKinesis.Record record : records) {
      all.add(record.data);
    }
    return all;
  }

  @Override
  public DescribeStreamResponse describeStream(DescribeStreamRequest request) {
    if (!streams.containsKey(request.streamName())) {
      throw ResourceNotFoundException.builder()
          .message("Stream " + request.streamName() + " not found")
          .build();
    }
    return DescribeStreamResponse.builder()
        .streamDescription(
            StreamDescription.builder()
                .streamName(request.streamName())
                .streamStatus(StreamStatus.ACTIVE)
                .build())
        .build();
  }

  @Override
  public CreateStreamResponse createStream(CreateStreamRequest request) {
    streams.put(request.streamName(), request.shardCount());
    createdStreams.add(request.streamName());
    return CreateStreamResponse.builder().build();
  }

  @Override
  public synchronized PutRecordsResponse putRecords(PutRecordsRequest request) {
    List<String> call = new ArrayList<String>();
    for (PutRecordsRequestEntry entry : request.records()) {
      call.add(entry.data().asString(StandardCharsets.UTF_8));
    }
    putCalls.add(call);
    if (call.isEmpty()) {
      // Like the real service: a PutRecords request needs at least one record.
      throw new IllegalArgumentException("PutRecords needs at least one record");
    }
    if (throwPuts > 0) {
      throwPuts--;
      throw new IllegalStateException("simulated PutRecords failure");
    }
    if (!streams.containsKey(request.streamName())) {
      throw ResourceNotFoundException.builder()
          .message("Stream " + request.streamName() + " not found")
          .build();
    }
    boolean failAll = failAllPuts > 0;
    if (failAll) {
      failAllPuts--;
    }
    List<PutRecordsResultEntry> results = new ArrayList<PutRecordsResultEntry>();
    int failed = 0;
    for (int i = 0; i < call.size(); i++) {
      String data = call.get(i);
      if (failAll || failOnce.remove(data)) {
        failed++;
        results.add(
            PutRecordsResultEntry.builder()
                .errorCode("ProvisionedThroughputExceededException")
                .errorMessage("simulated")
                .build());
      } else {
        PutRecordsRequestEntry entry = request.records().get(i);
        records.add(new FakeAmazonKinesis.Record(request.streamName(), entry.partitionKey(), data));
        results.add(PutRecordsResultEntry.builder().sequenceNumber(String.valueOf(i)).build());
      }
    }
    return PutRecordsResponse.builder().failedRecordCount(failed).records(results).build();
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void close() {
    closed = true;
  }
}

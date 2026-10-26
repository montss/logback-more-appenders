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

import com.amazonaws.services.kinesis.AbstractAmazonKinesis;
import com.amazonaws.services.kinesis.model.CreateStreamRequest;
import com.amazonaws.services.kinesis.model.CreateStreamResult;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.PutRecordsRequest;
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry;
import com.amazonaws.services.kinesis.model.PutRecordsResult;
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry;
import com.amazonaws.services.kinesis.model.ResourceNotFoundException;
import com.amazonaws.services.kinesis.model.StreamDescription;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory Kinesis Data Streams for the AWS SDK v1 appender. Created streams are ACTIVE at once.
 */
final class FakeAmazonKinesis extends AbstractAmazonKinesis {

  /** One record as the service accepted it. */
  static final class Record {
    final String stream;
    final String partitionKey;
    final String data;

    Record(String stream, String partitionKey, String data) {
      this.stream = stream;
      this.partitionKey = partitionKey;
      this.data = data;
    }

    @Override
    public String toString() {
      return stream + "[" + partitionKey + "] " + data;
    }
  }

  /** Shard count per stream. */
  final Map<String, Integer> streams = new ConcurrentHashMap<String, Integer>();

  final List<String> createdStreams = new CopyOnWriteArrayList<String>();
  /** The records of each PutRecords call, accepted or not. */
  final List<List<String>> putCalls = new CopyOnWriteArrayList<List<String>>();

  final List<Record> records = new CopyOnWriteArrayList<Record>();

  /** Number of PutRecords calls still to reject every record of. */
  volatile int failAllPuts;
  /** Number of PutRecords calls still to throw on. */
  volatile int throwPuts;
  /** Data of records to reject once, each. */
  final Set<String> failOnce = Collections.synchronizedSet(new HashSet<String>());

  volatile boolean shutdown;

  FakeAmazonKinesis withStream(String name) {
    streams.put(name, 1);
    return this;
  }

  /** The data of all accepted records, in order. */
  List<String> data() {
    List<String> all = new ArrayList<String>();
    for (Record record : records) {
      all.add(record.data);
    }
    return all;
  }

  @Override
  public DescribeStreamResult describeStream(String streamName) {
    return describeStream(new DescribeStreamRequest().withStreamName(streamName));
  }

  @Override
  public DescribeStreamResult describeStream(DescribeStreamRequest request) {
    if (!streams.containsKey(request.getStreamName())) {
      throw new ResourceNotFoundException("Stream " + request.getStreamName() + " not found");
    }
    return new DescribeStreamResult()
        .withStreamDescription(
            new StreamDescription()
                .withStreamName(request.getStreamName())
                .withStreamStatus("ACTIVE"));
  }

  @Override
  public CreateStreamResult createStream(CreateStreamRequest request) {
    streams.put(request.getStreamName(), request.getShardCount());
    createdStreams.add(request.getStreamName());
    return new CreateStreamResult();
  }

  @Override
  public synchronized PutRecordsResult putRecords(PutRecordsRequest request) {
    List<String> call = new ArrayList<String>();
    for (PutRecordsRequestEntry entry : request.getRecords()) {
      call.add(StandardCharsets.UTF_8.decode(entry.getData().duplicate()).toString());
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
    if (!streams.containsKey(request.getStreamName())) {
      throw new ResourceNotFoundException("Stream " + request.getStreamName() + " not found");
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
            new PutRecordsResultEntry()
                .withErrorCode("ProvisionedThroughputExceededException")
                .withErrorMessage("simulated"));
      } else {
        PutRecordsRequestEntry entry = request.getRecords().get(i);
        records.add(new Record(request.getStreamName(), entry.getPartitionKey(), data));
        results.add(new PutRecordsResultEntry().withSequenceNumber(String.valueOf(i)));
      }
    }
    return new PutRecordsResult().withFailedRecordCount(failed).withRecords(results);
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }
}

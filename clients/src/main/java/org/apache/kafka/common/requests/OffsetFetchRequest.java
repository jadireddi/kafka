/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common.requests;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.message.OffsetFetchRequestData;
import org.apache.kafka.common.message.OffsetFetchRequestData.OffsetFetchRequestTopic;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.types.Struct;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OffsetFetchRequest extends AbstractRequest {

    private static final List<OffsetFetchRequestTopic> ALL_TOPIC_PARTITIONS = null;
    public final OffsetFetchRequestData data;

    public static class Builder extends AbstractRequest.Builder<OffsetFetchRequest> {

        public final OffsetFetchRequestData data;

        public Builder(String groupId, List<TopicPartition> partitions) {
            super(ApiKeys.OFFSET_FETCH);

            final List<OffsetFetchRequestTopic> topics;
            if (partitions != null) {
                Map<String, OffsetFetchRequestTopic> offsetFetchRequestTopicMap = new HashMap<>();
                for (TopicPartition topicPartition : partitions) {
                    String topicName = topicPartition.topic();
                    OffsetFetchRequestTopic topic = offsetFetchRequestTopicMap.getOrDefault(
                        topicName, new OffsetFetchRequestTopic().setName(topicName));
                    topic.partitionIndexes().add(topicPartition.partition());
                    offsetFetchRequestTopicMap.put(topicName, topic);
                }
                topics = new ArrayList<>(offsetFetchRequestTopicMap.values());
            } else {
                // If passed in partition list is null, it is requesting offsets for all topic partitions.
                topics = ALL_TOPIC_PARTITIONS;
            }

            this.data = new OffsetFetchRequestData()
                            .setGroupId(groupId)
                            .setTopics(topics);
        }

        public static Builder allTopicPartitions(String groupId) {
            return new Builder(groupId, null);
        }

        public boolean isAllTopicPartitions() {
            return this.data.topics() == ALL_TOPIC_PARTITIONS;
        }

        @Override
        public OffsetFetchRequest build(short version) {
            if (isAllTopicPartitions() && version < 2)
                throw new UnsupportedVersionException("The broker only supports OffsetFetchRequest " +
                        "v" + version + ", but we need v2 or newer to request all topic partitions.");
            return new OffsetFetchRequest(data, version);
        }

        @Override
        public String toString() {
            return data.toString();
        }
    }

    public List<TopicPartition> partitions() {
        if (isAllPartitions()) {
            return null;
        }
        List<TopicPartition> partitions = new ArrayList<>();
        for (OffsetFetchRequestTopic topic : data.topics()) {
            for (Integer partitionIndex : topic.partitionIndexes()) {
                partitions.add(new TopicPartition(topic.name(), partitionIndex));
            }
        }
        return partitions;
    }

    public String groupId() {
        return data.groupId();
    }

    private OffsetFetchRequest(OffsetFetchRequestData data, short version) {
        super(ApiKeys.OFFSET_FETCH, version);
        this.data = data;
    }

    public OffsetFetchRequest(Struct struct, short version) {
        super(ApiKeys.OFFSET_FETCH, version);
        this.data = new OffsetFetchRequestData(struct, version);
    }

    public OffsetFetchResponse getErrorResponse(Errors error) {
        return getErrorResponse(AbstractResponse.DEFAULT_THROTTLE_TIME, error);
    }

    public OffsetFetchResponse getErrorResponse(int throttleTimeMs, Errors error) {
        short versionId = version();

        Map<TopicPartition, OffsetFetchResponse.PartitionData> responsePartitions = new HashMap<>();
        if (versionId < 2) {
            OffsetFetchResponse.PartitionData partitionError = new OffsetFetchResponse.PartitionData(
                    OffsetFetchResponse.INVALID_OFFSET,
                    Optional.empty(),
                    OffsetFetchResponse.NO_METADATA,
                    error);

            for (OffsetFetchRequestTopic topic : this.data.topics()) {
                for (int partitionIndex : topic.partitionIndexes()) {
                    responsePartitions.put(
                        new TopicPartition(topic.name(), partitionIndex), partitionError);
                }
            }
        }

        switch (versionId) {
            case 0:
            case 1:
            case 2:
                return new OffsetFetchResponse(error, responsePartitions);
            case 3:
            case 4:
            case 5:
                return new OffsetFetchResponse(throttleTimeMs, error, responsePartitions);
            default:
                throw new IllegalArgumentException(String.format("Version %d is not valid. Valid versions for %s are 0 to %d",
                        versionId, this.getClass().getSimpleName(), ApiKeys.OFFSET_FETCH.latestVersion()));
        }
    }

    @Override
    public OffsetFetchResponse getErrorResponse(int throttleTimeMs, Throwable e) {
        return getErrorResponse(throttleTimeMs, Errors.forException(e));
    }

    public static OffsetFetchRequest parse(ByteBuffer buffer, short version) {
        return new OffsetFetchRequest(ApiKeys.OFFSET_FETCH.parseRequest(version, buffer), version);
    }

    public boolean isAllPartitions() {
        return data.topics() == ALL_TOPIC_PARTITIONS;
    }

    @Override
    protected Struct toStruct() {
        return data.toStruct(version());
    }
}

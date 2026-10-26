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

open module com.sndyuk.logback.more.appenders {
  requires org.slf4j;
  requires static logback.core;
  requires static logback.classic;
  requires static logback.access;
  requires static fluent.logger;
  requires static fluency.core;
  requires static fluency.fluentd;
  requires static aws.java.sdk.core;
  requires static aws.java.sdk.logs;
  requires static aws.java.sdk.kinesis;
  requires static software.amazon.awssdk.core;
  requires static software.amazon.awssdk.auth;
  requires static software.amazon.awssdk.regions;
  requires static software.amazon.awssdk.services.kinesis;
  requires static software.amazon.awssdk.services.cloudwatchlogs;
}

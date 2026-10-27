Logback more appenders
==================================================
Is additional appenders for [Logback](http://logback.qos.ch/) and provide better performance and data consistency without any concern.

## About this fork

This is a maintained fork of [sndyuk/logback-more-appenders](https://github.com/sndyuk/logback-more-appenders),
published as `io.github.montss:logback-more-appenders`.

- **1.8.9** is identical to upstream `com.sndyuk:logback-more-appenders:1.8.9`. Only the groupId differs.
- Java packages and appender class names are unchanged. To switch, replace the groupId `com.sndyuk` with
  `io.github.montss`. Existing `logback.xml` / `logback-spring.xml` files keep working.
- Planned changes are in the [roadmap](ROADMAP.md).

Bug reports, feature requests and pull requests are welcome through
[GitHub Issues](https://github.com/montss/logback-more-appenders/issues).

## Appenders

> [!WARNING]
> **The AWS SDK for Java v1 appenders are deprecated** (`CloudWatchLogbackAppender`, `KinesisStreamLogbackAppender`).
> AWS SDK for Java v1 [reached end-of-support on December 31, 2025](https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/): since January 2026 it gets no more
> updates, including security fixes. Use the SDK v2 appenders (`CloudWatchLogbackAppenderV2`,
> `KinesisStreamLogbackAppenderV2`) instead. The SDK v1 appenders log a warning at start-up and will be removed in
> 2.0.0.

- [CloudWatch](https://aws.amazon.com/cloudwatch/)
    - depends on [cloudwatchlogs(v2)](https://aws.amazon.com/sdk-for-java/), or aws-java-sdk-logs(v1) [(DEPRECATED)](https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/).

- [Kinesis Stream](https://aws.amazon.com/kinesis/data-streams/)
    - depends on [kinesis(v2)](https://aws.amazon.com/sdk-for-java/), or aws-java-sdk-kinesis(v1) [(DEPRECATED)](https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/).

- [fluentd](http://fluentd.org/) [(DEPRECATED)](https://github.com/fluent/fluent-logger-java/issues/99) use fluency instead
    - depends on [fluent-logger for Java](https://github.com/fluent/fluent-logger-java).
    - Install fluentd before running logger.

- [fluency](https://github.com/komamitsu/fluency)
    - depends on [fluency](https://github.com/komamitsu/fluency).
    - Install fluentd before running logger.
    - Optional: send the SLF4J message template (`"User {} logged in"`) in its own field next to the formatted
      message, so log backends can group and count by template. The field is only added when the template differs
      from the message. Off by default:
      ```xml
      <captureMessageTemplate>true</captureMessageTemplate>
      <!-- Optional, default "message_template" -->
      <messageTemplateFieldName>message_template</messageTemplateFieldName>
      ```

### Java 9 module (Jigsaw) variant
This fork does not publish the `-JAVA9MODULE_SLF4J17` variant. Upstream stopped publishing it with 1.8.9 as well.

## Spring Boot compatibility

| logback-more-appenders | Spring Boot | Java | Example |
|---|---|---|---|
| 1.x | 2.7 | 8, 17 | [spring-boot-2.7/logback-spring.xml](examples/spring-boot-2.7/logback-spring.xml) |
| 1.x | 3.0, 3.1 | 17 | [spring-boot-3.x/logback-spring.xml](examples/spring-boot-3.x/logback-spring.xml) |
| 2.x (planned) | 3.x and later | 17 and later | |

Logback and SLF4J versions come from Spring Boot's dependency management, not from this library.
Every row is checked in CI on each change: a minimal Spring Boot app runs with the example config and must deliver
its logs to Fluentd through `FluencyLogbackAppender` without any Logback warning or error.

Use the example that matches your Spring Boot version. Do not set `<name>` inside the appender. On Spring Boot 3
(Logback 1.4+) it renames the appender, `<appender-ref>` no longer finds it, and the root logger ends up without
appenders: nothing is logged, not even to the console, and no error is reported.

## Installing

### Install jars from Maven2 repository
Configure your pom.xml:

    <dependencies>
    
      <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>${logback.version}</version>
      </dependency>
    
      <dependency>
        <groupId>io.github.montss</groupId>
        <artifactId>logback-more-appenders</artifactId>
        <version>1.10.0</version>
      </dependency>

      <!-- [Optional] If you use The CloudWatch V2 appender, You need to add the dependency(cloudwatchlogs). -->
      <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>cloudwatchlogs</artifactId>
        <version>${aws-v2.version}</version>
      </dependency>

      <!-- [Optional, DEPRECATED: AWS SDK v1 reached end-of-support] If you use The CloudWatch appender, You need to add the dependency(aws-java-sdk-logs). -->
      <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-java-sdk-logs</artifactId>
        <version>${aws.version}</version>
      </dependency>

      <!-- [Optional] If you use The Kinesis V2 appender, You need to add the dependency(kinesis). -->
      <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>kinesis</artifactId>
        <version>${aws-v2.version}</version>
        <optional>true</optional>
      </dependency>

      <!-- [Optional, DEPRECATED: AWS SDK v1 reached end-of-support] If you use The Kinesis appender, You need to add the dependency(aws-java-sdk-kinesis). -->
      <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-java-sdk-kinesis</artifactId>
        <version>${aws.version}</version>
      </dependency>

      <!-- [Optional] If you use The Fluentd appender, You need to add the dependency(fluent-logger). -->
      <dependency>
        <groupId>org.fluentd</groupId>
        <artifactId>fluent-logger</artifactId>
        <version>${fluentd.logger.version}</version>
      </dependency>
    
      <!-- [Optional] If you use The Fluency appender, You need to add the dependency(fluency). -->
      <dependency>
        <groupId>org.komamitsu</groupId>
        <artifactId>fluency-core</artifactId>
        <version>${fluency.version}</version>
        <optional>true</optional>
      </dependency>
      <dependency>
        <groupId>org.komamitsu</groupId>
        <artifactId>fluency-fluentd</artifactId>
        <version>${fluency.version}</version>
        <optional>true</optional>
      </dependency>
    
    </dependencies>

### Configure your logback.xml
You can find the sample configuration files here:

- [AWS services, SDK v2 - logback-appenders-aws-v2.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-aws-v2.xml)
- [AWS services, SDK v1 (DEPRECATED) - logback-appenders-aws.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-aws.xml)
- [Fluentd - logback-appenders-fluentd.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-fluentd.xml)
- [Stdout/err - logback-appenders-std.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-std.xml)

- [logback.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback.xml)
- [Spring Boot 2.7 - logback-spring.xml](examples/spring-boot-2.7/logback-spring.xml)
- [Spring Boot 3.x - logback-spring.xml](examples/spring-boot-3.x/logback-spring.xml)

### 


### License
[Apache License, Version 2.0](LICENSE)

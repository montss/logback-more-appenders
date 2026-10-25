Logback more appenders
==================================================
is additional appenders for [Logback](http://logback.qos.ch/) and provide better performance and data consistency without any concern.

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

- [CloudWatch](https://aws.amazon.com/cloudwatch/)
    - depends on [cloudwatchlogs(v2) or aws-java-sdk-logs(v1)](http://aws.amazon.com/sdkforjava/).

- [Kinesis Stream](https://aws.amazon.com/kinesis/data-streams/)
    - depends on [kinesis(v2) or aws-java-sdk-kinesis(v1)](http://aws.amazon.com/sdkforjava/).

- [fluentd](http://fluentd.org/) [(DEPRECATED)](https://github.com/fluent/fluent-logger-java/issues/99) use fluency instead
    - depends on [fluent-logger for Java](https://github.com/fluent/fluent-logger-java).
    - Install fluentd before running logger.

- [fluency](https://github.com/komamitsu/fluency)
    - depends on [fluency](https://github.com/komamitsu/fluency).
    - Install fluentd before running logger.

### Java 9 module (Jigsaw) variant
This fork does not publish the `-JAVA9MODULE_SLF4J17` variant. Upstream stopped publishing it with 1.8.9 as well.

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
        <version>1.8.9</version>
      </dependency>

      <!-- [Optional] If you use The CloudWatch V2 appender, You need to add the dependency(cloudwatchlogs). -->
      <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>cloudwatchlogs</artifactId>
        <version>${aws-v2.version}</version>
      </dependency>

      <!-- [Optional] If you use The CloudWatch appender, You need to add the dependency(aws-java-sdk-logs). -->
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

      <!-- [Optional] If you use The Kinesis appender, You need to add the dependency(aws-java-sdk-kinesis). -->
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

- [AWS services - logback-appenders-aws.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-aws.xml)
- [Fluentd - logback-appenders-fluentd.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-fluentd.xml)
- [Stdout/err - logback-appenders-std.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback-appenders-std.xml)

- [logback.xml](https://github.com/montss/logback-more-appenders/blob/main/src/test/resources/logback.xml)

### 


### License
[Apache License, Version 2.0](LICENSE)

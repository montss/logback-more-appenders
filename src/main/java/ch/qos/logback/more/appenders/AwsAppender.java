/*
 * Copyright (c) 2018 sndyuk <sanada@sndyuk.com>
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

import ch.qos.logback.core.AppenderBase;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

/**
 * Base class for the AWS SDK v1 appenders. On start it resolves the AWS credentials from the
 * credentials file, the profile, or the default credentials provider chain, in that order.
 *
 * @param <E> the type of the logged events
 * @deprecated The AWS SDK for Java v1 reached <a
 *     href="https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/">end-of-support</a>
 *     on 2025-12-31. Use {@link AwsAppenderV2} instead. Removed in 2.0.0.
 */
@Deprecated
public abstract class AwsAppender<E> extends AppenderBase<E> {

  protected AwsConfig config;
  protected AWSCredentialsProvider credentialsProvider;
  protected AWSCredentials credentials;

  @Override
  public void start() {
    try {
      if (config.getCredentialFilePath() != null && config.getCredentialFilePath().length() > 0) {
        this.credentials =
            new PropertiesCredentials(
                getClass().getClassLoader().getResourceAsStream(config.getCredentialFilePath()));
      } else if (config.getProfile() != null && config.getProfile().length() > 0) {
        this.credentialsProvider = new ProfileCredentialsProvider(config.getProfile());
      } else {
        this.credentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();
      }
      super.start();
    } catch (Exception e) {
      addWarn(
          "Could not initialize "
              + AwsAppender.class.getCanonicalName()
              + " ( will try to initialize again later ): "
              + e);
    }
  }

  /**
   * Reports that the appender uses the AWS SDK for Java v1 and names its SDK v2 replacement.
   *
   * @param replacement the SDK v2 appender to use instead
   */
  protected void warnSdkV1Deprecated(Class<?> replacement) {
    addWarn(
        getClass().getName()
            + " uses the AWS SDK for Java v1, which reached end-of-support on 2025-12-31 and gets"
            + " no more fixes, including security fixes. It is deprecated and will be removed in"
            + " 2.0.0. Use "
            + replacement.getName()
            + " instead.");
  }

  /** AWS settings: region, and either a credentials file on the classpath or a profile name. */
  public static class AwsConfig {
    private String credentialFilePath;
    private String region;
    private String profile;

    public void setCredentialFilePath(String credentialFilePath) {
      this.credentialFilePath = credentialFilePath;
    }

    public String getCredentialFilePath() {
      return credentialFilePath;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getProfile() {
      return profile;
    }

    public void setProfile(String profile) {
      this.profile = profile;
    }
  }
}

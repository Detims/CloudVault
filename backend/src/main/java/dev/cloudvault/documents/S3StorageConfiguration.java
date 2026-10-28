package dev.cloudvault.documents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnExpression("'${cloudvault.storage.s3.bucket:}' != ''")
class S3StorageConfiguration {
    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(@Value("${cloudvault.storage.s3.region}") String region,
                           AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentialsProvider).build();
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${cloudvault.storage.s3.region}") String region,
                      AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder().region(Region.of(region)).credentialsProvider(credentialsProvider).build();
    }
}

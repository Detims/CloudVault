package dev.cloudvault.documents;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnExpression("'${cloudvault.storage.s3.bucket:}' != ''")
class S3ObjectStorage implements ObjectStorage {
    private static final DateTimeFormatter POLICY_EXPIRATION_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final S3Presigner presigner;
    private final S3Client client;
    private final AwsCredentialsProvider credentialsProvider;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final String region;
    private final Duration urlLifetime;

    S3ObjectStorage(S3Presigner presigner, S3Client client, AwsCredentialsProvider credentialsProvider,
                    ObjectMapper objectMapper,
                    @Value("${cloudvault.storage.s3.bucket}") String bucket,
                    @Value("${cloudvault.storage.s3.region}") String region,
                    @Value("${cloudvault.storage.s3.presign-minutes}") long presignMinutes) {
        this.presigner = presigner;
        this.client = client;
        this.credentialsProvider = credentialsProvider;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.region = region;
        this.urlLifetime = Duration.ofMinutes(presignMinutes);
    }

    @Override
    public UploadLink createUploadLink(DocumentRecord document) {
        var credentials = credentialsProvider.resolveCredentials();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        var dateStamp = amzDate.substring(0, 8);
        var scope = dateStamp + "/" + region + "/s3/aws4_request";
        var credential = credentials.accessKeyId() + "/" + scope;
        var expiresAt = Instant.now().plus(urlLifetime);

        var fields = new LinkedHashMap<String, String>();
        fields.put("key", document.getObjectKey());
        fields.put("Content-Type", document.getContentType());
        fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", amzDate);
        if (credentials instanceof AwsSessionCredentials session) {
            fields.put("x-amz-security-token", session.sessionToken());
        }

        var conditions = new ArrayList<Object>();
        conditions.add(Map.of("bucket", bucket));
        conditions.add(Map.of("key", document.getObjectKey()));
        conditions.add(Map.of("Content-Type", document.getContentType()));
        conditions.add(List.of("content-length-range", document.getSizeBytes(), document.getSizeBytes()));
        fields.forEach((name, value) -> conditions.add(Map.of(name, value)));

        var policy = new LinkedHashMap<String, Object>();
        policy.put("expiration", POLICY_EXPIRATION_FORMAT.format(expiresAt));
        policy.put("conditions", conditions);

        try {
            var encodedPolicy = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(policy));
            fields.put("policy", encodedPolicy);
            fields.put("x-amz-signature", HexFormat.of().formatHex(signPolicy(credentials, dateStamp, encodedPolicy)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not create S3 upload policy", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign S3 upload policy", exception);
        }

        return new UploadLink("https://" + bucket + ".s3." + region + ".amazonaws.com/", Map.copyOf(fields), expiresAt);
    }

    @Override
    public DownloadLink createDownloadLink(DocumentRecord document) {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(document.getObjectKey())
            .responseContentDisposition("attachment; filename=\"" + safeHeaderFileName(document.getFileName()) + "\"")
            .build();
        var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(urlLifetime)
            .getObjectRequest(request)
            .build());
        return new DownloadLink(signed.url().toString(), Instant.now().plus(urlLifetime));
    }

    @Override
    public long uploadedSize(DocumentRecord document) {
        try {
            return client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(document.getObjectKey())
                .build()).contentLength();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded object was not found");
            }
            throw exception;
        }
    }

    @Override
    public void delete(DocumentRecord document) {
        client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(document.getObjectKey())
            .build());
    }

    private String safeHeaderFileName(String name) {
        return name.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private byte[] signPolicy(AwsCredentials credentials, String dateStamp, String encodedPolicy)
        throws GeneralSecurityException {
        var dateKey = hmac(("AWS4" + credentials.secretAccessKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
        var regionKey = hmac(dateKey, region);
        var serviceKey = hmac(regionKey, "s3");
        var signingKey = hmac(serviceKey, "aws4_request");
        return hmac(signingKey, encodedPolicy);
    }

    private byte[] hmac(byte[] key, String value) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}

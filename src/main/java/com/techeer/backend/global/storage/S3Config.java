package com.techeer.backend.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    /**
     * 자격 증명은 SDK 기본 체인으로 찾는다. 로컬은 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY 환경변수,
     * EC2 는 인스턴스에 붙인 IAM 역할을 쓴다. 자격 증명은 첫 요청 때 찾으므로 키가 없어도 서버는 뜬다.
     */
    @Bean
    public S3Client s3Client(@Value("${storage.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}

package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alibaba.cloud")
public class AlibabaCloudProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String bucketPrivate;
}

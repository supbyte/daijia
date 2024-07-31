package com.atguigu.daijia.driver.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * Minio文件上传
     */
    String upload(MultipartFile file);
}

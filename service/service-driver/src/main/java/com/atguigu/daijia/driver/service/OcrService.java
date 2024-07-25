package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import org.springframework.web.multipart.MultipartFile;

public interface OcrService {


    /**
     * 腾讯云-身份证识别
     */
    IdCardOcrVo idCardOcrByTencent(MultipartFile file);

    /**
     * 腾讯云-驾驶证识别
     */
    DriverLicenseOcrVo driverLicenseOcrByTencent(MultipartFile file);

    /**
     * 阿里云-身份证识别
     */
    IdCardOcrVo idCardOcrByAlibaba(MultipartFile file);

    /**
     * 阿里云-驾驶证识别
     */
    DriverLicenseOcrVo driverLicenseOcrByAlibaba(MultipartFile file);
}

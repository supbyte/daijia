package com.atguigu.daijia.driver.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeDrivingLicenseRequest;
import com.aliyun.ocr_api20210707.models.RecognizeDrivingLicenseResponse;
import com.aliyun.ocr_api20210707.models.RecognizeIdcardRequest;
import com.aliyun.ocr_api20210707.models.RecognizeIdcardResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.AlibabaCloudProperties;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.entity.aliyun.*;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import com.qcloud.cos.utils.Base64;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.DriverLicenseOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.DriverLicenseOCRResponse;
import com.tencentcloudapi.ocr.v20181119.models.IDCardOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.IDCardOCRResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.joda.time.format.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OcrServiceImpl implements OcrService {

    @Resource
    private TencentCloudProperties tencentCloudProperties;
    @Resource
    private AlibabaCloudProperties alibabaCloudProperties;
    @Resource
    private CosService cosService;


    @Override
    public IdCardOcrVo idCardOcrByTencent(MultipartFile file) {

        try{
            // 图片转Base64格式字符串
            String base64 = Base64.encodeAsString(file.getBytes());
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("ocr.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            OcrClient client = new OcrClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            IDCardOCRRequest req = new IDCardOCRRequest();
            // 设置文件
            req.setImageBase64(base64);

            // 返回的resp是一个IDCardOCRResponse的实例，与请求对象对应
            IDCardOCRResponse resp = client.IDCardOCR(req);

            //转换为IdCardOcrVo对象
            IdCardOcrVo idCardOcrVo = new IdCardOcrVo();
            if (StringUtils.hasText(resp.getName())) {
                //身份证正面
                idCardOcrVo.setName(resp.getName());
                idCardOcrVo.setGender("男".equals(resp.getSex()) ? "1" : "2");
                idCardOcrVo.setBirthday(DateTimeFormat.forPattern("yyyy/MM/dd").parseDateTime(resp.getBirth()).toDate());
                idCardOcrVo.setIdcardNo(resp.getIdNum());
                idCardOcrVo.setIdcardAddress(resp.getAddress());

                //上传身份证正面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardFrontUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //身份证反面
                //证件有效期："2010.07.21-2020.07.21"
                String idcardExpireString = resp.getValidDate().split("-")[1];
                idCardOcrVo.setIdcardExpire(DateTimeFormat.forPattern("yyyy.MM.dd").parseDateTime(idcardExpireString).toDate());
                //上传身份证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardBackUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardBackShowUrl(cosUploadVo.getShowUrl());
            }
            return idCardOcrVo;
        } catch (Exception e) {
            log.error(ExceptionUtils.getMessage(e));
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    @Override
    public DriverLicenseOcrVo driverLicenseOcrByTencent(MultipartFile file) {
        try{
            //图片转换base64格式字符串
            String fileBase64 = Base64.encodeAsString(file.getBytes());

            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(),
                    tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("ocr.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            OcrClient client = new OcrClient(cred, tencentCloudProperties.getRegion(),
                    clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DriverLicenseOCRRequest req = new DriverLicenseOCRRequest();
            req.setImageBase64(fileBase64);

            // 返回的resp是一个DriverLicenseOCRResponse的实例，与请求对象对应
            DriverLicenseOCRResponse resp = client.DriverLicenseOCR(req);

            //封装到vo对象里面
            DriverLicenseOcrVo driverLicenseOcrVo = new DriverLicenseOcrVo();
            if (StringUtils.hasText(resp.getName())) {
                //驾驶证正面
                //驾驶证名称要与身份证名称一致
                driverLicenseOcrVo.setName(resp.getName());
                driverLicenseOcrVo.setDriverLicenseClazz(resp.getClass_());
                driverLicenseOcrVo.setDriverLicenseNo(resp.getCardCode());
                driverLicenseOcrVo.setDriverLicenseIssueDate(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(resp.getDateOfFirstIssue()).toDate());
                driverLicenseOcrVo.setDriverLicenseExpire(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(resp.getEndDate()).toDate());

                //上传驾驶证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "driverLicense");
                driverLicenseOcrVo.setDriverLicenseFrontUrl(cosUploadVo.getUrl());
                driverLicenseOcrVo.setDriverLicenseFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //驾驶证反面
                //上传驾驶证反面图片到腾讯云cos
                CosUploadVo cosUploadVo =  cosService.upload(file, "driverLicense");
                driverLicenseOcrVo.setDriverLicenseBackUrl(cosUploadVo.getUrl());
                driverLicenseOcrVo.setDriverLicenseBackShowUrl(cosUploadVo.getShowUrl());
            }

            return driverLicenseOcrVo;
        } catch (Exception e) {
            log.error(ExceptionUtils.getMessage(e));
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    private Client createClient() throws Exception {
        // 工程代码泄露可能会导致 AccessKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考。
        // 建议使用更安全的 STS 方式，更多鉴权访问方式请参见：https://help.aliyun.com/document_detail/378657.html。
        Config config = new Config()
                // 地区
                .setRegionId(alibabaCloudProperties.getRegion())
                // 必填，ALIBABA_CLOUD_ACCESS_KEY_ID。
                .setAccessKeyId(alibabaCloudProperties.getAccessKeyId())
                // 必填，ALIBABA_CLOUD_ACCESS_KEY_SECRET。
                .setAccessKeySecret(alibabaCloudProperties.getAccessKeySecret());
        // Endpoint 请参考 https://api.aliyun.com/product/ocr-api
        config.endpoint = "ocr-api.cn-hangzhou.aliyuncs.com";
        return new Client(config);
    }

    @Override
    public IdCardOcrVo idCardOcrByAlibaba(MultipartFile file) {

        try {
            Client client = createClient();

            InputStream bodyStream = file.getInputStream();
            RecognizeIdcardRequest recognizeIdcardRequest = new RecognizeIdcardRequest();
            recognizeIdcardRequest.setBody(bodyStream);

            RuntimeOptions runtime = new RuntimeOptions();

            // 复制代码运行请自行打印 API 的返回值
            RecognizeIdcardResponse resp = client.recognizeIdcardWithOptions(recognizeIdcardRequest, runtime);
            String data = resp.getBody().getData();
            IdCardRoot idCardRoot = JSONObject.parseObject(data, IdCardRoot.class);
            IdCardFace face = idCardRoot.getData().getFace();
            IdCardBack back = idCardRoot.getData().getBack();
            //转换为IdCardOcrVo对象
            IdCardOcrVo idCardOcrVo = new IdCardOcrVo();

            if (face!=null) {
                //身份证正面
                idCardOcrVo.setName(face.getData().getName());
                idCardOcrVo.setGender("男".equals(face.getData().getSex()) ? "1" : "2");
                idCardOcrVo.setBirthday(DateTimeFormat.forPattern("yyyy年MM月dd日").parseDateTime(face.getData().getBirthDate()).toDate());
                idCardOcrVo.setIdcardNo(face.getData().getIdNumber());
                idCardOcrVo.setIdcardAddress(face.getData().getAddress());

                //上传身份证正面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardFrontUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //身份证反面
                //证件有效期："2010.07.21-2020.07.21"
                String idcardExpireString = back.getData().getValidPeriod().split("-")[1];
                idCardOcrVo.setIdcardExpire(DateTimeFormat.forPattern("yyyy.MM.dd").parseDateTime(idcardExpireString).toDate());
                //上传身份证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardBackUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardBackShowUrl(cosUploadVo.getShowUrl());
            }
            return idCardOcrVo;
        } catch (Exception e) {
            log.error(ExceptionUtils.getMessage(e));
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    @Override
    public DriverLicenseOcrVo driverLicenseOcrByAlibaba(MultipartFile file) {
        try {
            Client client = createClient();

            InputStream bodyStream = file.getInputStream();
            RecognizeDrivingLicenseRequest recognizeDrivingLicenseRequest = new RecognizeDrivingLicenseRequest();
            recognizeDrivingLicenseRequest.setBody(bodyStream);

            RuntimeOptions runtime = new RuntimeOptions();

            // 复制代码运行请自行打印 API 的返回值
            RecognizeDrivingLicenseResponse resp = client.recognizeDrivingLicenseWithOptions(recognizeDrivingLicenseRequest, runtime);

            String data = resp.getBody().getData();
            DriverLicenseRoot driverLicenseRoot = JSONObject.parseObject(data, DriverLicenseRoot.class);
            DriverLicenseFace face = driverLicenseRoot.getData().getFace();
            DriverLicenseBack back = driverLicenseRoot.getData().getBack();

            //封装到vo对象里面
            DriverLicenseOcrVo driverLicenseOcrVo = new DriverLicenseOcrVo();
            if (face != null) {
                //驾驶证正面
                //驾驶证名称要与身份证名称一致
                driverLicenseOcrVo.setName(face.getData().getName());
                driverLicenseOcrVo.setDriverLicenseClazz(face.getData().getApprovedType());
                driverLicenseOcrVo.setDriverLicenseNo(face.getData().getLicenseNumber());
                driverLicenseOcrVo.setDriverLicenseIssueDate(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(face.getData().getInitialIssueDate()).toDate());
                //"validPeriod": "2015-08-07至2025-08-07"
                driverLicenseOcrVo.setDriverLicenseExpire(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(face.getData().getValidPeriod().split("至")[1]).toDate());

                //上传驾驶证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "driverLicense");
                driverLicenseOcrVo.setDriverLicenseFrontUrl(cosUploadVo.getUrl());
                driverLicenseOcrVo.setDriverLicenseFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //驾驶证反面
                //上传驾驶证反面图片到腾讯云cos
                CosUploadVo cosUploadVo =  cosService.upload(file, "driverLicense");
                driverLicenseOcrVo.setDriverLicenseBackUrl(cosUploadVo.getUrl());
                driverLicenseOcrVo.setDriverLicenseBackShowUrl(cosUploadVo.getShowUrl());
            }

            return driverLicenseOcrVo;
        } catch (Exception e) {
            log.error(ExceptionUtils.getMessage(e));
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }
}

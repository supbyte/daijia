package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CiService;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ciModel.auditing.*;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.utils.Base64;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CiServiceImpl implements CiService {

    @Resource
    private TencentCloudProperties tencentCloudProperties;

    @Override
    public Boolean imageAuditing(String path) {
        //1.创建任务请求对象
        ImageAuditingRequest request = new ImageAuditingRequest();
        //2.添加请求参数 参数详情请见api接口文档
        //2.1设置请求bucket
        request.setBucketName(tencentCloudProperties.getBucketPrivate());
        //2.2设置审核类型
        request.setDetectType("porn,ads");
        //2.3设置bucket中的图片位置
        request.setObjectKey("1.png");
        //3.调用接口,获取任务响应对象
        COSClient client = this.getCosClient();
        ImageAuditingResponse response = client.imageAuditing(request);
        client.shutdown();
        //用于返回该审核场景的审核结果，返回值：0：正常。1：确认为当前场景的违规内容。2：疑似为当前场景的违规内容。
        return response.getPornInfo().getHitFlag().equals("0")
                && response.getAdsInfo().getHitFlag().equals("0")
                && response.getTerroristInfo().getHitFlag().equals("0")
                && response.getPoliticsInfo().getHitFlag().equals("0");
    }

    @Override
    public TextAuditingVo textAuditing(String content) {
        // 文本为空直接审核通过
        TextAuditingVo textAuditingVo = new TextAuditingVo();
        if (!StringUtils.hasText(content)){
            textAuditingVo.setResult("0");
            return textAuditingVo;
        }

        COSClient cosClient = this.getCosClient();

        //1.创建任务请求对象
        TextAuditingRequest request = new TextAuditingRequest();
        //2.添加请求参数 参数详情请见 API 接口文档
        request.setBucketName(tencentCloudProperties.getBucketPrivate());
        //2.1.1设置请求内容,文本内容的Base64编码
        String contentBase64 = Base64.encodeAsString(content.getBytes());
        request.getInput().setContent(contentBase64);
        request.getConf().setDetectType("all");
        //3.调用接口,获取任务响应对象
        TextAuditingResponse response = cosClient.createAuditingTextJobs(request);
        AuditingJobsDetail detail = response.getJobsDetail();
        if ("Success".equals(detail.getState())) {
            //检测结果: 0（审核正常），1 （判定为违规敏感文件），2（疑似敏感，建议人工复核）。
            String result = detail.getResult();

            //违规关键词
            StringBuilder keywords = new StringBuilder();
            List<SectionInfo> sectionInfoList = detail.getSectionList();
            for (SectionInfo info : sectionInfoList) {
                String pornInfoKeyword = info.getPornInfo().getKeywords();
                String illegalInfoKeyword = info.getIllegalInfo().getKeywords();
                String abuseInfoKeyword = info.getAbuseInfo().getKeywords();
                // 违规色情关键字
                if (!pornInfoKeyword.isEmpty()) {
                    keywords.append(pornInfoKeyword).append(",");
                }
                // 违规非法关键字
                if (!illegalInfoKeyword.isEmpty()) {
                    keywords.append(illegalInfoKeyword).append(",");
                }
                // 违规风险关键字
                if (!abuseInfoKeyword.isEmpty()) {
                    keywords.append(abuseInfoKeyword).append(",");
                }
            }
            textAuditingVo.setResult(result);
            textAuditingVo.setKeywords(keywords.toString());
        }
        return textAuditingVo;
    }

    private COSClient getCosClient() {
        // 1 初始化用户身份信息（secretId, secretKey）。
        String secretId = tencentCloudProperties.getSecretId();
        String secretKey = tencentCloudProperties.getSecretKey();
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 2 设置 bucket 的地域, COS 地域
        Region region = new Region(tencentCloudProperties.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        // 这里建议设置使用 https 协议
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 3 生成 cos 客户端。
        return new COSClient(cred, clientConfig);
    }
}

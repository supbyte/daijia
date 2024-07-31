package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.order.TextAuditingVo;

public interface CiService {

    /**
     * 腾讯云图片审核接口
     */
    Boolean imageAuditing(String path);

    /**
     * 文本审核接口
     */
    TextAuditingVo textAuditing(String content);
}

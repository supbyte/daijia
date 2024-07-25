package com.atguigu.daijia.model.entity.aliyun;

import lombok.Data;

/**
 * @author mucd
 */
@Data
public class IdCardDataBackData {
    /**
     * 办理地址
     */
    private String issueAuthority;

    /**
     * 生效-到期
     */
    private String validPeriod;
}

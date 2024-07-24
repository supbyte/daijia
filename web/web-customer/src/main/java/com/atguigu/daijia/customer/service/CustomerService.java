package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;

public interface CustomerService {


    /**
     * 小程序登录
     */
    String login(String code);

    /**
     * 获取客户基本信息（原始）
     */
    CustomerLoginVo getCustomerInfo(String token);

    /**
     * 获取客户基本信息（基于AOP登录判断）
     */
    CustomerLoginVo getCustomerInfo(Long userId);

    /**
     * 更新客户微信手机号码
     */
    Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm);
}

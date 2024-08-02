package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CustomerInfoService extends IService<CustomerInfo> {

    /**
     * 小程序用户登录
     */
    Long login(String code);

    /**
     * 获取客户登录信息
     */
    CustomerLoginVo getCustomerInfo(Long customerId);

    /**
     * 更新客户手机号码
     */
    Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm);

    /**
     * 获取客户OpenId
     */
    String getCustomerOpenId(Long customerId);
}

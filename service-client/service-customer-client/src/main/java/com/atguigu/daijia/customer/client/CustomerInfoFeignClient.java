package com.atguigu.daijia.customer.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "service-customer")
public interface CustomerInfoFeignClient {

    /**
     * 小程序授权登录
     */
    @GetMapping("/customer/info/login/{code}")
    public Result<Long> login(@PathVariable String code);

    /**
     * 获取客户登录信息
     */
    @GetMapping("/customer/info/getCustomerLoginInfo/{customerId}")
    public Result<CustomerLoginVo> getCustomerInfo(@PathVariable Long customerId);

    /**
     * 更新客户微信手机号码
     */
    @PostMapping("/customer/info/updateWxPhoneNumber")
    Result<Boolean> updateWxPhoneNumber(@RequestBody UpdateWxPhoneForm updateWxPhoneForm);

}
package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.login.GuiguLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.customer.service.OrderService;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "客户API接口管理")
@RestController
@RequestMapping("/customer")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerController {

    @Resource
    private CustomerService customerService;
    @Resource
    private OrderService orderService;

    //微信小程序登录接口
    @Operation(summary = "小程序授权登录")
    @GetMapping("/login/{code}")
    public Result<String> login(@PathVariable String code) {
        return Result.ok(customerService.login(code));
    }

//    /**
//     * 获取客户登录信息接口
//     */
//    @Operation(summary = "获取客户基本信息")
//    @GetMapping("/getCustomerLoginInfo")
//    public Result<CustomerLoginVo> getCustomerInfo(@RequestHeader(value = "token") String token){
//        return Result.ok(customerService.getCustomerInfo(token));
//    }

    /**
     * 获取客户登录信息接口
     */
    @Operation(summary = "获取客户基本信息")
    @GuiguLogin
    @GetMapping("/getCustomerLoginInfo")
    public Result<CustomerLoginVo> getCustomerInfo(){
        // 1.从ThreadLocal里面获取userId
        Long userId = AuthContextHolder.getUserId();
        return Result.ok(customerService.getCustomerInfo(userId));
    }

    @Operation(summary = "更新用户微信手机号")
    @GuiguLogin
    @PostMapping("/updateWxPhone")
    public Result updateWxPhone(@RequestBody UpdateWxPhoneForm updateWxPhoneForm) {
        updateWxPhoneForm.setCustomerId(AuthContextHolder.getUserId());
        return Result.ok(customerService.updateWxPhoneNumber(updateWxPhoneForm));
    }


}


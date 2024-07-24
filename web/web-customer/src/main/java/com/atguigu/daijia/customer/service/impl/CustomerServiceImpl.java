package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerServiceImpl implements CustomerService {

    @Resource
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public String login(String code) {
        // 1.用code调用远程服务
        Result<Long> result = customerInfoFeignClient.login(code);
        // 2.如果调用失败，则返回错误提示
        Integer codeResult = result.getCode();
        if (codeResult != 200) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 3.如果调用成功，则返回用户id，判断id是否为空
        Long customerId = result.getData();
        if (customerId == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 5.生成token
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        // 6.把用户id放到redis(token:customerId)，设置过期时间
        //redisTemplate.opsForValue().set(token,customerId.toString(),30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX+token,
                                            customerId.toString(),
                                            RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                                            TimeUnit.SECONDS);
        // 7.返回token
        return token;
    }

    /**
     *原始方式（不推荐使用）
     */
    @Override
    public CustomerLoginVo getCustomerInfo(String token) {
        // 1.根据token获取customerId
        String customerId = (String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX+token);
        if (!StringUtils.hasText(customerId)){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 2.根据customerId远程调用获取customerInfo
        Result<CustomerLoginVo> result = customerInfoFeignClient.getCustomerInfo(Long.parseLong(customerId));
        if (result.getCode()!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 3.返回VO对象
        return result.getData();
    }

    /**
     * aop注解方式（推荐使用）
     */
    @Override
    public CustomerLoginVo getCustomerInfo(Long userId) {
        // 1.根据customerId远程调用获取customerInfo
        Result<CustomerLoginVo> result = customerInfoFeignClient.getCustomerInfo(userId);
        if (result.getCode()!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 2.返回VO对象
        return result.getData();
    }

    @Override
    public Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm) {
        Result<Boolean> result = customerInfoFeignClient.updateWxPhoneNumber(updateWxPhoneForm);
        return result.getData();
    }
}

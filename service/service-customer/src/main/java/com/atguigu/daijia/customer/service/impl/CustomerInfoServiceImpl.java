package com.atguigu.daijia.customer.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.Watermark;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.alibaba.fastjson2.JSON;
import com.atguigu.daijia.customer.mapper.CustomerInfoMapper;
import com.atguigu.daijia.customer.mapper.CustomerLoginLogMapper;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.entity.customer.CustomerLoginLog;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.common.service.WxService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerInfoServiceImpl extends ServiceImpl<CustomerInfoMapper, CustomerInfo> implements CustomerInfoService {

    @Resource
    private WxMaService wxMaService;
    @Resource
    private CustomerInfoMapper customerInfoMapper;
    @Resource
    private CustomerLoginLogMapper customerLoginLogMapper;

    @Override
    public Long login(String code) {
        // 1.获取code值，使用微信工具包对象获取openId
        String openId = null;
        try {
            openId = wxMaService.getUserService().getSessionInfo(code).getOpenid();
        } catch (WxErrorException e) {
            throw new RuntimeException(e);
        }
        // 2.查询数据库是否有该openId，有则直接返回，无则新增（首次登录）
        LambdaQueryWrapper<CustomerInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CustomerInfo::getWxOpenId, openId);
        CustomerInfo customerInfo = customerInfoMapper.selectOne(queryWrapper);
        // 3.如果是第一次登录则添加用户信息到数据库
        if (customerInfo == null) {
            customerInfo = new CustomerInfo();
            customerInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            customerInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            customerInfo.setWxOpenId(openId);
            customerInfoMapper.insert(customerInfo);
        }
        // 4.记录登录日志信息
        CustomerLoginLog customerLoginLog = new CustomerLoginLog();
        customerLoginLog.setCustomerId(customerInfo.getId());
        customerLoginLog.setMsg("小程序登录");
        customerLoginLogMapper.insert(customerLoginLog);
        // 5.返回用户id
        return customerInfo.getId();
    }

    @Override
    public CustomerLoginVo getCustomerInfo(Long customerId) {
        // 1.查询用户信息
        CustomerInfo customerInfo = customerInfoMapper.selectById(customerId);
        // 2.封装
        CustomerLoginVo customerLoginVo = new CustomerLoginVo();
        BeanUtils.copyProperties(customerInfo, customerLoginVo);
        // 3.判断是否绑定手机号
        customerLoginVo.setIsBindPhone(StringUtils.hasText(customerInfo.getPhone()));
        // 4.返回
        return customerLoginVo;
    }

    @SneakyThrows
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm) {
        // 调用微信 API 获取用户的手机号
        //WxMaPhoneNumberInfo phoneInfo = wxMaService.getUserService().getPhoneNoInfo(updateWxPhoneForm.getCode());
        // 模拟调用微信 API 获取用户的手机号成功
        WxMaPhoneNumberInfo phoneInfo = new WxMaPhoneNumberInfo();
        phoneInfo.setPurePhoneNumber("13257975075");
        phoneInfo.setPhoneNumber("+8613257975075");
        phoneInfo.setCountryCode("CN");
        phoneInfo.setWatermark(new Watermark(String.valueOf(System.currentTimeMillis()),"wx7850ca8caa8b5829"));
        String phoneNumber = phoneInfo.getPurePhoneNumber();
        log.info("phoneInfo:{}", JSON.toJSONString(phoneInfo));

        CustomerInfo customerInfo = new CustomerInfo();
        customerInfo.setId(updateWxPhoneForm.getCustomerId());
        customerInfo.setPhone(phoneNumber);
        return customerInfoMapper.updateById(customerInfo)>0;
    }
}

package com.atguigu.daijia.customer.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 微信工具包对象
 */
@Component
public class WxConfigOperator {

    @Resource
    private WxConfigProperties wxConfigProperties;

    @Bean
    public WxMaService wxMaService() {

        // 初始化配置
        WxMaDefaultConfigImpl wx = new WxMaDefaultConfigImpl();
        wx.setAppid(wxConfigProperties.getAppId());
        wx.setSecret(wxConfigProperties.getSecret());

        // 初始化服务
        WxMaService wxMaService = new WxMaServiceImpl();
        wxMaService.setWxMaConfig(wx);
        return wxMaService;
    }
}

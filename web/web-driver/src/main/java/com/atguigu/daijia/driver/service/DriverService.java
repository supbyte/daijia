package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.driver.DriverLoginVo;

public interface DriverService {


    /**
     * 小程序司机端登录
     */
    String login(String code);

    /**
     * 获取司机登录信息
     */
    DriverLoginVo getDriverLoginInfo(Long driverId);
}

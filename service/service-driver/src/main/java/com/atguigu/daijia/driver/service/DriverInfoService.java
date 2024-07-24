package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface DriverInfoService extends IService<DriverInfo> {

    /**
     * 微信小程序司机端登录
     */
    Long login(String code);

    /**
     * 获取司机信息
     */
    DriverLoginVo getDriverInfo(Long driverId);
}

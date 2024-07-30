package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;

import java.util.List;

public interface OrderService {


    /**
     * 查询订单状态
     */
    Integer getOrderStatus(Long orderId);

    /**
     * 查询司机新订单数据
     */
    List<NewOrderDataVo> findNewOrderQueueData(Long driverId);

    /**
     * 司机抢单
     */
    Boolean robNewOrder(Long driverId, Long orderId);

    /**
     * 查找司机端当前订单
     */
    CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId);

    /**
     * 获取订单信息
     */
    OrderInfoVo getOrderInfo(Long orderId, Long driverId);

    /**
     * 计算最佳驾驶路线
     */
    DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm);

    /**
     * 根据订单id获取司机基本信息
     */
    DriverInfoVo getDriverInfo(Long orderId, Long customerId);
}

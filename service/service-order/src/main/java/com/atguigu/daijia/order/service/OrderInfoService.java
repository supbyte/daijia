package com.atguigu.daijia.order.service;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OrderInfoService extends IService<OrderInfo> {

    /**
     *保存订单信息
     */
    Long saveOrderInfo(OrderInfoForm orderInfoForm);

    /**
     *根据订单id获取订单状态
     */
    Integer getOrderStatus(Long orderId);

    /**
     * 司机抢单
     */
    Boolean robNewOrder(Long driverId, Long orderId);

    /**
     * 查找乘客端当前订单
     */
    CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId);

    /**
     * 查找司机端当前订单
     */
    CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId);

    /**
     * 司机到达起始点
     */
    Boolean driverArriveStartLocation(Long orderId, Long driverId);

    /**
     * 更新代驾车辆信息
     */
    Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm);

    /**
     * 开始代驾服务
     */
    Boolean startDriver(StartDriveForm startDriveForm);

    /**
     * 根据时间段获取订单数
     */
    Long getOrderNumByTime(String startTime, String endTime);

    /**
     * 结束代驾服务更新订单账单
     */
    Boolean endDrive(UpdateOrderBillForm updateOrderBillForm);

    /**
     * 获取乘客订单分页列表
     */
    PageVo findCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId);

    /**
     * 获取司机订单分页列表
     */
    PageVo findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId);

    /**
     * 根据订单id获取实际账单信息
     */
    OrderBillVo getOrderBillInfo(Long orderId);

    /**
     * 根据订单id获取实际分账信息
     */
    OrderProfitsharingVo getOrderProfitsharing(Long orderId);

    /**
     * 发送账单信息
     */
    Boolean sendOrderBillInfo(Long orderId, Long driverId);

    /**
     * 获取订单支付信息
     */
    OrderPayVo getOrderPayVo(String orderNo, Long customerId);

    /**
     * 更改订单支付状态
     */
    Boolean updateOrderPayStatus(String orderNo);

    /**
     * 获取订单的系统奖励
     */
    OrderRewardVo getOrderRewardFee(String orderNo);
}

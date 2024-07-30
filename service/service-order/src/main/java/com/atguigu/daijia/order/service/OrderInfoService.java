package com.atguigu.daijia.order.service;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
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
}

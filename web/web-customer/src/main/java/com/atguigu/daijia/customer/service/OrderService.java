package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;

public interface OrderService {

    /**
     *预估订单数据
     */
    ExpectOrderVo expectOrder(ExpectOrderForm expectOrderForm);

    /**
     * 乘客下单
     */
    Long submitOrder(SubmitOrderForm submitOrderForm);

    /**
     * 查询订单状态
     */
    Integer getOrderStatus(Long orderId);

    /**
     * 查找乘客端当前订单
     */
    CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId);

    /**
     * 查找订单详细信息
     */
    OrderInfoVo getOrderInfo(Long orderId, Long customerId);

    /**
     * 司机赶往代驾起始点：获取订单经纬度位置
     */
    OrderLocationVo getCacheOrderLocation(Long orderId);

    /**
     * 计算最佳驾驶线路
     */
    DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm);
}

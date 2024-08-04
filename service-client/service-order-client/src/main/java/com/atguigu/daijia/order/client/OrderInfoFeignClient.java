package com.atguigu.daijia.order.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;


@FeignClient(value = "service-order")
public interface OrderInfoFeignClient {

    /**
     * 乘客端查找当前订单
     */
    @GetMapping("/order/info/searchCustomerCurrentOrder/{customerId}")
    Result<CurrentOrderInfoVo> searchCustomerCurrentOrder(@PathVariable("customerId") Long customerId);

    /**
     * 司机端查找当前订单
     */
    @GetMapping("/order/info/searchDriverCurrentOrder/{driverId}")
    Result<CurrentOrderInfoVo> searchDriverCurrentOrder(@PathVariable("driverId") Long driverId);

    /**
     * 保存订单信息
     */
    @PostMapping("/order/info/saveOrderInfo")
    Result<Long> saveOrderInfo(@RequestBody OrderInfoForm orderInfoForm);

    /**
     * 根据订单id获取订单状态
     */
    @GetMapping("/order/info/getOrderStatus/{orderId}")
    Result<Integer> getOrderStatus(@PathVariable("orderId") Long orderId);

    /**
     * 司机抢单
     */
    @GetMapping("/order/info/robNewOrder/{driverId}/{orderId}")
    Result<Boolean> robNewOrder(@PathVariable("driverId") Long driverId, @PathVariable("orderId") Long orderId);

    /**
     * 根据订单id获取订单信息
     */
    @GetMapping("/order/info/getOrderInfo/{orderId}")
    Result<OrderInfo> getOrderInfo(@PathVariable("orderId") Long orderId);

    /**
     * 司机到达起始点
     */
    @GetMapping("/order/info/driverArriveStartLocation/{orderId}/{driverId}")
    Result<Boolean> driverArriveStartLocation(@PathVariable("orderId") Long orderId, @PathVariable("driverId") Long driverId);

    /**
     * 更新代驾车辆信息
     */
    @PostMapping("/order/info//updateOrderCart")
    Result<Boolean> updateOrderCart(@RequestBody UpdateOrderCartForm updateOrderCartForm);

    /**
     * 开始代驾服务
     */
    @PostMapping("/order/info/startDrive")
    Result<Boolean> startDrive(@RequestBody StartDriveForm startDriveForm);

    /**
     *  根据时间段获取订单数
     */
    @GetMapping("/order/info/getOrderNumByTime/{startTime}/{endTime}")
    Result<Long> getOrderNumByTime(@PathVariable("startTime") String startTime, @PathVariable("endTime") String endTime);

    /**
     * 结束代驾服务更新订单账单
     */
    @PostMapping("/order/info/endDrive")
    Result<Boolean> endDrive(@RequestBody UpdateOrderBillForm updateOrderBillForm);

    /**
     * 获取乘客订单分页列表
     */
    @GetMapping("/order/info/findCustomerOrderPage/{customerId}/{page}/{limit}")
    Result<PageVo> findCustomerOrderPage(@PathVariable("customerId") Long customerId,
                                         @PathVariable("page") Long page,
                                         @PathVariable("limit") Long limit);

    /**
     * 获取司机订单分页列表
     */
    @GetMapping("/order/info/findDriverOrderPage/{driverId}/{page}/{limit}")
    Result<PageVo> findDriverOrderPage(@PathVariable("driverId") Long driverId,
                                       @PathVariable("page") Long page,
                                       @PathVariable("limit") Long limit);

    /**
     * 根据订单id获取实际账单信息
     */
    @GetMapping("/order/info/getOrderBillInfo/{orderId}")
    Result<OrderBillVo> getOrderBillInfo(@PathVariable("orderId") Long orderId);

    /**
     * 根据订单id获取实际分账信息
     */
    @GetMapping("/order/info/getOrderProfitsharing/{orderId}")
    Result<OrderProfitsharingVo> getOrderProfitsharing(@PathVariable("orderId") Long orderId);

    /**
     * 司机发送账单信息
     */
    @GetMapping("/order/info/sendOrderBillInfo/{orderId}/{driverId}")
    Result<Boolean> sendOrderBillInfo(@PathVariable("orderId") Long orderId, @PathVariable("driverId") Long driverId);

    /**
     * 获取订单支付信息
     */
    @GetMapping("/order/info/getOrderPayVo/{orderNo}/{customerId}")
    Result<OrderPayVo> getOrderPayVo(@PathVariable("orderNo") String orderNo, @PathVariable("customerId") Long customerId);

    /**
     * 更改订单支付状态
     */
    @GetMapping("/order/info//updateOrderPayStatus/{orderNo}")
    Result<Boolean> updateOrderPayStatus(@PathVariable("orderNo") String orderNo);

    /**
     * 获取订单的系统奖励
     */
    @GetMapping("/order/info//getOrderRewardFee/{orderNo}")
    Result<OrderRewardVo> getOrderRewardFee(@PathVariable("orderNo") String orderNo);

    /**
     * 更新优惠券金额
     */
    @GetMapping("/order/info/updateCouponAmount/{orderId}/{couponAmount}")
    Result<Boolean> updateCouponAmount(@PathVariable Long orderId, @PathVariable BigDecimal couponAmount);
}
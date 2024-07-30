package com.atguigu.daijia.order.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


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
}
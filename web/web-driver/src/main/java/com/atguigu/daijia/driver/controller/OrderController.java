package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.login.GuiguLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单API接口管理")
@RestController
@RequestMapping("/order")
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderController {

    @Resource
    private OrderService orderService;


    /**
     *查询订单状态
     */
    @Operation(summary = "查询订单状态")
    @GuiguLogin
    @GetMapping("/getOrderStatus/{orderId}")
    public Result<Integer> getOrderStatus(@PathVariable Long orderId) {
        return Result.ok(orderService.getOrderStatus(orderId));
    }

    /**
     * 查询司机新订单数据
     */
    @Operation(summary = "查询司机新订单数据")
    @GuiguLogin
    @GetMapping("/findNewOrderQueueData")
    public Result<List<NewOrderDataVo>> findNewOrderQueueData() {
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(orderService.findNewOrderQueueData(driverId));
    }

    /**
     * 查找司机端当前订单
     */
    @Operation(summary = "司机端查找当前订单")
    @GuiguLogin
    @GetMapping("/searchDriverCurrentOrder")
    public Result<CurrentOrderInfoVo> searchDriverCurrentOrder() {
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(orderService.searchDriverCurrentOrder(driverId));
    }

    /**
     * 司机抢单
     */
    @Operation(summary = "司机抢单")
    @GuiguLogin
    @GetMapping("/robNewOrder/{orderId}")
    public Result<Boolean> robNewOrder(@PathVariable Long orderId) {
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(orderService.robNewOrder(driverId, orderId));
    }

    /**
     * 获取订单账单详细信息
     */
    @Operation(summary = "获取订单账单详细信息")
    @GuiguLogin
    @GetMapping("/getOrderInfo/{orderId}")
    public Result<OrderInfoVo> getOrderInfo(@PathVariable Long orderId) {
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(orderService.getOrderInfo(orderId, driverId));
    }

    /**
     * 计算最佳驾驶线路
     */
    @Operation(summary = "计算最佳驾驶线路")
    @GuiguLogin
    @PostMapping("/calculateDrivingLine")
    public Result<DrivingLineVo> calculateDrivingLine(@RequestBody CalculateDrivingLineForm calculateDrivingLineForm) {
        return Result.ok(orderService.calculateDrivingLine(calculateDrivingLineForm));
    }

    /**
     * 司机到达代驾起始地点
     */
    @Operation(summary = "司机到达代驾起始地点")
    @GuiguLogin
    @GetMapping("/driverArriveStartLocation/{orderId}")
    public Result<Boolean> driverArriveStartLocation(@PathVariable Long orderId) {
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(orderService.driverArriveStartLocation(orderId, driverId));
    }

    /**
     * 更新代驾车辆信息
     */
    @Operation(summary = "更新代驾车辆信息")
    @GuiguLogin
    @PostMapping("/updateOrderCart")
    public Result<Boolean> updateOrderCart(@RequestBody UpdateOrderCartForm updateOrderCartForm) {
        Long driverId = AuthContextHolder.getUserId();
        updateOrderCartForm.setDriverId(driverId);
        return Result.ok(orderService.updateOrderCart(updateOrderCartForm));
    }

    /**
     * 开始代驾服务
     */
    @Operation(summary = "开始代驾服务")
    @GuiguLogin
    @PostMapping("/startDrive")
    public Result<Boolean> startDrive(@RequestBody StartDriveForm startDriveForm) {
        Long driverId = AuthContextHolder.getUserId();
        startDriveForm.setDriverId(driverId);
        return Result.ok(orderService.startDrive(startDriveForm));
    }

    /**
     * 结束代驾服务更新订单账单
     */
    @Operation(summary = "结束代驾服务更新订单账单")
    @GuiguLogin
    @PostMapping("/endDrive")
    public Result<Boolean> endDrive(@RequestBody OrderFeeForm orderFeeForm) {
        Long driverId = AuthContextHolder.getUserId();
        orderFeeForm.setDriverId(driverId);
        return Result.ok(orderService.endDriveByThread(orderFeeForm));
    }

    /**
     * 获取司机订单分页列表
     */
    @Operation(summary = "获取司机订单分页列表")
    @GuiguLogin
    @GetMapping("findDriverOrderPage/{page}/{limit}")
    public Result<PageVo> findDriverOrderPage(
            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,

            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Long driverId = AuthContextHolder.getUserId();
        PageVo pageVo = orderService.findDriverOrderPage(driverId, page, limit);
        return Result.ok(pageVo);
    }

    /**
     * 司机发送账单信息
     */
    @Operation(summary = "司机发送账单信息")
    @GuiguLogin
    @GetMapping("/sendOrderBillInfo/{orderId}")
    public Result<Boolean> sendOrderBillInfo(@PathVariable Long orderId) {
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(orderService.sendOrderBillInfo(orderId, driverId));
    }
}


package com.atguigu.daijia.dispatch.controller;

import com.alibaba.nacos.api.model.v2.Result;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "司机新订单接口管理")
@RestController
@RequestMapping("/dispatch/newOrder")
@SuppressWarnings({"unchecked", "rawtypes"})
public class NewOrderController {

    @Resource
    private NewOrderService newOrderService;

    /**
     * 添加并开始新订单任务调度
     */
    @Operation(summary = "添加并开始新订单任务调度")
    @PostMapping("/addAndStartTask")
    public Result<Long> addAndStartTask(@RequestBody NewOrderTaskVo newOrderTaskVo){
        Long id = newOrderService.addAndStartTask(newOrderTaskVo);
        return Result.success(id);
    }

    /**
     * 查询司机新订单数据
     */
    @Operation(summary = "查询司机新订单数据")
    @GetMapping("/findNewOrderQueueData/{driverId}")
    public Result<List<NewOrderDataVo>> findNewOrderQueueData(@PathVariable Long driverId) {
        return Result.success(newOrderService.findNewOrderQueueData(driverId));
    }

    /**
     * 清空新订单队列数据
     */
    @Operation(summary = "清空新订单队列数据")
    @GetMapping("/clearNewOrderQueueData/{driverId}")
    public Result<Boolean> clearNewOrderQueueData(@PathVariable Long driverId) {
        return Result.success(newOrderService.clearNewOrderQueueData(driverId));
    }



}


package com.atguigu.daijia.order.service;

import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OrderMonitorService extends IService<OrderMonitor> {

    /**
     * 保存订单监控记录数据
     */
    Boolean saveOrderMonitorRecord(OrderMonitorRecord orderMonitorRecord);

    /**
     * 根据订单id获取订单监控信息
     */
    OrderMonitor getOrderMonitor(Long orderId);

    /**
     * 更新订单监控信息
     */
    Boolean updateOrderMonitor(OrderMonitor orderMonitor);
}

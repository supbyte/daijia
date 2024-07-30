package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private OrderInfoMapper orderInfoMapper;
    @Resource
    private OrderStatusLogMapper orderStatusLogMapper;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        // 构建订单对象
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm,orderInfo);
        // 生成订单号
        String orderNo = UUID.randomUUID().toString().replaceAll("-","");
        orderInfo.setOrderNo(orderNo);
        // 设置订单状态
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());
        // 插入订单数据
        orderInfoMapper.insert(orderInfo);
        //向redis添加标识
        //接单标识，标识不存在了说明不在等待接单状态了
        // TODO 此处是否应该加上具体订单标识？
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK+orderInfo.getId(), "0", RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);
        // 记录订单日志
        log(orderInfo.getId(), orderInfo.getStatus());
        // 返回订单ID
        return orderInfo.getId();
    }

    @Override
    public Integer getOrderStatus(Long orderId) {
        // 构建查询条件
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.select(OrderInfo::getStatus);
        // 根据订单id查询订单状态
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        if(null == orderInfo) {
            //返回null，feign解析会抛出异常，给默认值，后续会用
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }

    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }

    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        // 检查订单是否存在，利用Redis缓存减少数据库访问
        String orderKey = RedisConstant.ORDER_ACCEPT_MARK + orderId;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(orderKey))) {
            // 如果订单不存在，抛出抢单失败异常
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        // 创建一个基于订单ID的分布式锁
        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);
        try {
            // 尝试获取锁，如果获取失败直接返回false
            if (!lock.tryLock(RedisConstant.ROB_NEW_ORDER_LOCK_WAIT_TIME,
                    RedisConstant.ROB_NEW_ORDER_LOCK_LEASE_TIME,
                    TimeUnit.SECONDS)) {
                return false;
            }

            // 再次检查订单是否存在，确保数据一致性
            if (Boolean.FALSE.equals(redisTemplate.hasKey(orderKey))) {
                throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
            }

            // 查询订单信息
            OrderInfo orderInfo = orderInfoMapper.selectOne(
                    new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getId, orderId)
            );

            // 更新订单状态为已接单，设置接单司机ID和接单时间
            orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
            orderInfo.setDriverId(driverId);
            orderInfo.setAcceptTime(new Date());

            // 使用乐观锁更新订单状态，只有当订单状态为等待接单时才更新
            if (orderInfoMapper.update(orderInfo,
                    new LambdaQueryWrapper<OrderInfo>()
                            .eq(OrderInfo::getId, orderId)
                            .eq(OrderInfo::getStatus, OrderStatus.WAITING_ACCEPT.getStatus())) < 1) {
                // 如果更新失败，抛出抢单失败异常
                throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
            }

            // 抢单成功后删除抢单标识
            redisTemplate.delete(orderKey);
        } catch (InterruptedException | GuiguException e) {
            // 记录异常日志并抛出抢单失败异常
            log.error("Failed to grab the order: {}", orderId, e);
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        } finally {
            // 释放锁，但只在锁被当前线程持有时释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 返回抢单成功标志
        return true;
    }

    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        // 封装查询条件
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getCustomerId,customerId);
        //各种状态
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus(),
                OrderStatus.UNPAID.getStatus()
        };
        queryWrapper.in(OrderInfo::getStatus, Arrays.asList(statusArray));
        // 获取最新的一条记录
        queryWrapper.orderByDesc(OrderInfo::getId);
        queryWrapper.last("limit 1");
        // 调用方法
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        // 封装到CurrentOrderInfoVo
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if (orderInfo != null){
            // 设置当前订单id
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            // 设置当前订单状态
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            // 设置当前订单是否存在
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        }else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }

        return currentOrderInfoVo;
    }
}

package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.MqConst;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.OrderBill;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderProfitsharing;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.order.mapper.OrderBillMapper;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderProfitsharingMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
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
    private OrderProfitsharingMapper orderProfitsharingMapper;
    @Resource
    private OrderBillMapper orderBillMapper;
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

        //生成订单之后，发送延迟消息
        this.sendDelayMessage(orderInfo.getId());

        //向redis添加标识
        //接单标识，标识不存在了说明不在等待接单状态了
        // TODO 此处是否应该加上具体订单标识？
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK+orderInfo.getId(), "0", RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);
        // 记录订单日志
        log(orderInfo.getId(), orderInfo.getStatus());

        // 返回订单ID
        return orderInfo.getId();
    }

    /**
     * 生成订单之后发送延迟消息
     */
    private void sendDelayMessage(Long orderId) {
        try{
            //1 创建一个阻塞队列
            RBlockingQueue<Object> blockingQueue = redissonClient.getBlockingQueue(MqConst.CANCEL_ORDER_DELAY_QUEUE);

            //2 把创建队列放到延迟队列里面
            RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

            //3 发送消息到延迟队列里面
            //设置过期时间 15分钟
            delayedQueue.offer(orderId.toString(),15,TimeUnit.MINUTES);

        }catch (Exception e) {
            log.error("发送延迟消息失败",e);
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
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

    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        // 封装查询条件
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getDriverId,driverId);
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

    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);

        OrderInfo updateOrderInfo = new OrderInfo();
        // 设置订单状态为：司机已到达（3）
        updateOrderInfo.setStatus(OrderStatus.DRIVER_ARRIVED.getStatus());
        // 设置司机到达时间
        updateOrderInfo.setArriveTime(new Date());
        // 只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if(row == 1) {
            // 记录日志
            this.log(orderId, OrderStatus.DRIVER_ARRIVED.getStatus());
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, updateOrderCartForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId, updateOrderCartForm.getDriverId());

        OrderInfo updateOrderInfo = new OrderInfo();
        BeanUtils.copyProperties(updateOrderCartForm, updateOrderInfo);
        // 设置订单状态为：更新代驾车辆信息（4）
        updateOrderInfo.setStatus(OrderStatus.UPDATE_CART_INFO.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if(row == 1) {
            //记录日志
            this.log(updateOrderCartForm.getOrderId(), OrderStatus.UPDATE_CART_INFO.getStatus());
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    @Override
    public Boolean startDriver(StartDriveForm startDriveForm) {

        // 根据订单id和司机id获取订单
        LambdaQueryWrapper<OrderInfo> queryWrapper =  new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId,startDriveForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId,startDriveForm.getDriverId());
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);

        // 修改订单状态为：开始代驾服务（5）
        orderInfo.setStatus(OrderStatus.START_SERVICE.getStatus());
        // 修改订单开始代驾服务时间
        orderInfo.setStartServiceTime(new Date());
        // 更新数据库
        int rows = orderInfoMapper.update(orderInfo,queryWrapper);
        if (rows < 1){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return true;
    }

    @Override
    public Long getOrderNumByTime(String startTime, String endTime) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper();
        // 服务时间在指定时间内的订单
        queryWrapper.ge(OrderInfo::getStartServiceTime,startTime);
        queryWrapper.le(OrderInfo::getEndServiceTime,endTime);
        return orderInfoMapper.selectCount(queryWrapper);
    }

    @Override
    public Boolean endDrive(UpdateOrderBillForm updateOrderBillForm) {
        // 更新指定订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(OrderInfo::getId, updateOrderBillForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId, updateOrderBillForm.getDriverId());

        // 设置更新数据
        OrderInfo orderInfo = new OrderInfo();
        // 设置订单状态为：结束服务（6）
        orderInfo.setStatus(OrderStatus.END_SERVICE.getStatus());
        // 设置订单实际金额
        orderInfo.setRealAmount(updateOrderBillForm.getTotalAmount());
        // 设置订单实际距离
        orderInfo.setRealDistance(updateOrderBillForm.getRealDistance());
        // 设置代驾服务结束时间
        orderInfo.setEndServiceTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, queryWrapper);
        if(rows == 1) {
            //添加账单数据
            OrderBill orderBill = new OrderBill();
            BeanUtils.copyProperties(updateOrderBillForm,orderBill);
            orderBill.setOrderId(updateOrderBillForm.getOrderId());
            orderBill.setPayAmount(updateOrderBillForm.getTotalAmount());
            orderBillMapper.insert(orderBill);

            //添加分账信息
            OrderProfitsharing orderProfitsharing = new OrderProfitsharing();
            BeanUtils.copyProperties(updateOrderBillForm, orderProfitsharing);
            orderProfitsharing.setOrderId(updateOrderBillForm.getOrderId());
            orderProfitsharing.setRuleId(updateOrderBillForm.getProfitsharingRuleId());
            orderProfitsharing.setStatus(1);
            orderProfitsharingMapper.insert(orderProfitsharing);

        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    @Override
    public PageVo findCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId) {
        IPage<OrderListVo> pageInfo =  orderInfoMapper.selectCustomerOrderPage(pageParam,customerId);

        return new PageVo<>(pageInfo.getRecords(),pageInfo.getPages(),pageInfo.getTotal());
    }

    @Override
    public PageVo findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId) {
        IPage<OrderListVo> pageInfo =  orderInfoMapper.selectDriverOrderPage(pageParam,driverId);

        return new PageVo<>(pageInfo.getRecords(),pageInfo.getPages(),pageInfo.getTotal());
    }

    @Override
    public OrderBillVo getOrderBillInfo(Long orderId) {
        LambdaQueryWrapper<OrderBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderBill::getOrderId,orderId);
        OrderBill orderBill = orderBillMapper.selectOne(wrapper);

        OrderBillVo orderBillVo = new OrderBillVo();
        BeanUtils.copyProperties(orderBill,orderBillVo);
        return orderBillVo;
    }

    @Override
    public OrderProfitsharingVo getOrderProfitsharing(Long orderId) {
        LambdaQueryWrapper<OrderProfitsharing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderProfitsharing::getOrderId,orderId);
        OrderProfitsharing orderProfitsharing = orderProfitsharingMapper.selectOne(wrapper);

        OrderProfitsharingVo orderProfitsharingVo = new OrderProfitsharingVo();
        BeanUtils.copyProperties(orderProfitsharing,orderProfitsharingVo);
        return orderProfitsharingVo;
    }

    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.UNPAID.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if(row == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    @Override
    public OrderPayVo getOrderPayVo(String orderNo, Long customerId) {
        OrderPayVo orderPayVo = orderInfoMapper.selectOrderPayVo(orderNo, customerId);
        if(null != orderPayVo) {
            String content = orderPayVo.getStartLocation() + " 到 " + orderPayVo.getEndLocation();
            orderPayVo.setContent(content);
        }
        return orderPayVo;
    }

    @Override
    public Boolean updateOrderPayStatus(String orderNo) {
        // 1.根据订单编号查询订单信息，检查订单状态
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        if(orderInfo == null || Objects.equals(orderInfo.getStatus(), OrderStatus.PAID.getStatus())) {
            return true;
        }
        // 2.更新订单状态
        orderInfo.setStatus(OrderStatus.PAID.getStatus());
        orderInfo.setPayTime(new Date());
        int rows = orderInfoMapper.update(orderInfo, queryWrapper);
        if(rows == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    @Override
    public OrderRewardVo getOrderRewardFee(String orderNo) {
        //根据订单编号查询订单表
        OrderInfo orderInfo =
                orderInfoMapper.selectOne(
                        new LambdaQueryWrapper<OrderInfo>()
                                .eq(OrderInfo::getOrderNo, orderNo)
                                .select(OrderInfo::getId,OrderInfo::getDriverId));

        //根据订单id查询系统奖励表
        OrderBill orderBill =
                orderBillMapper.selectOne(new LambdaQueryWrapper<OrderBill>()
                        .eq(OrderBill::getOrderId, orderInfo.getId())
                        .select(OrderBill::getRewardFee));

        //封装到vo里面
        OrderRewardVo orderRewardVo = new OrderRewardVo();
        orderRewardVo.setOrderId(orderInfo.getId());
        orderRewardVo.setDriverId(orderInfo.getDriverId());
        orderRewardVo.setRewardFee(orderBill.getRewardFee());
        return orderRewardVo;
    }

    @Override
    public void orderCancel(long orderId) {
        // 根据orderId查询订单状态
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);

        // 异常处理
        if (orderInfo == null){
            log.error("订单状态异常，未能查询到id为{}的订单信息",orderId);
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        // 判断状态是否为等待接单
        if (Objects.equals(orderInfo.getStatus(), OrderStatus.WAITING_ACCEPT.getStatus())){
            // 修改状态为：未接单取消订单
            orderInfo.setStatus(OrderStatus.CANCEL_ORDER.getStatus());
            int rows = orderInfoMapper.updateById(orderInfo);
            if (rows > 0){
                // 订单取消成功后删除redis中的订单接单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK+orderId);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean updateCouponAmount(Long orderId, BigDecimal couponAmount) {
        int row = orderBillMapper.updateCouponAmount(orderId, couponAmount);
        if(row != 1) {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }
}

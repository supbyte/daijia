package com.atguigu.daijia.dispatch.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.dispatch.mapper.OrderJobMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.dispatch.xxl.client.XxlJobClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.dispatch.OrderJob;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class NewOrderServiceImpl implements NewOrderService {

    @Resource
    private OrderJobMapper orderJobMapper;
    @Resource
    private XxlJobClient xxlJobClient;
    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public Long addAndStartTask(NewOrderTaskVo newOrderTaskVo) {
        // 判断当前订单是否启动任务调度
        LambdaQueryWrapper<OrderJob> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderJob::getOrderId,newOrderTaskVo.getOrderId());
        OrderJob orderJob = orderJobMapper.selectOne(queryWrapper);
        // 若没有启动则进行后续操作
        if (orderJob != null){
            return orderJob.getJobId();
        }
        // 创建并启动任务调度
        // String executorHandler 执行任务job方法
        // String param
        // String corn 执行cron表达式   0 0/1 * * * ?   每分钟执行一次
        // String desc 描述信息
        Long jobId = xxlJobClient.addAndStart("newOrderTaskHandler", "", "0 0/1 * * * ?", "新创建订单任务调度：" + newOrderTaskVo.getOrderId());
        // 记录任务调度信息
        orderJob = new OrderJob();
        orderJob.setOrderId(newOrderTaskVo.getOrderId());
        orderJob.setJobId(jobId);
        orderJob.setParameter(JSONObject.toJSONString(newOrderTaskVo));
        orderJobMapper.insert(orderJob);
        return jobId;
    }

    @Override
    public void executeTask(long jobId) {
        // 1.根据jobId查询数据库，判断当前任务是否已经创建
        LambdaQueryWrapper<OrderJob> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderJob::getJobId,jobId);
        OrderJob orderJob = orderJobMapper.selectOne(queryWrapper);
        if (orderJob == null){
            // 如果没有创建，则直接return
            return;
        }
        // 2.查询订单状态，如果当前订单未=为待接单状态则继续执行，否则停止执行任务调度
        // 查询OrderJob里面的参数对象
        String parameter = orderJob.getParameter();
        NewOrderTaskVo newOrderTaskVo = JSONObject.parseObject(parameter, NewOrderTaskVo.class);
        // 获取OrderId
        //Long orderId = orderInfoFeignClient.getOrderStatus(orderJob.getOrderId()).getData();
        Long orderId = newOrderTaskVo.getOrderId();
        Integer status = orderInfoFeignClient.getOrderStatus(orderId).getData();
        if(status.intValue() != OrderStatus.WAITING_ACCEPT.getStatus().intValue()) {
            //停止任务调度
            xxlJobClient.stopJob(jobId);
            return;
        }
        // 3.远程调用搜索附近满足条件的可接单司机
        SearchNearByDriverForm searchNearByDriverForm = new SearchNearByDriverForm();
        searchNearByDriverForm.setLongitude(newOrderTaskVo.getStartPointLongitude());
        searchNearByDriverForm.setLatitude(newOrderTaskVo.getStartPointLatitude());
        searchNearByDriverForm.setMileageDistance(newOrderTaskVo.getExpectDistance());
        // 4.当远程调用之后获取到满足条件的可接单司机列表集合
        List<NearByDriverVo> nearByDriverVoList = locationFeignClient.searchNearByDriver(searchNearByDriverForm).getData();
        // 5.遍历司机集合，封装成一个个司机对象，为每个司机创建临时队列，存储新订单信息
        nearByDriverVoList.forEach(driver -> {
            // 使用redis的set类型
            //根据订单id生成key
            String repeatKey = RedisConstant.DRIVER_ORDER_REPEAT_LIST+newOrderTaskVo.getOrderId();
            // 记录司机id，防止重复推送
            Boolean isMember = redisTemplate.opsForSet().isMember(repeatKey, driver.getDriverId());
            if (Boolean.FALSE.equals(isMember)){
                // 把订单信息推送给满足条件的多个司机
                redisTemplate.opsForSet().add(repeatKey,driver.getDriverId());
                // 过期时间：15分钟，超过15分钟未接单自动取消
                redisTemplate.expire(repeatKey,RedisConstant.DRIVER_ORDER_REPEAT_LIST_EXPIRES_TIME, TimeUnit.MINUTES);

                NewOrderDataVo newOrderDataVo = new NewOrderDataVo();
                BeanUtils.copyProperties(newOrderTaskVo,newOrderDataVo);
                newOrderDataVo.setDistance(driver.getDistance());
                // 将新订单保存到司机的临时队列中，通过redis的list集合实现
                String key = RedisConstant.DRIVER_ORDER_TEMP_LIST+driver.getDriverId();
                redisTemplate.opsForList().leftPush(key,JSONObject.toJSONString(newOrderDataVo));
                //过期时间：1分钟
                redisTemplate.expire(key,RedisConstant.DRIVER_ORDER_TEMP_LIST_EXPIRES_TIME, TimeUnit.MINUTES);
            }

        });
    }

    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        List<NewOrderDataVo> list = new ArrayList<>();
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        Long size = redisTemplate.opsForList().size(key);
        if(size > 0) {
            for (int i = 0; i < size; i++) {
                String content = (String)redisTemplate.opsForList().leftPop(key);
                NewOrderDataVo newOrderDataVo = JSONObject.parseObject(content,NewOrderDataVo.class);
                list.add(newOrderDataVo);
            }
        }
        return list;
    }


    @Override
    public Boolean clearNewOrderQueueData(Long driverId) {
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        redisTemplate.delete(key);
        return true;
    }
}

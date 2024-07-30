package com.atguigu.daijia.map.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {

    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;



    /**
     *  Redis GEO 主要用于存储地理位置信息，并对存储的信息进行相关操作，该功能在 Redis 3.2 版本新增。
     *  后续用在，乘客下单后寻找5公里范围内开启接单服务的司机，通过Redis GEO进行计算
     */
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        // 封装坐标信息
        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue(),updateDriverLocationForm.getLatitude().doubleValue());
        // 将坐标信息添加到redis缓存中
        redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION, point, updateDriverLocationForm.getDriverId().toString());
        return true;
    }

    @Override
    public Boolean removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        return true;
    }

    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {
        // 搜索经纬度位置5公里以内的司机
        //定义经纬度点
        Point point = new Point(searchNearByDriverForm.getLongitude().doubleValue(), searchNearByDriverForm.getLatitude().doubleValue());
        //定义距离：5公里(系统配置)
        Distance distance = new Distance(SystemConstant.NEARBY_DRIVER_RADIUS, RedisGeoCommands.DistanceUnit.KILOMETERS);
        //定义以point点为中心，distance为距离这么一个范围
        Circle circle = new Circle(point, distance);
        //定义GEO参数
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance() //包含距离
                .includeCoordinates() //包含坐标
                .sortAscending(); //排序：升序
        // GEORADIUS获取附近范围内的信息
        GeoResults<RedisGeoCommands.GeoLocation<String>> result = this.redisTemplate.opsForGeo().radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);
        // 1.操作redis里的geo数据
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = Objects.requireNonNull(result).getContent();
        // 2.查询满足条件的NearByDriverVo集合
        return content.stream()
                .filter(item -> {
                    // 获取司机id
                    Long driverId = Long.parseLong(item.getContent().getName());
                    log.info("司机：{}，距离：{}", driverId, item.getDistance().getValue());
                    // 根据司机id远程调用获取个性化设置
                    DriverSet driverSet = driverInfoFeignClient.getDriverSet(driverId).getData();
                    // 判断订单里程和是否满足司机接单要求（若要求为0则代表无限制）
                    BigDecimal orderDistance = driverSet.getOrderDistance();
                    if (orderDistance.doubleValue() != 0 && searchNearByDriverForm.getMileageDistance().doubleValue() > orderDistance.doubleValue()) {
                        // 订单里程大于司机预设里程，不满足条件
                        return false;
                    }

                    // 判断接单里程和是否满足司机接单要求（若要求为0则代表无限制）
                    BigDecimal acceptDistance = driverSet.getAcceptDistance();
                    // 判断接单距离是否大于司机预设距离
                    return orderDistance.doubleValue() == 0 || !(item.getDistance().getValue() > driverSet.getAcceptDistance().doubleValue());
                }).map(item -> {
                    // 获取司机id
                    Long driverId = Long.parseLong(item.getContent().getName());
                    // 获取当前距离
                    BigDecimal currentDistance = BigDecimal.valueOf(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);
                    // 封装返回对象
                    NearByDriverVo nearByDriverVo = new NearByDriverVo();
                    nearByDriverVo.setDriverId(driverId);
                    nearByDriverVo.setDistance(currentDistance);
                    return nearByDriverVo;
                }).toList();
    }

    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        OrderLocationVo orderLocationVo = new OrderLocationVo();
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());
        redisTemplate.opsForValue().set(RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId(), orderLocationVo);
        return true;
    }

    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        return (OrderLocationVo)redisTemplate.opsForValue().get(RedisConstant.UPDATE_ORDER_LOCATION + orderId);
    }
}

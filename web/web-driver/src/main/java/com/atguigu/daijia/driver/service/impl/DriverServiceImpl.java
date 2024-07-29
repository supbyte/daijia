package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverServiceImpl implements DriverService {

    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;
    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private NewOrderFeignClient newOrderFeignClient;
    @Resource
    private RedisTemplate redisTemplate;

    //登录
    @Override
    public String login(String code) {
        //远程调用，得到司机id
        Result<Long> longResult = driverInfoFeignClient.login(code);
        //TODO 判断
        Long driverId = longResult.getData();

        //token字符串
        String token = UUID.randomUUID().toString().replaceAll("-","");
        //放到redis，设置过期时间
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token,
                driverId.toString(),
                RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                TimeUnit.SECONDS);
        return token;
    }

    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        return driverInfoFeignClient.getDriverInfo(driverId).getData();
    }

    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        return driverInfoFeignClient.getDriverAuthInfo(driverId).getData();
    }

    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        Result<Boolean> booleanResult = driverInfoFeignClient.UpdateDriverAuthInfo(updateDriverAuthInfoForm);
        return booleanResult.getData();
    }


    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        Result<Boolean> booleanResult = driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm);
        return booleanResult.getData();
    }

    @Override
    public Boolean isFaceRecognition(Long driverId) {
        return driverInfoFeignClient.isFaceRecognition(driverId).getData();
    }

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        return driverInfoFeignClient.verifyDriverFace(driverFaceModelForm).getData();
    }

    @Override
    public Boolean startService(Long driverId) {
        //判断认证状态
        DriverLoginVo driverLoginVo = driverInfoFeignClient.getDriverInfo(driverId).getData();
        if (driverLoginVo.getAuthStatus() != 2){
            throw new GuiguException(ResultCodeEnum.AUTH_ERROR);
        }
        // 判断司机当日是否完成人脸识别
        Boolean isFaced = driverInfoFeignClient.isFaceRecognition(driverId).getData();
        if (!isFaced){
            throw new GuiguException(ResultCodeEnum.FACE_ERROR);
        }
        // 更新司机接单状态
        driverInfoFeignClient.updateServiceStatus(driverId,1);
        // 删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);
        // 清空司机新订单队列
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }

    @Override
    public Boolean stopService(Long driverId) {
        //更新司机的接单状态 0
        driverInfoFeignClient.updateServiceStatus(driverId,0);

        //删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);

        //清空司机临时队列
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }
}

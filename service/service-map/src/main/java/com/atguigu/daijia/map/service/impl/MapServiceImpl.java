package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.map.service.MapService;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MapServiceImpl implements MapService {

    @Resource
    private RestTemplate restTemplate;

    @Value("${tencent.cloud.map}")
    private String key;


    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        // 请求腾讯提供的接口，按照接口要求传递相关参数，返回所需结果
        // 使用spring封装的restTemplate发送请求
        // 1.定义请求地址
        String url = "https://apis.map.qq.com/ws/direction/v1/driving/?from={from}&to={to}&key={key}";
        // 2.封装传递参数
        Map<String,String> map = new HashMap<>();
        // 起始点
        map.put("from",calculateDrivingLineForm.getStartPointLatitude()+","+calculateDrivingLineForm.getStartPointLongitude());
        // 结束点
        map.put("to",calculateDrivingLineForm.getEndPointLatitude()+","+calculateDrivingLineForm.getEndPointLongitude());
        // key
        map.put("key",key);
        // 3.使用RestTemplate发送请求
        JSONObject result = restTemplate.getForObject(url, JSONObject.class, map);
        //处理返回结果
        //判断调用是否成功(status == 0)
        if (Objects.requireNonNull(result).getInteger("status") != 0){
            throw new GuiguException(ResultCodeEnum.MAP_FAIL);
        }
        // 4.获取返回路线信息
        JSONObject route = result.getJSONObject("result").getJSONArray("routes").getJSONObject(0);

        // 5.封装返回结果
        DrivingLineVo drivingLineVo = new DrivingLineVo();
        // 方案总距离，单位：千米
        drivingLineVo.setDistance(route.getBigDecimal("distance")
                        .multiply(new BigDecimal("0.001"))
                        .setScale(2, RoundingMode.HALF_UP));
        // 方案估算时间（结合路况），单位：分钟
        drivingLineVo.setDuration(route.getBigDecimal("duration"));
        // 方案路线坐标点串
        drivingLineVo.setPolyline(route.getJSONArray("polyline"));

        return drivingLineVo;
    }
}

package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.driver.client.CiFeignClient;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
import com.atguigu.daijia.order.client.OrderMonitorFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MonitorServiceImpl implements MonitorService {

    @Resource
    private FileService fileService;

    @Resource
    private OrderMonitorFeignClient orderMonitorFeignClient;

    @Resource
    private CiFeignClient ciFeignClient;

    @Override
    public Boolean upload(MultipartFile file, OrderMonitorForm orderMonitorForm) {
        // 上传文件获得url
        String url = fileService.upload(file);
        OrderMonitorRecord orderMonitorRecord = new OrderMonitorRecord();
        // 设置订单id
        orderMonitorRecord.setOrderId(orderMonitorForm.getOrderId());
        // 设置监控内容
        orderMonitorRecord.setContent(orderMonitorForm.getContent());
        // 设置录音文件地址
        orderMonitorRecord.setFileUrl(url);

        // 增加文本内容审核
        TextAuditingVo textAuditingVo = ciFeignClient.textAuditing(orderMonitorForm.getContent()).getData();
        // 设置审核结果
        orderMonitorRecord.setResult(textAuditingVo.getResult());
        // 设置违规关键字
        orderMonitorRecord.setKeywords(textAuditingVo.getKeywords());

        // 远程调用存储订单记录到mongodb中
        return orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord).getData();
    }
}

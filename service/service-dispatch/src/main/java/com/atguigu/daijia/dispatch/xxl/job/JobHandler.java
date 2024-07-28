package com.atguigu.daijia.dispatch.xxl.job;

import com.atguigu.daijia.dispatch.mapper.XxlJobLogMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.model.entity.dispatch.XxlJobLog;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobHandler {

    @Resource
    private XxlJobLogMapper xxlJobLogMapper;
    @Resource
    private NewOrderService newOrderService;

    @XxlJob("newOrderTaskHandler")
    public void  newOrderTaskHandler(){
        // 记录任务调度日志
        XxlJobLog xxlJobLog = new XxlJobLog();
        xxlJobLog.setJobId(XxlJobHelper.getJobId());
        long startTime = System.currentTimeMillis();
        try{
            // 执行任务：搜索附近代驾司机
            newOrderService.executeTask(XxlJobHelper.getJobId());
            // 记录成功状态
            xxlJobLog.setStatus(1);
        }catch(Exception e){
            // 记录失败状态
            xxlJobLog.setStatus(0);
            xxlJobLog.setError(e.getMessage());
            log.error("任务执行失败{}",e.getMessage());
        }finally{
            long takeTimes = System.currentTimeMillis()-startTime;
            xxlJobLog.setTimes((int) takeTimes);
            // 存入数据库
            xxlJobLogMapper.insert(xxlJobLog);
        }
    }
}

package com.atguigu.daijia.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.common.constant.MqConst;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.service.RabbitService;
import com.atguigu.daijia.common.util.RequestUtils;
import com.atguigu.daijia.driver.client.DriverAccountFeignClient;
import com.atguigu.daijia.model.entity.payment.PaymentInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.enums.TradeType;
import com.atguigu.daijia.model.form.driver.TransferForm;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.vo.order.OrderRewardVo;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.payment.config.WxPayV3Properties;
import com.atguigu.daijia.payment.mapper.PaymentInfoMapper;
import com.atguigu.daijia.payment.service.WxPayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.PromotionDetail;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.model.TransactionPayer;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

    @Resource
    private PaymentInfoMapper paymentInfoMapper;
    @Resource
    private RSAAutoCertificateConfig rsaAutoCertificateConfig;
    @Resource
    private WxPayV3Properties wxPayV3Properties;
    @Resource
    private RabbitService rabbitService;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private DriverAccountFeignClient driverAccountFeignClient;

    @Override
    public WxPrepayVo createWxPayment(PaymentInfoForm paymentInfoForm) {
        try{
            // 1.添加支付记录到支付表（如果表里已存在该记录则无需添加）
            LambdaQueryWrapper<PaymentInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PaymentInfo::getOrderNo, paymentInfoForm.getOrderNo());
            PaymentInfo paymentInfo = paymentInfoMapper.selectOne(queryWrapper);
            if (paymentInfo == null){
                paymentInfo = new PaymentInfo();
                BeanUtils.copyProperties(paymentInfoForm,paymentInfo);
                // 设置支付状态：未支付（0）
                paymentInfo.setPaymentStatus(0);
                paymentInfoMapper.insert(paymentInfo);
            }

            // 2.创建微信支付api对象
            JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();

            // 3.创建支付request对象，封装所需参数
            PrepayRequest request = new PrepayRequest();
            Amount amount = new Amount();

            //amount.setTotal(paymentInfoForm.getAmount().multiply(new BigDecimal(100)).intValue());
            // 测试使用，支付0.01元
            amount.setTotal(1);
            request.setAmount(amount);
            request.setAppid(wxPayV3Properties.getAppid());
            request.setMchid(wxPayV3Properties.getMerchantId());

            //string[1,127]
            String description = paymentInfo.getContent();
            if(description.length() > 127) {
                description = description.substring(0, 127);
            }
            request.setDescription(paymentInfo.getContent());
            request.setNotifyUrl(wxPayV3Properties.getNotifyUrl());
            request.setOutTradeNo(paymentInfo.getOrderNo());

            //获取用户信息
            Payer payer = new Payer();
            payer.setOpenid(paymentInfoForm.getCustomerOpenId());
            request.setPayer(payer);

            //是否指定分账，不指定不能分账
            SettleInfo settleInfo = new SettleInfo();
            settleInfo.setProfitSharing(true);
            request.setSettleInfo(settleInfo);

            // 4.调用微信支付使用对象方法发起微信支付调用
            //PrepayWithRequestPaymentResponse response = service.prepayWithRequestPayment(request);
            //log.info("微信支付下单返回参数：{}", JSON.toJSONString(response));

            // 5.根据返回结果封装到VO对象中
            WxPrepayVo wxPrepayVo = new WxPrepayVo();
            //BeanUtils.copyProperties(response, wxPrepayVo);
            //wxPrepayVo.setTimeStamp(response.getTimeStamp());
            // TODO 个人开发者只能模拟调用
            wxPrepayVo.setAppId(wxPayV3Properties.getAppid());
            wxPrepayVo.setTimeStamp(String.valueOf(System.currentTimeMillis()/1000));
            wxPrepayVo.setNonceStr("nOnc3Str1ng");
            wxPrepayVo.setPackageVal("prepay_id="+paymentInfoForm.getCustomerOpenId());
            wxPrepayVo.setSignType("MD5");
            wxPrepayVo.setPaySign(wxPayV3Properties.getApiV3key());
            return wxPrepayVo;
        }catch(Exception e){
            log.error("微信支付下单失败：{}", e.getMessage());
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    @Override
    public Boolean queryPayStatus(String orderNo) {
        //1 创建微信操作对象
        JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();

        //2 封装查询支付状态需要参数
        QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
        queryRequest.setMchid(wxPayV3Properties.getMerchantId());
        queryRequest.setOutTradeNo(orderNo);

        //3 调用微信操作对象里面方法实现查询操作
        //Transaction transaction = service.queryOrderByOutTradeNo(queryRequest);

        //4 查询返回结果，根据结果判断
//        if(transaction != null && transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
//            //5 如果支付成功，调用其他方法实现支付后处理逻辑
//            this.handlePayment(transaction);
//
//            return true;
//        }
//        return false;

        // TODO 直接模拟调用成功，执行后续逻辑
        Transaction transaction = new Transaction();
        // 订单编号
        transaction.setOutTradeNo(orderNo);
        transaction.setAppid(wxPayV3Properties.getAppid());
        transaction.setTransactionId(String.valueOf(System.currentTimeMillis()));
        transaction.setTransactionId(String.valueOf(System.currentTimeMillis()));
        transaction.setAmount(new TransactionAmount());
        transaction.setAttach("This is a virtual transaction.");
        transaction.setBankType("CMB");
        transaction.setMchid(wxPayV3Properties.merchantId);
        transaction.setPromotionDetail(new ArrayList<>());
        transaction.setSuccessTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        transaction.setTradeState(Transaction.TradeStateEnum.SUCCESS);
        transaction.setTradeStateDesc("支付成功");
        transaction.setTradeType(Transaction.TradeTypeEnum.NATIVE);
        this.handlePayment(transaction);
        return true;
    }


    @Override
    public void wxnotify(HttpServletRequest request) {
        //1.回调通知的验签与解密
        //从request头信息获取参数
        //HTTP 头 Wechatpay-Signature
        // HTTP 头 Wechatpay-Nonce
        //HTTP 头 Wechatpay-Timestamp
        //HTTP 头 Wechatpay-Serial
        //HTTP 头 Wechatpay-Signature-Type
        //HTTP 请求体 body。切记使用原始报文，不要用 JSON 对象序列化后的字符串，避免验签的 body 和原文不一致。
        String wechatPaySerial = request.getHeader("Wechatpay-Serial");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String signature = request.getHeader("Wechatpay-Signature");
        String requestBody = RequestUtils.readData(request);

        //2.构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(wechatPaySerial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(requestBody)
                .build();

        //3.初始化 NotificationParser
        NotificationParser parser = new NotificationParser(rsaAutoCertificateConfig);
        //4.以支付通知回调为例，验签、解密并转换成 Transaction
        Transaction transaction = parser.parse(requestParam, Transaction.class);

        if(null != transaction && transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
            //5.处理支付业务
            this.handlePayment(transaction);
        }
    }

    /**
     * 支付成功回调方法
     */
    private void handlePayment(Transaction transaction) {
        // 1.更新支付记录，修改状态为：已支付（8）
        String orderNo = transaction.getOutTradeNo();
        // 查询支付记录
        LambdaQueryWrapper<PaymentInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentInfo::getOrderNo, orderNo);
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(queryWrapper);
        // 如果已经支付过则直接结束，不需要更新数据库
        if (paymentInfo.getPaymentStatus() == 1){
            return;
        }
        // 手动模拟数据
        TransactionPayer payer = new TransactionPayer();
        payer.setOpenid(paymentInfo.getCustomerOpenId());
        transaction.setPayer(payer);

        paymentInfo.setPaymentStatus(1);
        paymentInfo.setOrderNo(transaction.getOutTradeNo());
        paymentInfo.setTransactionId(transaction.getTransactionId());
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setCallbackContent(JSON.toJSONString(transaction));
        paymentInfoMapper.updateById(paymentInfo);
        // 2.发送端：发送mq消息，传递订单编号
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER, MqConst.ROUTING_PAY_SUCCESS, orderNo);
        //   接收端（支付模块）：获取订单编号，完成后续处理
    }

    @GlobalTransactional
    @Override
    public void handleOrder(String orderNo) {
        //1 远程调用：更新订单状态：已经支付
        orderInfoFeignClient.updateOrderPayStatus(orderNo);

        //2 远程调用：获取系统奖励，打入到司机账户
        OrderRewardVo orderRewardVo = orderInfoFeignClient.getOrderRewardFee(orderNo).getData();
        if (orderRewardVo != null && orderRewardVo.getRewardFee().doubleValue() > 0){
            TransferForm transferForm = new TransferForm();
            transferForm.setTradeNo(orderNo);
            transferForm.setTradeType(TradeType.REWARD.getType());
            transferForm.setContent(TradeType.REWARD.getContent());
            transferForm.setAmount(orderRewardVo.getRewardFee());
            transferForm.setDriverId(orderRewardVo.getDriverId());
            driverAccountFeignClient.transfer(transferForm);
        }
        //3 TODO 其他
    }
}

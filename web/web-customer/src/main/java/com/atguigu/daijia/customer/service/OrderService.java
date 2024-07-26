package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;

public interface OrderService {

    /**
     *预估订单数据
     */
    ExpectOrderVo expectOrder(ExpectOrderForm expectOrderForm);
}

package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;

public interface CouponService  {


    /**
     * 查询未领取优惠券分页列表
     */
    PageVo<NoReceiveCouponVo> findNoReceivePage(Long customerId, Long page, Long limit);

    /**
     * 查询未使用优惠券分页列表
     */
    PageVo<NoUseCouponVo> findNoUsePage(Long customerId, Long page, Long limit);

    /**
     * 查询已使用优惠券分页列表
     */
    PageVo<UsedCouponVo> findUsedPage(Long customerId, Long page, Long limit);

    /**
     * 领取优惠券
     */
    Boolean receive(Long customerId, Long couponId);
}

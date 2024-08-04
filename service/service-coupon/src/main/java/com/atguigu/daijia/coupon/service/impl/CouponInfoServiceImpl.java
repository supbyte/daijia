package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Resource
    private CouponInfoMapper couponInfoMapper;
    @Resource
    private CustomerCouponMapper customerCouponMapper;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoReceiveCouponVo> pageInfo = couponInfoMapper.findNoReceivePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoUseCouponVo> pageInfo = couponInfoMapper.findNoUsePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<UsedCouponVo> pageInfo = couponInfoMapper.findUsedPage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 用户领取优惠券
     *
     * @param customerId  用户ID
     * @param couponId    优惠券ID
     * @return            领取结果，成功返回true，失败返回false
     */
    @Override
    public Boolean receive(Long customerId, Long couponId) {
        // 1. 根据couponId查询优惠券是否存在
        CouponInfo couponInfo = couponInfoMapper.selectById(couponId);
        if (couponInfo == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        // 2. 判断优惠券是否过期
        if (couponInfo.getExpireTime().before(new Date())) {
            throw new GuiguException(ResultCodeEnum.COUPON_EXPIRE);
        }

        // 3. 检查优惠券发行数量是否大于领取数量
        if (couponInfo.getPublishCount() != 0 && couponInfo.getPublishCount() <= couponInfo.getReceiveCount()) {
            throw new GuiguException(ResultCodeEnum.COUPON_LESS);
        }

        // 4. 检查用户领取数量是否达到限制
        if (couponInfo.getPerLimit() > 0) {
            // 统计当前用户已经领取的优惠券数量
            Long receivedCount = getReceivedCount(customerId, couponId);
            if (receivedCount >= couponInfo.getPerLimit()) {
                throw new GuiguException(ResultCodeEnum.COUPON_USER_LIMIT);
            }
        }

        // 乐观锁 + 分布式锁（悲观锁）
        RLock lock = null;
        try {
            lock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean flag = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME, RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!flag) {
                // 尝试重试获取锁
                flag = retryLock(lock);
            }
            if (flag) {
                // 5. 领取优惠券，更新优惠券领取数量
                int row = couponInfoMapper.updateReceiveCount(couponId);
                // 6. 保存领取记录
                if (row == 1) {
                    return saveCustomerCoupon(customerId, couponId, couponInfo.getExpireTime());
                }
                throw new GuiguException(ResultCodeEnum.COUPON_LESS);
            }
        } catch (Exception e) {
            log.error("优惠券领取失败", e);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
        throw new GuiguException(ResultCodeEnum.COUPON_LESS);
    }

    /**
     * 获取当前用户已经领取的优惠券数量
     *
     * @param customerId 用户ID
     * @param couponId   优惠券ID
     * @return 已领取的数量
     */
    private Long getReceivedCount(Long customerId, Long couponId) {
        LambdaQueryWrapper<CustomerCoupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CustomerCoupon::getCouponId, couponId);
        queryWrapper.eq(CustomerCoupon::getCustomerId, customerId);
        return customerCouponMapper.selectCount(queryWrapper);
    }

    /**
     * 重试获取锁
     *
     * @param lock 分布式锁对象
     * @return 是否成功获取锁
     */
    private boolean retryLock(RLock lock) {
        for (int i = 0; i < RedisConstant.COUPON_LOCK_MAX_RETRY_TIMES; i++) {
            try {
                boolean flag = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME, RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
                if (flag) {
                    return true;
                }
                Thread.sleep(100); // 等待一段时间后再次尝试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重试获取锁时发生中断", e);
            }
        }
        return false;
    }


    private Boolean saveCustomerCoupon(Long customerId, Long couponId, Date expireTime) {
        CustomerCoupon customerCoupon = new CustomerCoupon();
        customerCoupon.setCustomerId(customerId);
        customerCoupon.setCouponId(couponId);
        customerCoupon.setStatus(1);
        customerCoupon.setReceiveTime(new Date());
        customerCoupon.setExpireTime(expireTime);
        return customerCouponMapper.insert(customerCoupon) > 0;
    }
}

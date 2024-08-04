package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
        // 获取乘客未使用的优惠券列表
        List<NoUseCouponVo> noUseCoupons = couponInfoMapper.findNoUseList(customerId);

        return noUseCoupons.stream()
                // 过滤并转换成AvailableCouponVo列表
                .filter(noUseCouponVo -> isCouponApplicable(noUseCouponVo, orderAmount))
                .map(noUseCouponVo -> buildAvailableCouponVo(noUseCouponVo, orderAmount))
                // 对AvailableCouponVo列表按减免金额排序
                .sorted(Comparator.comparing(AvailableCouponVo::getReduceAmount))
                .collect(Collectors.toList());
    }

    private boolean isCouponApplicable(NoUseCouponVo noUseCouponVo, BigDecimal orderAmount) {
        BigDecimal conditionAmount = noUseCouponVo.getConditionAmount();
        BigDecimal amount = noUseCouponVo.getAmount();
        BigDecimal discount = noUseCouponVo.getDiscount();

        if (noUseCouponVo.getCouponType() == 1) { // 现金券
            return (conditionAmount.doubleValue() == 0 && orderAmount.subtract(amount).doubleValue() > 0)
                    || (conditionAmount.doubleValue() > 0 && orderAmount.subtract(conditionAmount).doubleValue() > 0);
        } else if (noUseCouponVo.getCouponType() == 2) { // 折扣券
            BigDecimal discountAmount = orderAmount.multiply(discount).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
            BigDecimal reduceAmount = orderAmount.subtract(discountAmount);

            return (conditionAmount.doubleValue() == 0)
                    || (conditionAmount.doubleValue() > 0 && discountAmount.subtract(conditionAmount).doubleValue() > 0);
        }

        return false;
    }

    private AvailableCouponVo buildAvailableCouponVo(NoUseCouponVo noUseCouponVo, BigDecimal orderAmount) {
        AvailableCouponVo availableCouponVo = new AvailableCouponVo();
        try {
            BeanUtils.copyProperties(availableCouponVo, noUseCouponVo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BigDecimal reduceAmount;
        if (noUseCouponVo.getCouponType() == 1) { // 现金券
            reduceAmount = noUseCouponVo.getAmount();
        } else { // 折扣券
            BigDecimal discountAmount = orderAmount.multiply(noUseCouponVo.getDiscount()).divide(BigDecimal.TEN,2, RoundingMode.HALF_UP);
            reduceAmount = orderAmount.subtract(discountAmount);
        }

        availableCouponVo.setReduceAmount(reduceAmount);
        availableCouponVo.setCouponId(noUseCouponVo.getId());
        return availableCouponVo;
    }

    @Transactional(noRollbackFor = Exception.class)
    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
        // 获取乘客优惠券
        CustomerCoupon customerCoupon = customerCouponMapper.selectById(useCouponForm.getCustomerCouponId());
        if (customerCoupon == null) {
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }

        // 获取优惠券信息
        CouponInfo couponInfo = couponInfoMapper.selectById(customerCoupon.getCouponId());
        if (couponInfo == null) {
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }

        // 判断该优惠券是否为乘客所有
        if (customerCoupon.getCustomerId().longValue() != useCouponForm.getCustomerId().longValue()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        // 获取优惠券减免金额
        BigDecimal reduceAmount = BigDecimal.ZERO; // 初始化为0，如果没有满足条件则返回0

        if (couponInfo.getCouponType() == 1) { // 现金券
            // 使用门槛判断
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                // 没门槛，订单金额必须大于优惠券减免金额
                if (useCouponForm.getOrderAmount().subtract(couponInfo.getAmount()).doubleValue() > 0) {
                    reduceAmount = couponInfo.getAmount();
                }
            } else {
                // 有门槛，订单金额大于优惠券门槛金额
                if (useCouponForm.getOrderAmount().subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                    reduceAmount = couponInfo.getAmount();
                }
            }
        } else { // 折扣券
            // 订单折扣后金额
            BigDecimal discountOrderAmount = useCouponForm.getOrderAmount().multiply(couponInfo.getDiscount()).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
            // 订单优惠金额
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                // 没门槛
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            } else {
                // 有门槛，订单折扣后金额大于优惠券门槛金额
                if (discountOrderAmount.subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                    reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
                }
            }
        }

        if (reduceAmount.doubleValue() > 0) {
            int row = couponInfoMapper.updateUseCount(couponInfo.getId());
            if (row == 1) {
                CustomerCoupon updateCustomerCoupon = new CustomerCoupon();
                updateCustomerCoupon.setId(customerCoupon.getId());
                updateCustomerCoupon.setUsedTime(new Date());
                updateCustomerCoupon.setOrderId(useCouponForm.getOrderId());
                customerCouponMapper.updateById(updateCustomerCoupon);
                return reduceAmount;
            }
        }

        throw new GuiguException(ResultCodeEnum.DATA_ERROR);
    }
}

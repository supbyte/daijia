package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import org.springframework.web.multipart.MultipartFile;

public interface CosService {


    /**
     *文件上传
     */
    CosUploadVo upload(MultipartFile file, String path);

    /**
     * 获取图片回显url
     */
    String getImageUrl(String path);
}

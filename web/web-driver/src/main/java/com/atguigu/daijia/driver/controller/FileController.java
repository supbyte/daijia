package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.login.GuiguLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "上传管理接口")
@RestController
@RequestMapping("file")
public class FileController {

    @Resource
    private CosService cosService;
    @Resource
    private FileService fileService;

//    /**
//     * 腾讯云文件上传接口
//     */
//    @Operation(summary = "腾讯云上传")
//    @GuiguLogin
//    @PostMapping("/upload")
//    public Result<CosUploadVo> upload(@RequestPart("file") MultipartFile file,
//                                      @RequestParam(name = "path",defaultValue = "auth") String path) {
//        CosUploadVo cosUploadVo = cosService.uploadFile(file,path);
//        return Result.ok(cosUploadVo);
//    }


    /**
     * Minio文件上传
     */
    @Operation(summary = "Minio文件上传")
    @PostMapping("upload")
    public Result<String> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(fileService.upload(file));
    }


}

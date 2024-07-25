package com.atguigu.daijia.model.entity.aliyun;

import lombok.Data;

import java.util.Date;

@Data
public class DriverLicenseFaceData {
    private String licenseNumber;
    private String name;
    private String sex;
    private String nationality;
    private String address;
    private String birthDate;
    private String initialIssueDate;
    private String approvedType;
    private String issueAuthority;
    private String validFromDate;
    private String validPeriod;
}

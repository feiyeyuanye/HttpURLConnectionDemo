package com.example.httpdemoapplication;

import java.util.Map;

/**
 * 网络传输加密
 */
public interface IEncrypt {
    public String encrypt(String urlPath, Map<String, Object> params);
    public String dencrypt();
}

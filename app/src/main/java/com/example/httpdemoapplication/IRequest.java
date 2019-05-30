package com.example.httpdemoapplication;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义网络接口
 */
public interface IRequest {
    public String getBaseUrl();
    public String getMethod();
    public IEncrypt getEncrypt();
    public HashMap<String, Object> getParam();
    public Map<String, FilePair> getFilePair();
    public Map<String, String> getHeaders();
}

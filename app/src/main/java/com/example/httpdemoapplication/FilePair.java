package com.example.httpdemoapplication;

/**
 * 上传文件
 */
public  class FilePair{
        String mFileName;
        byte[] mBinaryData;
        public FilePair(String fileName, byte[] data) {
            this.mFileName = fileName;
            this.mBinaryData = data;
        }
}


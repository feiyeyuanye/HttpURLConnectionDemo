package com.example.httpdemoapplication;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HttpsURLConnection;

/**
 * 网络封装类
 */
public class NetUtil {

    public enum Method {
        GET,
        POST
    }

    private static final String END = "\r\n";
    public static int connectionTimeOut = 30000;
    public static int readSocketTimeOut = 30000;

    /**
     * GET 请求方式
     */
    public static String get(IRequest request) {
        InputStream inputStream = null;
        HttpURLConnection httpURLConnection = null;
        try {
            // 1. 创建一个URL对象
            // buildGetUrl ，根据参数拼接 url 。
            URL url = new URL(buildGetUrl(request.getBaseUrl(), request.getParam(), request.getEncrypt()));
            // 打开链接
            //  2. 调用URL对象的openConnection()来获取HttpURLConnection对象实例
            openUrlConnection(url,httpURLConnection);
            // 通用配置
            // 3. 设置Http请求使用的方法：GET/POST/其他
            // 4. 设置连接超时，读取超时的毫秒数，以及服务器希望得到的一些消息头。
            normalSetting(httpURLConnection, Method.GET, request.getHeaders());
            if (httpURLConnection == null) {
                return null;
            }
            // 响应码
            int responseCode = httpURLConnection.getResponseCode();
            // 为200时是标志请求成功了
            // 如果返回301，或者是302，是由于链接重定向的问题造成的，
            // 可通过String location =httpURLConnection.getHeaderField("Location");获取重定向的网址进行重新请求。
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 5. 调用getInputStream()方法获得服务器返回的输入流，然后读取输入流。
                inputStream = httpURLConnection.getInputStream();
                String contentEncoding = httpURLConnection.getContentEncoding();
                InputStream stream = null;
                try {
                    stream = wrapStream(contentEncoding, inputStream);
                    String data = convertStreamToString(stream);
                    return data;
                } catch (IOException e) {
                    return "";
                } finally {
                    closeQuietly(stream);
                }

            }
            return null;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * POST 请求方式
     */
    public static String post(IRequest request) {
        String boundary = UUID.randomUUID().toString();
        HttpURLConnection httpURLConnection = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        URL url = null;
        try {
            url = new URL(request.getBaseUrl());
            openUrlConnection(url,httpURLConnection);
            normalSetting(httpURLConnection, Method.POST,request.getHeaders());
//            if (req.mMimeType != null) {// stats cache log request
//                String data = (String) bodyPair.get("data");
//                httpURLConnection.setRequestProperty("Content-Type", req.mMimeType.toString());
//                outputStream = httpURLConnection.getOutputStream();
//                outputStream.write(data.getBytes());
//            } else
            if (request.getParam() != null && request.getParam().size() > 0) {
//                multipart/form-data，使用表单上传文件时，必须让 form 的 enctyped 等于这个值
                httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                outputStream = httpURLConnection.getOutputStream();
                addBodyParams(request.getParam(),request.getFilePair(), outputStream, boundary);
            } else {
////              Content-Type 被指定为 application/x-www-form-urlencoded
//                httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
//                Uri.Builder builder = new Uri.Builder();
////              提交的数据按照 key1=val1&key2=val2 的方式进行编码，并且key 和 val 都进行了 URL 转码。
//                builder.appendQueryParameter("content", message);
//                String query = builder.build().getEncodedQuery();
//                outputStream = new DataOutputStream(httpURLConnection.getOutputStream());
//                outputStream.write(query.getBytes());
            }
            outputStream.flush();
            int responseCode = httpURLConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = httpURLConnection.getInputStream();
                String contentEncoding = httpURLConnection.getContentEncoding();
                InputStream stream = wrapStream(contentEncoding, inputStream);
                String data = convertStreamToString(stream);
                return data;

            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static void openUrlConnection(URL url, HttpURLConnection httpURLConnection) throws IOException {
        String scheme = url.getProtocol();
        boolean isHttpsRequest = false;
        if ("https".equals(scheme)) {
            isHttpsRequest = true;
        }
        if (isHttpsRequest) {
            httpURLConnection = (HttpsURLConnection) (url).openConnection();
            // TODO 处理https证书 1,需要测试https请求;2如需设置证书，需验证是否会对其它https请求有影响
            //trustHosts((HttpsURLConnection) urlConnection);
        } else {
            httpURLConnection = (HttpURLConnection) (url).openConnection();
        }

    }

    /**
     *  通用配置设置
     */
    private static void normalSetting(HttpURLConnection urlConnection, Method method, Map<String, String> mHeaders) throws ProtocolException {
        // 设置连接主机超时（单位：毫秒）
        urlConnection.setConnectTimeout(connectionTimeOut);
        // 设置从主机读取数据超时（单位：毫秒）
        urlConnection.setReadTimeout(readSocketTimeOut);
        urlConnection.setRequestMethod(method.toString());
        if (method == Method.GET) {
            // Accept-Encoding是浏览器发给服务器,声明浏览器支持的编码类型
            urlConnection.setRequestProperty("Accept-Encoding", "gzip");
            if (mHeaders != null && mHeaders.size() > 0) {
                Set<String> stringKeys = mHeaders.keySet();
                for (String key : stringKeys) {
                    urlConnection.setRequestProperty(key, mHeaders.get(key));
                }
            }
        } else if (method == Method.POST) {
            // URL 连接可用于输入和/或输出。
            // 如果打算使用 URL 连接进行输出，则将 DoOutput 标志设置为 true；
            // 如果不打算使用，则设置为 false。默认值为 false。
            // setDoOutput(true);以后就可以使用 httpURLConnection.getOutputStream().write()
            urlConnection.setDoOutput(true);
            // URL 连接可用于输入和/或输出。
            // 如果打算使用 URL 连接进行输入，则将 DoInput 标志设置为 true；
            // 如果不打算使用，则设置为 false。默认值为 true。
            // setDoInput(true);以后就可以使用 httpURLConnection.getInputStream().read();
            urlConnection.setDoInput(true);
        }
    }

    /**
     * 写入数据
     */
    private static void addBodyParams(HashMap<String, Object> map, Map<String, FilePair> filePair, OutputStream outputStream, String boundary) throws IOException {
        boolean didWriteData = false;
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, Object> bodyPair =map;
        Set<String> keys = bodyPair.keySet();
        for (String key : keys) {
            if (bodyPair.get(key) != null) {
                addFormField(stringBuilder, key, bodyPair.get(key).toString(), boundary);
            }
        }

        if (stringBuilder.length() > 0) {
            didWriteData = true;
            outputStream = new DataOutputStream(outputStream);
            outputStream.write(stringBuilder.toString().getBytes());
        }

        // upload files like POST files to server
        if (filePair != null && filePair.size() > 0) {
            Set<String> fileKeys = filePair.keySet();
            for (String key : fileKeys) {
                FilePair pair = filePair.get(key);
                byte[] data = pair.mBinaryData;
                if (data == null || data.length < 1) {
                    continue;
                } else {
                    didWriteData = true;
                    addFilePart(pair.mFileName, data, boundary, outputStream);
                }
            }
        }

        if (didWriteData) {
            finishWrite(outputStream, boundary);
        }
    }

    private static void addFormField(StringBuilder writer, final String name, final String value, String boundary) {
        writer.append("--").append(boundary).append(END)
                .append("Content-Disposition: form-data; name=\"").append(name)
                .append("\"").append(END)
                .append("Content-Type: text/plain; charset=").append("UTF-8")
                .append(END).append(END).append(value).append(END);
    }

    private static void addFilePart(final String fieldName, byte[] data, String boundary, OutputStream outputStream)
            throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("--").append(boundary).append(END)
                .append("Content-Disposition: form-data; name=\"")
                .append("pic").append("\"; filename=\"").append(fieldName)
                .append("\"").append(END).append("Content-Type: ")
                .append("application/octet-stream").append(END)
                .append("Content-Transfer-Encoding: binary").append(END)
                .append(END);
        outputStream.write(stringBuilder.toString().getBytes());
        outputStream.write(data);
        outputStream.write(END.getBytes());
    }

    private static void finishWrite(OutputStream outputStream, String boundary) throws IOException {
        outputStream.write(END.getBytes());
        outputStream.write(("--" + boundary + "--").getBytes());
        outputStream.write(END.getBytes());
        outputStream.flush();
        outputStream.close();
    }

    private static InputStream wrapStream(String contentEncoding, InputStream inputStream)
            throws IOException {
        if (contentEncoding == null || "identity".equalsIgnoreCase(contentEncoding)) {
            return inputStream;
        }
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            return new GZIPInputStream(inputStream);
        }
        if ("deflate".equalsIgnoreCase(contentEncoding)) {
            return new InflaterInputStream(inputStream, new Inflater(false), 512);
        }
        throw new RuntimeException("unsupported content-encoding: " + contentEncoding);
    }

    /**
     * 根据参数拼接URL
     */
    private static String buildGetUrl(String urlPath, Map<String, Object> params, IEncrypt encrypt) {
        if (TextUtils.isEmpty(urlPath) || params == null || params.size() == 0) {
            return urlPath;
        }

        if (!urlPath.endsWith("?")) {
            urlPath += "?";
        }

        String paramsStr = buildGetParams(params);
        if (encrypt != null) {
            // 加密
            // 如果不需要加密，可将这个参数设置为空，或者直接实现，返回原字符串即可。
            paramsStr = encrypt.encrypt(urlPath, params);

        }

        StringBuilder sbUrl = new StringBuilder(urlPath);
        sbUrl.append(paramsStr);
        return sbUrl.toString();
    }

    private static String buildGetParams(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        Set<String> keys = params.keySet();
        for (String key : keys) {
            if (params.get(key) == null) {
                continue;
            }
            sb = sb.append(key + "=" + URLEncoder.encode(params.get(key).toString()) + "&");
        }

        String paramsStr = sb.substring(0, sb.length() - 1).toString();
        return paramsStr;
    }

    private static String convertStreamToString(InputStream is) {
        InputStreamReader inputStreamReader = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(inputStreamReader, 512);
        StringBuilder stringBuilder = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line + "\n");
            }
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(inputStreamReader);
            closeQuietly(reader);
        }
        return stringBuilder.toString();
    }

    private static void closeQuietly(Closeable io) {
        try {
            if (io != null) {
                io.close();
            }
        } catch (IOException e) {
        }
    }

}
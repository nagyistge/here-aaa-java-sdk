/*
 * Copyright (c) 2016 HERE Europe B.V.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.here.account.http.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.here.account.http.HttpConstants;
import com.here.account.http.HttpException;
import com.here.account.http.HttpProvider;

/**
 * A pure-Java implementation of the HttpProvider interface using 
 * {@link HttpURLConnection}.
 * 
 * @author kmccrack
 *
 */
public class JavaHttpProvider implements HttpProvider {
    
    public static class Builder {
    
        private Builder() {
        }
    
        /**
         * Build using builders, builders, and more builders.
         * 
         * @return the built HttpProvider implementation based on Java 
         * {@link HttpURLConnection}.
         */
        public HttpProvider build() {
            // uses Java's default connection pooling by default
            return new JavaHttpProvider();
    
        }
    }

    
    static class JavaHttpResponse implements HttpResponse {
        
        private final int statusCode;
        private final long contentLength;
        private final InputStream responseBody;
        
        public JavaHttpResponse(int statusCode, 
                long contentLength, 
                InputStream responseBody) {
            this.statusCode = statusCode;
            this.contentLength = contentLength;
            this.responseBody = responseBody;
        }
            
        /**
         * {@inheritDoc}
         */
        @Override
        public int getStatusCode() {
            return statusCode;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getContentLength() {
            return contentLength;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream getResponseBody() throws IOException {
            return responseBody;
        }
    }
    
    static class JavaHttpRequest implements HttpRequest {
        
        private final String method;
        private final String url;
        private String authorizationHeader;
        
        private byte[] body;
        private final String contentType;
        private final String contentLength;
        
        public JavaHttpRequest(String method, String url, 
                String requestBodyJson, Map<String, List<String>> formParams) {
            this.method = method;
            this.url = url;
            
            try {
                if (null != formParams) {
                    contentType = HttpConstants.CONTENT_TYPE_FORM_URLENCODED;
                    body = getBody2(formParams);
                    contentLength = String.valueOf(body.length);
                } else if (null != requestBodyJson) {
                    contentType = HttpConstants.CONTENT_TYPE_JSON;
                    body = requestBodyJson.getBytes(HttpConstants.ENCODING_CHARSET);
                    contentLength = String.valueOf(body.length);
                } else {
                    contentType = null;
                    body = null;
                    contentLength = null;
                }
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addAuthorizationHeader(String value) {
            this.authorizationHeader = value;
        }
        
        public String getMethod() {
            return method;
        }

        public String getUrl() {
            return url;
        }

        public String getAuthorizationHeader() {
            return authorizationHeader;
        }
        
        public byte[] getBody() {
            return body;
        }

        public String getContentType() {
            return contentType;
        }

        public String getContentLength() {
            return contentLength;
        }



    }
    
    /**
     * Only the Builder can construct a JavaHttpProvider.
     */
    private JavaHttpProvider() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        // nothing to do
    }

    @Override
    public HttpRequest getRequest(HttpRequestAuthorizer httpRequestAuthorizer, String method, String url,
            String requestBodyJson) {
        Map<String, List<String>> formParams = null;
        HttpRequest httpRequest = new JavaHttpRequest( method,  url, 
                 requestBodyJson,  formParams);
        httpRequestAuthorizer.authorize(httpRequest, method, url, formParams);
        return httpRequest;
    }

    @Override
    public HttpRequest getRequest(HttpRequestAuthorizer httpRequestAuthorizer, String method, String url,
            Map<String, List<String>> formParams) {
        String requestBodyJson = null;
        HttpRequest httpRequest = new JavaHttpRequest( method,  url, 
                 requestBodyJson,  formParams);
        httpRequestAuthorizer.authorize(httpRequest, method, url, formParams);
        return httpRequest;
    }
    
    protected long getContentLength(HttpURLConnection connection) {
        String contentLengthString = connection.getHeaderField(HttpConstants.CONTENT_LENGTH_HEADER);
        return Long.parseLong(contentLengthString);
    }

    @Override
    public HttpResponse execute(HttpRequest httpRequest) throws HttpException, IOException {
        JavaHttpRequest javaHttpRequest = (JavaHttpRequest) httpRequest;
                 
        URL url = new URL(javaHttpRequest.getUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod(javaHttpRequest.getMethod());

        byte[] body = javaHttpRequest.getBody();
        if (null != body) {
            connection.setRequestProperty(HttpConstants.CONTENT_TYPE_HEADER, 
                    javaHttpRequest.getContentType());
            connection.setRequestProperty(HttpConstants.CONTENT_LENGTH_HEADER, 
                    javaHttpRequest.getContentLength());
        }
        
        String authorizationHeader = javaHttpRequest.getAuthorizationHeader();
        if (null != authorizationHeader) {
            connection.setRequestProperty(HttpConstants.AUTHORIZATION_HEADER, authorizationHeader);
        }
                 
        // Write data
        try (
            OutputStream outputStream = connection.getOutputStream()
        ) {
            if (null != body) {
                outputStream.write(body);
            }
            outputStream.flush();
        }
                 
            // Read response
        int statusCode = connection.getResponseCode();
        
        long responseContentLength = getContentLength(connection);
        
        InputStream inputStream;
        if (statusCode < HttpURLConnection.HTTP_BAD_REQUEST) {
            inputStream = connection.getInputStream();
        } else {
             /* error from server */
            inputStream = connection.getErrorStream();
        }
        
        return new JavaHttpResponse(statusCode, responseContentLength, inputStream);
    }

    protected static byte[] getBody2(Map<String, List<String>> formParams) throws UnsupportedEncodingException {
        StringBuilder formBuf = new StringBuilder();
        boolean first = true;
        Set<Entry<String, List<String>>> formEntrySet = formParams.entrySet();
        for (Entry<String, List<String>> formEntry : formEntrySet) {
            String key = formEntry.getKey();
            List<String> values = formEntry.getValue();
            if (first) {
                first = false;
            } else {
                formBuf.append('&');
            }
            String encodedKey = URLEncoder.encode(key, HttpConstants.CHARSET_STRING);
            if (null != values && !values.isEmpty()) {
                for (String value : values) {
                    formBuf
                        .append(encodedKey)
                        .append('=')
                        .append(URLEncoder.encode(value, HttpConstants.CHARSET_STRING));
                }
            } else {
                formBuf.append(encodedKey);
            }
        }
        return formBuf.toString().getBytes(HttpConstants.ENCODING_CHARSET);
    }

    public static Builder builder() {
        return new Builder();
    }
            
}
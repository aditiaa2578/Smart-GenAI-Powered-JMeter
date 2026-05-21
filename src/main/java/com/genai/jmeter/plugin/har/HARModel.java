package com.genai.jmeter.plugin.har;

import java.util.List;
import java.util.Map;

/**
 * POJO model for the HAR (HTTP Archive) format v1.2
 */
public class HARModel {

    public Log log;

    public static class Log {
        public String version;
        public Creator creator;
        public List<Entry> entries;
    }

    public static class Creator {
        public String name;
        public String version;
    }

    public static class Entry {
        public String startedDateTime;
        public long time;
        public Request request;
        public Response response;
        public Cache cache;
        public Timings timings;
        public String serverIPAddress;
        public String connection;
        public String comment;

        public String getUrl() {
            return request != null ? request.url : "";
        }

        public String getMethod() {
            return request != null ? request.method : "GET";
        }
    }

    public static class Request {
        public String method;
        public String url;
        public String httpVersion;
        public List<NameValuePair> cookies;
        public List<NameValuePair> headers;
        public List<NameValuePair> queryString;
        public PostData postData;
        public long headersSize;
        public long bodySize;
    }

    public static class Response {
        public int status;
        public String statusText;
        public String httpVersion;
        public List<NameValuePair> cookies;
        public List<NameValuePair> headers;
        public Content content;
        public String redirectURL;
        public long headersSize;
        public long bodySize;

        public String getContentType() {
            if (headers == null) return "";
            return headers.stream()
                    .filter(h -> "content-type".equalsIgnoreCase(h.name))
                    .map(h -> h.value)
                    .findFirst().orElse("");
        }

        public String getHeaderValue(String name) {
            if (headers == null) return null;
            return headers.stream()
                    .filter(h -> name.equalsIgnoreCase(h.name))
                    .map(h -> h.value)
                    .findFirst().orElse(null);
        }
    }

    public static class Content {
        public long size;
        public String mimeType;
        public String text;
        public String encoding;
    }

    public static class PostData {
        public String mimeType;
        public String text;
        public List<Param> params;
    }

    public static class Param {
        public String name;
        public String value;
        public String fileName;
        public String contentType;
    }

    public static class NameValuePair {
        public String name;
        public String value;
        public String comment;
    }

    public static class Cache {
        public CacheEntry beforeRequest;
        public CacheEntry afterRequest;
    }

    public static class CacheEntry {
        public String expires;
        public String lastAccess;
        public String eTag;
        public int hitCount;
    }

    public static class Timings {
        public long send;
        public long wait;
        public long receive;
        public long blocked;
        public long dns;
        public long connect;
        public long ssl;
    }
}

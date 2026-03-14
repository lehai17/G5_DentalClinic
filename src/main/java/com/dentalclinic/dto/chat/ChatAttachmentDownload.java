package com.dentalclinic.dto.chat;

import org.springframework.core.io.Resource;

public class ChatAttachmentDownload {
    private final Resource resource;
    private final String fileName;
    private final String contentType;
    private final long size;

    public ChatAttachmentDownload(Resource resource, String fileName, String contentType, long size) {
        this.resource = resource;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }
}

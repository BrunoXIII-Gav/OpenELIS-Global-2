package org.openelisglobal.orderadditionalfield.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.sql.Timestamp;
import java.util.Base64;
import org.apache.commons.lang3.StringUtils;

public class OrderAdditionalFieldFilePayload {

    private String fileName;
    private String fileType;
    private Long fileSize;
    private Timestamp uploadedAt;
    private String base64Content;
    private String uploadToken;
    private Boolean deleteFile;

    @JsonIgnore
    private byte[] content;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Timestamp uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
        if (StringUtils.isBlank(base64Content)) {
            this.content = null;
            return;
        }

        if (base64Content.contains(";base64,")) {
            String[] contentInfo = base64Content.split(";base64,", 2);
            if (StringUtils.isBlank(this.fileType)) {
                this.fileType = contentInfo[0];
            }
            this.content = Base64.getDecoder().decode(contentInfo[1]);
            return;
        }

        this.content = Base64.getDecoder().decode(base64Content);
    }

    public String getUploadToken() {
        return uploadToken;
    }

    public void setUploadToken(String uploadToken) {
        this.uploadToken = uploadToken;
    }

    public Boolean getDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(Boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    @JsonIgnore
    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    @JsonIgnore
    public boolean hasContent() {
        return content != null && content.length > 0;
    }
}

package org.openelisglobal.orderadditionalfield.bean;

/**
 * A reference to the current session upload, or an explicit removal of a saved
 * file.
 */
public class OrderAdditionalFieldFileReference {
    private String uploadToken;
    private Boolean deleteFile;

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
}

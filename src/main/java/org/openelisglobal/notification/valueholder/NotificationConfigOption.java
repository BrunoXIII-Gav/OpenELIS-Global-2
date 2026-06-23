package org.openelisglobal.notification.valueholder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openelisglobal.common.valueholder.BaseObject;
import org.openelisglobal.notification.valueholder.NotificationPayloadTemplate.NotificationPayloadType;

@Entity
@Table(name = "notification_config_option")
public class NotificationConfigOption extends BaseObject<Integer> {

    private static final long serialVersionUID = -6242849348547228319L;

    public enum NotificationNature {
        RESULT_VALIDATION, RESULT_PENDING_VALIDATION, FREEZER_TEMPERATURE_ALERT, // Task T039: Alert system integration
        EQUIPMENT_ALERT, // Task T039: Alert system integration
        INVENTORY_ALERT // Task T039: Alert system integration
    }

    public enum NotificationPersonType {
        PATIENT, PROVIDER, INTERNAL_PROFILE
    }

    public enum NotificationMethod {
        SMS, EMAIL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_config_option_generator")
    @SequenceGenerator(name = "notification_config_option_generator", sequenceName = "notification_config_option_seq", allocationSize = 1)
    private Integer id;

    // persistence
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_nature")
    // validation
    @NotNull
    private NotificationNature notificationNature;

    // persistence
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_person_type")
    // validation
    @NotNull
    private NotificationPersonType notificationPersonType;

    // persistence
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_method")
    // validation
    @NotNull
    private NotificationMethod notificationMethod;

    @Valid
    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH })
    @JoinColumn(name = "payload_template_id", referencedColumnName = "id")
    @JsonIgnore
    private NotificationPayloadTemplate payloadTemplate;

    @Column(name = "active")
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "additional_contacts", joinColumns = @JoinColumn(name = "notification_config_option_id"))
    @Column(name = "contact")
    @JsonIgnore
    private List<String> additionalContacts;

    @Column(name = "professional_profile_code")
    private String professionalProfileCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_config_option_user", joinColumns = @JoinColumn(name = "notification_config_option_id"))
    @Column(name = "system_user_id")
    private Set<String> selectedUserIds;

    @Transient
    private String transientSubjectTemplate;

    @Transient
    private String transientMessageTemplate;

    public NotificationConfigOption(NotificationMethod methodType, NotificationPersonType personType,
            NotificationNature notificationNature, boolean active) {
        this.notificationMethod = methodType;
        this.notificationPersonType = personType;
        this.notificationNature = notificationNature;
        this.active = active;
    }

    public NotificationConfigOption() {
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public NotificationNature getNotificationNature() {
        return notificationNature;
    }

    public void setNotificationNature(NotificationNature notificationNature) {
        this.notificationNature = notificationNature;
    }

    public NotificationPersonType getNotificationPersonType() {
        return notificationPersonType;
    }

    public void setNotificationPersonType(NotificationPersonType notificationPersonType) {
        this.notificationPersonType = notificationPersonType;
    }

    public NotificationMethod getNotificationMethod() {
        return notificationMethod;
    }

    public void setNotificationMethod(NotificationMethod notificationMethod) {
        this.notificationMethod = notificationMethod;
    }

    public NotificationPayloadTemplate getPayloadTemplate() {
        return payloadTemplate;
    }

    public void setPayloadTemplate(NotificationPayloadTemplate payloadTemplate) {
        this.payloadTemplate = payloadTemplate;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getAdditionalContacts() {
        return additionalContacts;
    }

    public void setAdditionalContacts(List<String> additionalContacts) {
        this.additionalContacts = additionalContacts;
    }

    public String getProfessionalProfileCode() {
        return professionalProfileCode;
    }

    public void setProfessionalProfileCode(String professionalProfileCode) {
        this.professionalProfileCode = professionalProfileCode;
    }

    @JsonProperty("selectedUserIds")
    public List<String> getSelectedUserIds() {
        if (selectedUserIds == null) {
            selectedUserIds = new LinkedHashSet<>();
        }
        return new ArrayList<>(selectedUserIds);
    }

    @JsonProperty("selectedUserIds")
    public void setSelectedUserIds(List<String> selectedUserIds) {
        if (selectedUserIds == null) {
            this.selectedUserIds = new LinkedHashSet<>();
            return;
        }
        this.selectedUserIds = selectedUserIds.stream().map(String::trim).filter(s -> !s.isBlank()).distinct()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @JsonProperty("subjectTemplate")
    public String getSubjectTemplate() {
        if (transientSubjectTemplate != null) {
            return transientSubjectTemplate;
        }
        return payloadTemplate == null ? "" : payloadTemplate.getSubjectTemplate();
    }

    @JsonProperty("subjectTemplate")
    public void setSubjectTemplate(String subjectTemplate) {
        transientSubjectTemplate = subjectTemplate;
        ensurePayloadTemplate();
        payloadTemplate.setSubjectTemplate(subjectTemplate);
    }

    @JsonProperty("messageTemplate")
    public String getMessageTemplate() {
        if (transientMessageTemplate != null) {
            return transientMessageTemplate;
        }
        return payloadTemplate == null ? "" : payloadTemplate.getMessageTemplate();
    }

    @JsonProperty("messageTemplate")
    public void setMessageTemplate(String messageTemplate) {
        transientMessageTemplate = messageTemplate;
        ensurePayloadTemplate();
        payloadTemplate.setMessageTemplate(messageTemplate);
    }

    @JsonProperty("additionalContactsCsv")
    public String getAdditionalContactsCsv() {
        return additionalContacts == null || additionalContacts.isEmpty() ? "" : String.join(",", additionalContacts);
    }

    public void setAdditionalContactsCsv(String additionalContactsCsv) {
        if (additionalContactsCsv == null || additionalContactsCsv.isBlank()) {
            additionalContacts = new ArrayList<>();
            return;
        }
        additionalContacts = Arrays.stream(additionalContactsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .distinct().collect(Collectors.toList());
    }

    private void ensurePayloadTemplate() {
        if (payloadTemplate == null) {
            payloadTemplate = new NotificationPayloadTemplate();
            payloadTemplate.setType(NotificationPayloadType.TEST_RESULT);
        } else if (payloadTemplate.getType() == null) {
            payloadTemplate.setType(NotificationPayloadType.TEST_RESULT);
        }
    }
}

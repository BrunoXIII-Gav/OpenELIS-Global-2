package org.openelisglobal.common.provider.validation;

import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator.ValidationResults;
import org.openelisglobal.common.service.AccessionService;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.spring.util.SpringContext;

public class PrefixNumAccessionValidator implements IAccessionNumberGenerator {

    private static final int NUMERIC_PART_LENGTH = 13;
    private static final boolean NEED_PROGRAM_CODE = false;

    protected SampleService sampleService = SpringContext.getBean(SampleService.class);
    protected AccessionService accessionService = SpringContext.getBean(AccessionService.class);

    @Override
    public boolean needProgramCode() {
        return NEED_PROGRAM_CODE;
    }

    @Override
    public ValidationResults validFormat(String accessionNumber, boolean checkDate) throws IllegalArgumentException {
        String prefix = getPrefix();
        int expectedLength = getMaxAccessionLength();

        if (accessionNumber == null || accessionNumber.length() != expectedLength) {
            return ValidationResults.LENGTH_FAIL;
        }

        if (!accessionNumber.startsWith(prefix)) {
            return ValidationResults.SITE_FAIL;
        }

        String numericPart = accessionNumber.substring(prefix.length());
        try {
            Long.parseLong(numericPart);
        } catch (NumberFormatException e) {
            return ValidationResults.FORMAT_FAIL;
        }

        return ValidationResults.SUCCESS;
    }

    @Override
    public String getInvalidMessage(ValidationResults results) {
        return MessageUtil.getMessage("sample.entry.invalid.accession.number.suggestion") + " "
                + getNextAvailableAccessionNumber(null, false);
    }

    @Override
    public String getInvalidFormatMessage(ValidationResults results) {
        return MessageUtil.getMessage("sample.entry.invalid.accession.number.format.corrected",
                new String[] { getFormatPattern(), getFormatExample() });
    }

    private String getFormatPattern() {
        return getPrefix() + StringUtils.repeat('#', NUMERIC_PART_LENGTH);
    }

    private String getFormatExample() {
        return getPrefix() + StringUtils.leftPad("1", NUMERIC_PART_LENGTH, "0");
    }

    @Override
    public int getMaxAccessionLength() {
        return getPrefix().length() + NUMERIC_PART_LENGTH;
    }

    @Override
    public int getMinAccessionLength() {
        return getMaxAccessionLength();
    }

    @Override
    public boolean accessionNumberIsUsed(String accessionNumber, String recordType) {
        return sampleService.getSampleByAccessionNumber(accessionNumber) != null;
    }

    @Override
    public ValidationResults checkAccessionNumberValidity(String accessionNumber, String recordType, String isRequired,
            String projectFormName) {
        ValidationResults results = validFormat(accessionNumber, false);
        if (results == ValidationResults.SUCCESS && accessionNumberIsUsed(accessionNumber, null)) {
            results = ValidationResults.USED_FAIL;
        }
        return results;
    }

    @Override
    public int getInvarientLength() {
        return getPrefix().length();
    }

    @Override
    public int getChangeableLength() {
        return NUMERIC_PART_LENGTH;
    }

    @Override
    public String getPrefix() {
        String prefix = ConfigurationProperties.getInstance().getPropertyValue(Property.ACCESSION_NUMBER_PREFIX);
        return prefix == null ? "" : prefix.toUpperCase();
    }

    @Override
    public String getNextAvailableAccessionNumber(String programCode, boolean reserve) {
        String nextAccessionNumber;
        do {
            nextAccessionNumber = reserve ? incrementAccessionNumber() : incrementAccessionNumberNoReserve();
        } while (accessionNumberIsUsed(nextAccessionNumber, null));
        return nextAccessionNumber;
    }

    @Override
    public String getNextAccessionNumber(String programCode, boolean reserve) {
        return getNextAvailableAccessionNumber(programCode, reserve);
    }

    private String incrementAccessionNumber() {
        long nextNum = accessionService.getNextNumberIncrement(getPrefix(), AccessionFormat.PREFIXNUM);
        return getPrefix() + String.format("%013d", nextNum);
    }

    private String incrementAccessionNumberNoReserve() {
        long nextNum = accessionService.getNextNumberNoIncrement(getPrefix(), AccessionFormat.PREFIXNUM);
        return getPrefix() + String.format("%013d", nextNum);
    }
}

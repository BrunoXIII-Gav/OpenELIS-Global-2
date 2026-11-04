package org.openelisglobal.sample.service;

import java.time.Year;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.exception.LIMSInvalidConfigurationException;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator.ValidationResults;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.IntegerUtil;
import org.openelisglobal.common.valueholder.AccessionNumberInfo;
import org.openelisglobal.common.valueholder.AccessionNumberInfo.AccessionIdentity;
import org.openelisglobal.sample.dao.OrderAccessionDAO;
import org.openelisglobal.sample.exception.OrderAccessionConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nonallocating previews and transactional assignment for new patient orders.
 */
@Service
@Transactional
public class OrderAccessionServiceImpl implements OrderAccessionService {
    @Autowired
    private OrderAccessionDAO dao;
    @Autowired
    private AccessionNumberValidatorFactory validators;

    protected String property(Property property) {
        return StringUtils.defaultString(ConfigurationProperties.getInstance().getPropertyValue(property));
    }

    private AccessionFormat format() {
        return AccessionFormat.valueOf(property(Property.AccessionFormat).toUpperCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public String preview(String programCode) {
        return next(sequence(programCode), false);
    }

    @Override
    public String generate(String programCode) {
        // Both calls join this transaction. The preview does not allocate a number;
        // confirm rechecks the sequence under the submission lock before assigning it.
        return confirm(preview(programCode), true);
    }

    @Override
    public String confirm(String proposed, boolean generated) {
        if (StringUtils.isBlank(proposed))
            throw new IllegalArgumentException("order.labnumber.required");
        String number = proposed.trim();
        try {
            if (validators.getValidator(generated ? AccessionFormat.MAIN : AccessionFormat.GENERAL).validFormat(number,
                    true) != ValidationResults.SUCCESS)
                throw new IllegalArgumentException("order.labnumber.invalid");
        } catch (LIMSInvalidConfigurationException error) {
            throw new IllegalStateException("order.labnumber.format.unsupported", error);
        }
        dao.lockSubmission();
        if (!generated) {
            if (dao.isUsed(number))
                throw new OrderAccessionConflictException();
            return number;
        }
        Sequence sequence = sequence(format() == AccessionFormat.PROGRAMNUM ? number.substring(0, 4) : null);
        // Re-read the current sequence under lock: old generators/printed labels may
        // have reserved values since this browser obtained its proposal.
        String current = next(sequence, true);
        long value = Math.max(sequence.parse(number), sequence.parse(current));
        number = sequence.render(value);
        while (dao.isUsed(number))
            number = sequence.render(++value);
        if (sequence.identity != null) {
            AccessionNumberInfo counter = dao.counter(sequence.identity, true);
            if (counter == null) {
                counter = new AccessionNumberInfo();
                counter.setAccessionIdentity(sequence.identity);
                counter.setCurVal(value);
                dao.saveCounter(counter);
            } else
                counter.setCurVal(Math.max(counter.getCurVal() == null ? 0 : counter.getCurVal(), value));
        }
        return number;
    }

    private String next(Sequence sequence, boolean lock) {
        long last = 0;
        if (sequence.identity != null) {
            var counter = dao.counter(sequence.identity, lock);
            if (counter != null && counter.getCurVal() != null)
                last = counter.getCurVal();
        }
        String largest = dao.largest(sequence.prefix, sequence.prefix.length() + sequence.width);
        if (largest != null) {
            try {
                last = Math.max(last, sequence.parse(largest));
            } catch (IllegalArgumentException ignored) {
                /* Legacy manually entered value outside the configured sequence. */ }
        }
        String result = sequence.render(Math.addExact(last, 1));
        while (dao.isUsed(result))
            result = sequence.render(++last + 1);
        return result;
    }

    private Sequence sequence(String programCode) {
        String year = String.format(java.util.Locale.ROOT, "%02d", Year.now().getValue() % 100);
        String prefix = property(Property.ACCESSION_NUMBER_PREFIX);
        return switch (format()) {
        case PREFIXNUM -> {
            String normalized = prefix.toUpperCase(Locale.ROOT);
            yield new Sequence(normalized, 13, false, new AccessionIdentity(normalized, AccessionFormat.PREFIXNUM));
        }
        case SITEYEARNUM ->
            new Sequence(prefix + year, 13, false, new AccessionIdentity(prefix + year, AccessionFormat.SITEYEARNUM));
        case ALT_YEAR -> {
            String alt = property(Property.ALT_ACCESSION_PREFIX);
            yield new Sequence(alt + year, 13, false, new AccessionIdentity(alt + year, AccessionFormat.ALT_YEAR));
        }
        case ALPHANUM -> {
            String alpha = property(Property.ALPHANUM_ACCESSION_PREFIX);
            // Existing ALPHANUM generators use ALT_YEAR with prefix + year as their counter
            // key.
            yield new Sequence(year + alpha, 6, true, new AccessionIdentity(alpha + year, AccessionFormat.ALT_YEAR));
        }
        case YEARNUM_SIX -> new Sequence(year, 6, false, null);
        case YEARNUM_SEVEN -> new Sequence(year, 7, false, null);
        case YEARNUM_DASH_SEVEN -> new Sequence(year + "-", 7, false, null);
        case PROGRAMNUM -> {
            if (programCode == null || programCode.length() != 4)
                throw new IllegalArgumentException("order.labnumber.program.required");
            yield new Sequence(programCode.toUpperCase(Locale.ROOT), 5, false, null);
        }
        default -> throw new IllegalArgumentException("order.labnumber.format.unsupported");
        };
    }

    private record Sequence(String prefix, int width, boolean alpha, AccessionIdentity identity) {
        long parse(String number) {
            if (!number.startsWith(prefix) || number.length() != prefix.length() + width)
                throw new IllegalArgumentException("order.labnumber.invalid");
            String suffix = number.substring(prefix.length());
            return alpha ? IntegerUtil.parseIntBase27(suffix) : Long.parseLong(suffix);
        }

        String render(long value) {
            if (value < 1 || (alpha && value > Integer.MAX_VALUE))
                throw new IllegalArgumentException("order.labnumber.exhausted");
            String suffix = alpha ? IntegerUtil.toStringBase27((int) value) : Long.toString(value);
            if (suffix.length() > width)
                throw new IllegalArgumentException("order.labnumber.exhausted");
            return prefix + StringUtils.leftPad(suffix, width, '0');
        }
    }
}

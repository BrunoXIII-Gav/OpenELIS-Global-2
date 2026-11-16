package org.openelisglobal.reports.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsentSignatureServiceImpl implements ConsentSignatureService {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    @Autowired
    private ProviderService providers;
    @Autowired
    private SystemUserService users;

    @Override
    public byte[] getProviderSignature(String providerId, String personId) {
        Provider provider;
        if (StringUtils.isNotBlank(providerId)) {
            provider = providers.get(providerId.trim());
        } else if (StringUtils.isNotBlank(personId)) {
            List<Provider> matches = providers.getAllMatching("person.id", personId.trim());
            provider = matches.size() == 1 ? matches.get(0) : null;
        } else {
            return null;
        }
        if (provider == null || provider.getId() == null || provider.getPerson() == null) {
            return null;
        }
        String linkedPersonId = provider.getPerson().getId();
        if (StringUtils.isBlank(linkedPersonId)
                || (StringUtils.isNotBlank(personId) && !personId.trim().equals(linkedPersonId))) {
            return null;
        }
        // Never fall back to the logged-in user or choose an arbitrary linked account.
        var accounts = users.getAllMatching(Map.of("linkedProviderPersonId", linkedPersonId, "isActive", "Y"));
        if (accounts.size() != 1) {
            return null;
        }
        return decodeImage(accounts.get(0).getSignatureImageData());
    }

    private byte[] decodeImage(String data) {
        if (StringUtils.isBlank(data) || data.length() > (MAX_IMAGE_BYTES * 4 / 3) + 256) {
            return null;
        }
        try {
            String encoded = data.trim();
            if (encoded.startsWith("data:")) {
                int comma = encoded.indexOf(',');
                if (comma < 0 || !encoded.substring(0, comma).endsWith(";base64")) {
                    return null;
                }
                encoded = encoded.substring(comma + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return null;
            }
            return ConsentImageValidator.validatedImage(bytes);
        } catch (Exception invalidImage) {
            return null;
        }
    }
}

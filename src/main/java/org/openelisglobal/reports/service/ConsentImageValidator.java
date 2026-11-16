package org.openelisglobal.reports.service;

import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;

final class ConsentImageValidator {
    private ConsentImageValidator() {
    }

    static byte[] validatedImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return null;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                return null;
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!List.of("png", "jpeg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096 || reader.read(0) == null)
                    return null;
                return bytes;
            } finally {
                reader.dispose();
            }
        } catch (Exception invalidImage) {
            return null;
        }
    }
}

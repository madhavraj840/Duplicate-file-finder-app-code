package com.bunkwise.duplicatefilefinder.core.engine.algo;

import com.bunkwise.duplicatefilefinder.core.domain.model.DetectedType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Detects the REAL file type from magic bytes (ARCHITECTURE §5.2). Pure and
 * testable: reads only the first handful of bytes from the stream. A file whose
 * extension lies (photo.mp4 that is really a JPEG) is routed by its true type.
 */
public final class MagicByteSniffer {

    private MagicByteSniffer() {}

    /** Reads up to 16 bytes and classifies. Never throws for a short/empty file. */
    public static DetectedType detect(InputStream in) throws IOException {
        byte[] b = new byte[16];
        int read = 0;
        while (read < b.length) {
            int r = in.read(b, read, b.length - read);
            if (r == -1) break;
            read += r;
        }
        return detect(b, read);
    }

    /** Classifies from an already-read header buffer of [len] valid bytes. */
    public static DetectedType detect(byte[] b, int len) {
        if (len < 2) return DetectedType.UNKNOWN;

        // JPEG: FF D8 FF
        if (len >= 3 && u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) return DetectedType.JPEG;
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return DetectedType.PNG;
        // GIF: "GIF8"
        if (len >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return DetectedType.GIF;
        // BMP: "BM"
        if (b[0] == 'B' && b[1] == 'M') return DetectedType.BMP;
        // RIFF....WEBP
        if (len >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return DetectedType.WEBP;
        // ISO-BMFF box: "....ftyp" -> mp4/heif family
        if (len >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            char c8 = (char) b[8];
            char c9 = (char) b[9];
            // heic/heif/mif1 brands
            if ((c8 == 'h' && c9 == 'e') || (c8 == 'm' && c9 == 'i')) return DetectedType.HEIF;
            return DetectedType.MP4;
        }
        // PDF: "%PDF"
        if (len >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return DetectedType.PDF;
        // ZIP/DOCX/APK: "PK\3\4"
        if (len >= 4 && b[0] == 'P' && b[1] == 'K' && u(b[2]) == 0x03 && u(b[3]) == 0x04) return DetectedType.ZIP;
        // ID3 / MP3 frame sync
        if (len >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return DetectedType.AUDIO;
        if (len >= 2 && u(b[0]) == 0xFF && (u(b[1]) & 0xE0) == 0xE0) return DetectedType.AUDIO;
        // OGG
        if (len >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') return DetectedType.AUDIO;
        // WAV: RIFF....WAVE
        if (len >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') return DetectedType.AUDIO;

        return DetectedType.UNKNOWN;
    }

    private static int u(byte x) { return x & 0xFF; }
}

package com.hms.patient;

import java.io.InputStream;

public interface VirusScanner {
    ScanResult scan(InputStream inputStream, String fileName);

    record ScanResult(boolean clean, String details) {
        public static ScanResult ok() {
            return new ScanResult(true, "Clean");
        }
        public static ScanResult infected(String reason) {
            return new ScanResult(false, reason);
        }
    }
}


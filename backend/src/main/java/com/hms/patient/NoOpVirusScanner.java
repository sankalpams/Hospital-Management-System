package com.hms.patient;

import org.springframework.stereotype.Component;
import java.io.InputStream;

@Component
public class NoOpVirusScanner implements VirusScanner {
    @Override
    public ScanResult scan(InputStream inputStream, String fileName) {
        return ScanResult.ok();
    }
}


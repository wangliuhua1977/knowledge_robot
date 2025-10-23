package com.knowledge.robot.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CertificateLoader {

    public List<X509Certificate> load(Path path) throws IOException, CertificateException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = factory.generateCertificates(inputStream);
            List<X509Certificate> result = new ArrayList<>();
            for (Certificate certificate : certificates) {
                if (certificate instanceof X509Certificate x509Certificate) {
                    result.add(x509Certificate);
                }
            }
            return result;
        }
    }
}

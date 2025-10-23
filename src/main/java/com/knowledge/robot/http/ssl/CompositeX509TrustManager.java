package com.knowledge.robot.http.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.X509TrustManager;

public class CompositeX509TrustManager implements X509TrustManager {
    private final List<X509TrustManager> delegates;

    public CompositeX509TrustManager(List<X509TrustManager> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException lastException = null;
        for (X509TrustManager delegate : delegates) {
            try {
                delegate.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException lastException = null;
        for (X509TrustManager delegate : delegates) {
            try {
                delegate.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegates.stream()
                .flatMap(manager -> List.of(manager.getAcceptedIssuers()).stream())
                .toArray(X509Certificate[]::new);
    }
}

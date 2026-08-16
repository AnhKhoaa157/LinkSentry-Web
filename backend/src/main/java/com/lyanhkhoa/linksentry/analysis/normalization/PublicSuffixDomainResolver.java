package com.lyanhkhoa.linksentry.analysis.normalization;

import com.google.common.net.InternetDomainName;
import java.util.List;

public final class PublicSuffixDomainResolver {

    public DomainParts resolve(String asciiHost) {
        InternetDomainName domain = InternetDomainName.from(asciiHost);

        if (!domain.hasPublicSuffix() || !domain.isUnderPublicSuffix()) {
            return DomainParts.withoutRegistrableDomain();
        }

        InternetDomainName registrable = domain.topPrivateDomain();
        List<String> hostLabels = domain.parts();
        List<String> registrableParts = registrable.parts();

        int subdomainCount = hostLabels.size() - registrableParts.size();
        List<String> subdomains = hostLabels.subList(0, subdomainCount);

        return new DomainParts(registrable.toString(), subdomains);
    }
}

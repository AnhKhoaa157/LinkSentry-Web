package com.lyanhkhoa.linksentry.common.trial;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Resolves a trial installation and admits one scan against its configured quota. */
public interface TrialScanAdmission {

    /** Resolves a credential to any known device installation, licensed or not. */
    Optional<UUID> resolveDeviceId(String rawCredential);

    /** Records one scan admission if the device still has trial quota. */
    boolean tryAdmit(UUID deviceId, Instant now);
}

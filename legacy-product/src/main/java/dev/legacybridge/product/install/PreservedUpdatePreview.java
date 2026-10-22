package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.EngineError;
import java.util.Optional;

/** Fresh read-only preflight observation, not a plan, consent, installation or runtime proof. */
public record PreservedUpdatePreview(Optional<Identity> identity, Optional<EngineError> error) {
    public record Identity(String modId, String previousVersion, String replacementVersion,
                           String previousArtifactSha256, String replacementArtifactSha256) { }
    public boolean reviewed() { return identity.isPresent() && error.isEmpty(); }
}

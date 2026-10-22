package dev.legacybridge.product.controller;

import dev.legacybridge.product.install.PreservedUpdatePreview;
import java.util.Optional;

/** Atomic presentation of this controller's current update. History never reconstructs it. */
public record PreservedUpdateView(Optional<PreservedUpdateSelection> selection,
                                  Optional<PreservedUpdatePreview> preview, PreservedInstallView observation) { }

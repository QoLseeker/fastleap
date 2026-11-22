package dev.sbkit;

import dev.sbkit.features.FastLeap;
import dev.sbkit.features.RagPetSwap;

import java.util.List;

/** The only place that lists features. A new feature = one more line here. */
public final class Features {
    public static final List<Feature> ALL = List.of(
        new FastLeap(),
        new RagPetSwap()
    );

    private Features() {}
}

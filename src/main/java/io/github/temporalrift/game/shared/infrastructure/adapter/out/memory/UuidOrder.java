package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.Comparator;
import java.util.UUID;

/** Orders UUIDs the way PostgreSQL does, byte by byte as unsigned values, unlike {@link UUID#compareTo}. */
public final class UuidOrder {

    public static final Comparator<UUID> POSTGRES = Comparator.<UUID>comparingLong(
                    uuid -> uuid.getMostSignificantBits() ^ Long.MIN_VALUE)
            .thenComparingLong(uuid -> uuid.getLeastSignificantBits() ^ Long.MIN_VALUE);

    private UuidOrder() {}
}

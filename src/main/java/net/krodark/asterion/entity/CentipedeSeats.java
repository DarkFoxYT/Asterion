package net.krodark.asterion.entity;

import java.util.Arrays;
import java.util.UUID;

/** Stable segment ownership. Removing the driver never promotes a rear passenger. */
public final class CentipedeSeats {
    private final UUID[] occupants = new UUID[CentipedeChain.MAX_SEGMENTS];

    public int seatOf(UUID rider) {
        for (int i = 0; i < occupants.length; i++) if (rider.equals(occupants[i])) return i;
        return -1;
    }

    public int firstFree(int count) {
        for (int i = 0; i < Math.min(count, occupants.length); i++) if (occupants[i] == null) return i;
        return -1;
    }

    public boolean claim(UUID rider, int seat, int count) {
        if (seat < 0 || seat >= Math.min(count, occupants.length) || occupants[seat] != null
                || seatOf(rider) >= 0) return false;
        occupants[seat] = rider;
        return true;
    }

    public void release(UUID rider) {
        int seat = seatOf(rider);
        if (seat >= 0) occupants[seat] = null;
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < occupants.length; i++) if (occupants[i] != null)
            result.append(i).append('=').append(occupants[i]).append(';');
        return result.toString();
    }

    public void decode(String encoded) {
        Arrays.fill(occupants, null);
        if (encoded.length() > 1500) return;
        for (String entry : encoded.split(";")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2) continue;
            try { claim(UUID.fromString(pair[1]), Integer.parseInt(pair[0]), occupants.length); }
            catch (IllegalArgumentException ignored) { /* Ignore invalid saved reservations. */ }
        }
    }
}

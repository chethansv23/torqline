package com.torqline.repairorder.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 * OPEN --assign--> IN_PROGRESS --request parts--> PARTS_PENDING
 *                   ^    |                            |
 *                   |    +--complete--> COMPLETED     |
 *                   +------ parts reserved/failed ----+
 * OPEN, IN_PROGRESS, PARTS_PENDING --cancel--> CANCELLED
 * </pre>
 */
public enum RepairOrderStatus {
    OPEN, IN_PROGRESS, PARTS_PENDING, COMPLETED, CANCELLED;

    public Set<RepairOrderStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(PARTS_PENDING, COMPLETED, CANCELLED);
            case PARTS_PENDING -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(RepairOrderStatus.class);
        };
    }

    public boolean canMoveTo(RepairOrderStatus target) {
        return next().contains(target);
    }
}
